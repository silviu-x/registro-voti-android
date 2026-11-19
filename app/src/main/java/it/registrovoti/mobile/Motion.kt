package it.registrovoti.mobile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal val SmoothMotion = CubicBezierEasing(0.42f, 0f, 0.32f, 1f)

internal fun Modifier.pressMotion(enabled: Boolean = true): Modifier = if (!enabled) this else composed {
    var pressed by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.94f, stiffness = if (pressed) 780f else 430f), label = "Pressione")
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

@Composable
internal fun EnterScreen(content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(270, easing = SmoothMotion)) +
        slideInVertically(tween(320, easing = SmoothMotion)) { it / 36 }) { content() }
}
