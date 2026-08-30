package com.example.peliselprimazo.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ParticleLoading(modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    val color1 by infiniteTransition.animateColor(
        initialValue = Color(0xFFE50914),
        targetValue = Color(0xFF00D2FF),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color1"
    )

    val color2 by infiniteTransition.animateColor(
        initialValue = Color(0xFF9D50BB),
        targetValue = Color(0xFF6E48AA),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color2"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Usamos drawScopeSize para evitar conflictos con el parámetro 'size' del composable
            val drawScopeSize = this.size
            val center = Offset(drawScopeSize.width / 2f, drawScopeSize.height / 2f)
            val a = drawScopeSize.width / 3f 
            
            val particleCount = 20
            for (i in 0 until particleCount) {
                val t = (progress + (i.toFloat() / particleCount)) % 1f
                val angle = t.toDouble() * 2.0 * PI
                
                val sinAngle = sin(angle)
                val cosAngle = cos(angle)
                
                // Ecuación de la Lemniscata de Bernoulli
                val denom = 1.0 + sinAngle * sinAngle
                val x = (a.toDouble() * cosAngle / denom).toFloat()
                val y = (a.toDouble() * sinAngle * cosAngle / denom).toFloat()
                
                val particleCenter = center + Offset(x, y)
                val color = if (i % 2 == 0) color1 else color2
                
                // Partícula
                drawCircle(
                    color = color.copy(alpha = 0.8f),
                    radius = (drawScopeSize.width / 60f),
                    center = particleCenter
                )
                
                // Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                        center = particleCenter,
                        radius = (drawScopeSize.width / 20f)
                    ),
                    radius = (drawScopeSize.width / 20f),
                    center = particleCenter
                )
            }
        }
    }
}
