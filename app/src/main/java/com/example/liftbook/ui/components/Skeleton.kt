package com.example.liftbook.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay

/**
 * Hosts skeleton placeholders shaped like the content that's coming. It stays invisible for the
 * first moment of loading and then fades in, so a fast load never flashes a skeleton; once shown
 * it pulses gently. Screen readers hear [contentDescription] instead of the placeholder shapes.
 */
@Composable
fun SkeletonContainer(
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Previews and screenshots are static, so they show the skeleton at full strength.
    val isStatic = LocalInspectionMode.current
    val appearance = remember { Animatable(if (isStatic) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(APPEAR_DELAY_MILLIS)
        appearance.animateTo(1f, tween(FADE_MILLIS))
    }
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    Box(
        modifier = modifier
            // Read in the layer block so the pulse only redraws, never recomposes.
            .graphicsLayer { alpha = appearance.value * pulse.value }
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        content()
    }
}

/** One placeholder shape. Size it with the modifier to match the text or element it stands in for. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

private const val APPEAR_DELAY_MILLIS = 150L
private const val FADE_MILLIS = 200
private const val PULSE_MILLIS = 900
private const val PULSE_MIN_ALPHA = 0.6f
