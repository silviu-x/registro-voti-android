package it.registrovoti.mobile

import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal val SmoothMotion = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

internal fun Modifier.pressMotion(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    var pressed by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (pressed) 0.985f else 1f,
        spring(dampingRatio = 1f, stiffness = if (pressed) 1200f else 760f), label = "Pressione")
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            try {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            } finally {
                pressed = false
            }
        }
    }.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

// One clock per screen, shared by its sections. Scrolling never restarts the entrance.
@Composable
internal fun rememberEntrance(): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(350, easing = LinearEasing))
    }
    return progress
}

internal fun Modifier.enterSection(clock: Animatable<Float, AnimationVector1D>, start: Float = 0f) =
    graphicsLayer {
        val fraction = ((clock.value - start) / 0.72f).coerceIn(0f, 1f)
        val progress = LinearOutSlowInEasing.transform(fraction)
        alpha = progress
        translationY = 10.dp.toPx() * (1f - progress)
    }

@Composable
internal fun EnterScreen(content: @Composable () -> Unit) {
    Box(Modifier.enterSection(rememberEntrance())) { content() }
}
