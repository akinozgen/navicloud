package com.ozgen.navicloud.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Playing-state indicator: three bars animate while playing, freeze when
 * paused. State-driven — the animation IS the information. Scale-only
 * (graphicsLayer), no layout churn; composed at most once on screen.
 */
@Composable
fun PlayingBars(
    playing: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (playing) {
            val transition = rememberInfiniteTransition(label = "eq")
            listOf(0, 160, 320).forEach { offset ->
                val scale by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(420),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(offset),
                    ),
                    label = "bar",
                )
                Bar(tint) { scale }
            }
        } else {
            listOf(0.55f, 0.35f, 0.45f).forEach { s -> Bar(tint) { s } }
        }
    }
}

@Composable
private fun Bar(tint: Color, scale: () -> Float) {
    Box(
        Modifier
            .width(3.dp)
            .height(16.dp)
            .graphicsLayer {
                scaleY = scale()
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .background(tint, RoundedCornerShape(1.5.dp)),
    )
}
