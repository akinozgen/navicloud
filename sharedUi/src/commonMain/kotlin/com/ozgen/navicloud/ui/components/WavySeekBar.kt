package com.ozgen.navicloud.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Squiggly seek bar: the played portion is a wave that flows while playing
 * and flattens when paused or scrubbing — the motion IS the playback state
 * (same language as Android 13's media notification). Draw-only (Canvas),
 * no layout animation; the phase loop runs only while playing.
 */
@Composable
fun WavySeekBar(
    value: Float,
    playing: Boolean,
    accent: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }

    val amplitude by animateFloatAsState(
        targetValue = if (playing && !dragging) 1f else 0f,
        animationSpec = tween(350),
        label = "amplitude",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.45f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "thumb",
    )
    // Phase only animates while it matters
    val phase: Float = if (playing && !dragging) {
        val transition = rememberInfiniteTransition(label = "wave")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
            label = "phase",
        )
        p
    } else 0f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        dragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        onValueChangeFinished()
                    },
                ) { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            },
    ) {
        val w = size.width
        val cy = size.height / 2f
        val px = value.coerceIn(0f, 1f) * w

        // Remaining track: quiet thin line
        drawLine(
            color = Color(0x40FFFFFF),
            start = Offset(px, cy),
            end = Offset(w, cy),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Played portion: subtle wave (or flat line when the amplitude settles)
        val amp = 2.dp.toPx() * amplitude
        if (px > 0f) {
            if (amp > 0.5f) {
                val wavelength = 52.dp.toPx()
                val step = 4.dp.toPx()
                val path = Path()
                path.moveTo(0f, cy + sin(phase) * amp)
                var x = 0f
                while (x < px) {
                    x = (x + step).coerceAtMost(px)
                    path.lineTo(x, cy + sin(phase + (x / wavelength) * (2f * PI).toFloat()) * amp)
                }
                drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawLine(accent, Offset(0f, cy), Offset(px, cy), 3.dp.toPx(), StrokeCap.Round)
            }
        }

        drawCircle(Color.White, radius = 5.dp.toPx() * thumbScale, center = Offset(px, cy))
    }
}

/**
 * WavySeekBar'ın düz kardeşi: aynı incelik (3dp, yuvarlak uç, küçük thumb),
 * dalga yok — ses seviyesi gibi ikincil denetimler için.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xCCFFFFFF),
    inactiveColor: Color = Color(0x33FFFFFF),
) {
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.35f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "thinThumb",
    )
    Canvas(
        modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val w = size.width
        val cy = size.height / 2f
        val px = value.coerceIn(0f, 1f) * w
        drawLine(inactiveColor, Offset(px, cy), Offset(w, cy), 3.dp.toPx(), StrokeCap.Round)
        if (px > 0f) drawLine(activeColor, Offset(0f, cy), Offset(px, cy), 3.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, radius = 4.dp.toPx() * thumbScale, center = Offset(px, cy))
    }
}
