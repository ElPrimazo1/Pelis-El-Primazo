package com.example.peliselprimazo.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "alpha"
    )
    
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
        label = "scale"
    )

    val blurAnim = animateFloatAsState(
        targetValue = if (startAnimation) 0f else 20f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "blur"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000) // Duración de la intro
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Efecto de resplandor de fondo
        Box(
            modifier = Modifier
                .size(300.dp)
                .alpha(alphaAnim.value * 0.3f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color.Transparent
                        )
                    )
                )
                .blur(50.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
                .blur(blurAnim.value.dp)
        ) {
            Text(
                text = "PELIS",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp
            )
            Text(
                text = "EL PRIMAZO",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Línea dorada animada
            Box(
                modifier = Modifier
                    .width(if (startAnimation) 100.dp else 0.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}
