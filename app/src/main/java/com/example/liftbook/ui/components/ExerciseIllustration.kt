package com.example.liftbook.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.liftbook.ui.theme.IconSize
import com.example.liftbook.ui.theme.LiftBookPreview
import com.example.liftbook.ui.theme.Spacing
import com.example.liftbook.ui.theme.ThemePreviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The drawing of an exercise's movement (its `Exercise.illustration`) on a paper-toned tile, or
 * a quiet dumbbell glyph when it has none: custom exercises, and the few built-ins the drawing
 * set lacks. Decorative — the exercise's name is always beside it — so it says nothing to
 * TalkBack.
 *
 * The drawings are grey figures on white and look the same in both themes: they sit on the same
 * light paper tone whether the theme is light or dark, rather than being recoloured. The white
 * is multiplied away into the tile, so there is never a white card inside the tile's corners.
 * The square drawing fits inside whatever shape the tile is given; a wide tile simply has more
 * paper either side.
 */
@Composable
fun ExerciseIllustration(
    illustration: String?,
    modifier: Modifier = Modifier,
    detail: IllustrationDetail = IllustrationDetail.Thumbnail,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val image = rememberIllustration(illustration, detail)
    val colors = MaterialTheme.colorScheme
    // Judged from the scheme rather than the system, since the theme setting can override it.
    // In dark theme the inverse surface is the light theme's own high container: the same paper.
    val dark = colors.background.luminance() < 0.5f
    val paper = if (dark) colors.inverseSurface else colors.surfaceContainerHigh
    val ink = if (dark) colors.inverseOnSurface else colors.onSurfaceVariant
    // A drawing that's already cached is there from the first frame; one that isn't fades in.
    val alpha by animateFloatAsState(
        targetValue = if (image is Illustration.Ready) 1f else 0f,
        animationSpec = tween(FADE_MILLIS),
        label = "illustrationAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(paper)
            .drawBehind {
                val bitmap = (image as? Illustration.Ready)?.bitmap ?: return@drawBehind
                val side = min(size.width, size.height)
                if (side <= 0f) return@drawBehind
                val dstSize = IntSize(side.roundToInt(), side.roundToInt())
                val dstOffset = IntOffset(
                    ((size.width - side) / 2f).roundToInt(),
                    ((size.height - side) / 2f).roundToInt(),
                )
                drawImage(
                    image = bitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = alpha,
                    blendMode = BlendMode.Multiply,
                    filterQuality = FilterQuality.Medium,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (image is Illustration.None) {
            Icon(
                Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(if (detail == IllustrationDetail.Full) IconSize.action else IconSize.inline),
            )
        }
    }
}

/** How large the drawing will be shown, which sets how finely it is decoded. */
enum class IllustrationDetail(internal val sampleSize: Int) {
    /** A list row's tile: a quarter of the file's pixels is plenty. */
    Thumbnail(sampleSize = 2),

    /** A page's panel: every pixel the file has. */
    Full(sampleSize = 1),
}

object ExerciseIllustrationDefaults {
    /** The tile beside a list row's title. */
    val ThumbnailSize: Dp = 48.dp

    /** The tile in a workout's exercise header, where the name needs the room. */
    val InlineSize: Dp = 40.dp

    /** The tile beside a page's headline. */
    val HeaderSize: Dp = 64.dp

    /** Width to height of the panel at the top of an exercise's page. */
    const val PanelAspectRatio: Float = 2f
}

private sealed interface Illustration {
    data object Loading : Illustration

    data object None : Illustration

    data class Ready(val bitmap: ImageBitmap) : Illustration
}

@Composable
private fun rememberIllustration(name: String?, detail: IllustrationDetail): Illustration {
    if (name == null) return Illustration.None
    val assets = LocalContext.current.assets
    val key = "$name@${detail.sampleSize}"
    // A preview renders one frame, so it decodes in place rather than wait for a coroutine.
    if (LocalInspectionMode.current) {
        return remember(key) { decode(assets, name, detail.sampleSize)?.let(Illustration::Ready) ?: Illustration.None }
    }
    // Keyed, so a row reused for another exercise never shows the previous one's drawing.
    var state by remember(key) {
        mutableStateOf(IllustrationCache.get(key)?.let(Illustration::Ready) ?: Illustration.Loading)
    }
    LaunchedEffect(key) {
        if (state !is Illustration.Loading) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) { decode(assets, name, detail.sampleSize) }
        state = if (bitmap != null) {
            IllustrationCache.put(key, bitmap)
            Illustration.Ready(bitmap)
        } else {
            Illustration.None
        }
    }
    return state
}

private fun decode(assets: AssetManager, name: String, sampleSize: Int): ImageBitmap? = runCatching {
    assets.open("$ASSET_DIR/$name.webp").use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }
}.getOrNull()?.asImageBitmap()

/** Decoded drawings by name and detail, up to about 16 MB: a screenful of rows plus a few pages. */
private object IllustrationCache : LruCache<String, ImageBitmap>(CACHE_BYTES) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
}

private const val ASSET_DIR = "exercises"
private const val FADE_MILLIS = 150
private const val BYTES_PER_PIXEL = 4
private const val CACHE_BYTES = 16 * 1024 * 1024

@ThemePreviews
@Composable
private fun ExerciseIllustrationPreview() {
    LiftBookPreview {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ExerciseIllustration("barbell_bench_press", Modifier.size(ExerciseIllustrationDefaults.ThumbnailSize))
                ExerciseIllustration("dumbbell_lateral_raise", Modifier.size(ExerciseIllustrationDefaults.ThumbnailSize))
                ExerciseIllustration(null, Modifier.size(ExerciseIllustrationDefaults.ThumbnailSize))
            }
            ExerciseIllustration(
                illustration = "cable_bar_lateral_pulldown",
                detail = IllustrationDetail.Full,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().aspectRatio(ExerciseIllustrationDefaults.PanelAspectRatio),
            )
        }
    }
}
