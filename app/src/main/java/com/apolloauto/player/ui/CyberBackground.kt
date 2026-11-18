package com.apolloauto.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.apolloauto.player.theme.LocalHudPalette

@Composable
fun CyberBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalHudPalette.current
    // Keep navigation and settings static; save animation work for Car Mode.
    // The full visualizer catalogue remains available in the immersive player.
    val glow = 0.35f
    val scan = 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(p.voidBlack, p.panel, p.voidBlack)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 48f
            var x = 0f
            while (x < size.width) {
                drawLine(p.grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(p.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            val scanY = size.height * scan
            drawLine(
                p.primary.copy(alpha = 0.35f),
                Offset(0f, scanY),
                Offset(size.width, scanY),
                strokeWidth = 3f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glowA.copy(alpha = glow * 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.2f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.15f, size.height * 0.2f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(p.glowB.copy(alpha = glow * 0.3f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.75f),
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.85f, size.height * 0.75f)
            )
        }
        content()
    }
}
