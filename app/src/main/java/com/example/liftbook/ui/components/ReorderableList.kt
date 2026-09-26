package com.example.liftbook.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/*
 * Drag-to-reorder for a LazyColumn, started from a handle rather than a long press, so a list
 * full of text fields still scrolls and edits normally. The dragged item follows the finger; the
 * others make room as its middle crosses theirs; dragging to an edge scrolls. Indices are the
 * LazyColumn's own, so the caller maps them past any header items. A handle can't be used from
 * TalkBack, so screens offer a Move up / Move down alternative too.
 */

@Stable
class ReorderableListState internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val canMoveTo: (index: Int) -> Boolean,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    /** The index the dragged item currently occupies, or null when nothing is being dragged. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** The item that was just dropped, while it settles back into its slot. */
    internal var settlingIndex by mutableStateOf<Int?>(null)
        private set
    internal val settlingOffset = Animatable(0f)

    internal val scrollRequests = Channel<Float>(Channel.CONFLATED)

    private var dragDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingIndex }

    /** How far the dragged item is drawn from the slot the list lays it out in. */
    internal val draggingOffset: Float
        get() = draggingItem?.let { initialOffset + dragDelta - it.offset } ?: 0f

    internal fun onDragStart(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingIndex = item.index
        initialOffset = item.offset
        dragDelta = 0f
    }

    internal fun onDrag(deltaY: Float) {
        dragDelta += deltaY
        val dragged = draggingItem ?: return
        // Where the item is drawn, independent of where the list currently lays it out.
        val top = initialOffset + dragDelta
        val bottom = top + dragged.size
        val middle = (top + bottom) / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != dragged.index && canMoveTo(item.index) &&
                middle.toInt() in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            // Keep the scroll position pinned by index: the list would otherwise follow the
            // first visible item's key, and scroll along with the move.
            if (dragged.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
                listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            }
            onMove(dragged.index, target.index)
            draggingIndex = target.index
        } else {
            val overscroll = when {
                dragDelta > 0 -> (bottom - listState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                dragDelta < 0 -> (top - listState.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) scrollRequests.trySend(overscroll)
        }
    }

    internal fun onDragEnd() {
        val index = draggingIndex ?: return
        val offset = draggingOffset
        draggingIndex = null
        dragDelta = 0f
        initialOffset = 0
        settlingIndex = index
        scope.launch {
            settlingOffset.snapTo(offset)
            settlingOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f))
            settlingIndex = null
        }
    }
}

/**
 * [canMoveTo] says which LazyColumn indices are reorderable items (not headers or footers);
 * [onMove] moves the item at one index to another.
 */
@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    canMoveTo: (index: Int) -> Boolean,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableListState {
    val scope = rememberCoroutineScope()
    val currentCanMoveTo by rememberUpdatedState(canMoveTo)
    val currentOnMove by rememberUpdatedState(onMove)
    val state = remember(listState) {
        ReorderableListState(
            listState = listState,
            scope = scope,
            canMoveTo = { currentCanMoveTo(it) },
            onMove = { from, to -> currentOnMove(from, to) },
        )
    }
    LaunchedEffect(state) {
        for (delta in state.scrollRequests) listState.scrollBy(delta)
    }
    return state
}

/**
 * Wraps one reorderable item. While dragged it's drawn above its neighbours at the finger;
 * everything else animates into its new place. [content] is told whether it's being dragged, to
 * show it lifted.
 */
@Composable
fun LazyItemScope.ReorderableItem(
    state: ReorderableListState,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    val dragging = index == state.draggingIndex
    val placement = when {
        dragging -> Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = state.draggingOffset }
        index == state.settlingIndex -> Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = state.settlingOffset.value }
        else -> Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }
    Box(modifier.then(placement)) { content(dragging) }
}

/** Makes this element the drag handle for the item with [key]. */
fun Modifier.dragHandle(state: ReorderableListState, key: Any): Modifier = pointerInput(state, key) {
    detectDragGestures(
        onDragStart = { state.onDragStart(key) },
        onDrag = { change, amount ->
            change.consume()
            state.onDrag(amount.y)
        },
        onDragEnd = { state.onDragEnd() },
        onDragCancel = { state.onDragEnd() },
    )
}
