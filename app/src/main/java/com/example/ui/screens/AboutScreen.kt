package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.components.GlassCard
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme

@Composable
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val colors = LyraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LyraAmbientBackdrop()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag("about_screen"),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            LyraTopBar(
                title = "ABOUT LYRA",
                subtitle = "Intelligence & Specifications",
                showBack = true,
                onBackClick = onBackClick
            )
        }

        // Official Brand Logo Header
        item {
            Box(
                modifier = Modifier
                    .size(148.dp)
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
                    .testTag("about_lyra_logo"),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_lyra_logo),
                    contentDescription = "Lyra Official Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(120.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "LYRA CALLING ASSISTANT",
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "\"Your calls. Your privacy. Your assistant.\"",
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Developer & Ownership Card
        item {
            SectionHeader(title = "Ownership & Architecture")

            GlassCard(borderColor = colors.glassBorderPurple) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AboutRow(label = "Application", value = "Lyra Calling Assistant")
                    AboutRow(label = "Version", value = "2.0 (Production Release)")
                    AboutRow(label = "Developer / Owner", value = "Ajay")
                    AboutRow(label = "Package Name", value = "com.ajay.lyra")
                    AboutRow(label = "Architecture", value = "MVVM + Clean Repository")
                    AboutRow(label = "Local Storage", value = "Room Database + DataStore")
                    AboutRow(label = "Telecom Engine", value = "Android InCallService + Telephony")
                }
            }
        }

        // Privacy Pledge Card
        item {
            SectionHeader(title = "Strict Privacy Pledge")

            GlassCard(borderColor = EmeraldAccept.copy(alpha = 0.35f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldAccept,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zero Cloud Leakage Guarantee",
                            color = EmeraldAccept,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Lyra operates strictly offline and locally by default.\n" +
                               "2. Never uploads private contacts to external servers.\n" +
                               "3. Never records or streams call audio without explicit permission.\n" +
                               "4. All notes, summaries, and priority tags stay on this device.\n" +
                               "5. Complies with Google Play policies and least-privilege standards.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Open Source Technologies
        item {
            SectionHeader(title = "Frameworks & Tooling")

            GlassCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Native Android Stack",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "• Kotlin 2.0 & Jetpack Compose (Material 3)\n" +
                               "• Android Telecom InCallService & BroadcastReceiver\n" +
                               "• Room Database & Kotlin Coroutines Flow\n" +
                               "• AndroidX Preferences DataStore\n" +
                               "• Coil Image Loading Library\n" +
                               "• Robolectric JVM Testing Suite",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val colors = LyraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = colors.textSecondary, fontSize = 12.sp)
        Text(text = value, color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
