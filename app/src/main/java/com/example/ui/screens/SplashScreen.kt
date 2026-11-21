package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LyraTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit
) {
    val colors = LyraTheme.colors
    LaunchedEffect(Unit) {
        delay(1400) // Brief 1.4 second smooth entrance without delaying app
        onNavigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.secondary.copy(alpha = 0.28f),
                                colors.primary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .testTag("splash_lyra_logo"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_lyra_logo),
                    contentDescription = "Lyra Official Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(168.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Personal Calling Assistant",
                color = colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "\"Your calls. Your privacy. Your assistant.\"",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(44.dp))

            Text(
                text = "Designed for Ajay • Local-First Architecture",
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}
