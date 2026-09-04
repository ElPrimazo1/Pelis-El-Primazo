package com.example.peliselprimazo.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.peliselprimazo.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // Animación de escala muy suave
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.05f else 1f,
        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(1800) 
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Resplandor de fondo ambiental (este sí puede tener un pequeño fade-in)
        val ambientAlpha by animateFloatAsState(
            targetValue = if (startAnimation) 0.4f else 0f,
            animationSpec = tween(800)
        )

        Box(
            modifier = Modifier
                .size(500.dp)
                .alpha(ambientAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
                .blur(80.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // El Logo es visible desde el primer frame (sin alpha animation)
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Logo Pelis El Primazo",
                modifier = Modifier
                    .size(280.dp)
                    .scale(scaleAnim.value)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // La barra de carga también es visible de inmediato
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(200.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
