package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navigation.LyraDestinations
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.LyraTheme

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    object Keypad : BottomNavItem(
        route = LyraDestinations.DIALER,
        title = "Dialpad",
        selectedIcon = Icons.Default.Dialpad,
        unselectedIcon = Icons.Outlined.Dialpad,
        testTag = "nav_keypad"
    )
    object Recents : BottomNavItem(
        route = LyraDestinations.CALL_HISTORY,
        title = "Recent",
        selectedIcon = Icons.Default.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_recents"
    )
    object Contacts : BottomNavItem(
        route = LyraDestinations.CONTACTS,
        title = "Contacts",
        selectedIcon = Icons.Default.People,
        unselectedIcon = Icons.Outlined.People,
        testTag = "nav_contacts"
    )
    object LyraAI : BottomNavItem(
        route = LyraDestinations.HOME,
        title = "Lyra AI",
        selectedIcon = Icons.Default.Security,
        unselectedIcon = Icons.Outlined.Security,
        testTag = "nav_lyra_ai"
    )
}

/**
 * Double-Capsule Bottom Navigation Bar:
 * ONE outer floating pill capsule enclosing 4 inner interactive pill capsules.
 */
@Composable
fun LyraBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    unreadMissedCount: Int = 0
) {
    val colors = LyraTheme.colors
    val items = listOf(
        BottomNavItem.Keypad,
        BottomNavItem.Recents,
        BottomNavItem.Contacts,
        BottomNavItem.LyraAI
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (colors.isGlassStyle) {
                    Modifier.background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.35f to colors.background.copy(alpha = 0.72f),
                            1.0f to colors.background.copy(alpha = 0.94f)
                        )
                    )
                } else {
                    Modifier.background(colors.background)
                }
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        OuterCapsuleContainer(
            containerColor = if (colors.isGlassStyle) colors.glassSurface else colors.surface,
            borderColor = colors.glassBorder,
            tonalElevation = if (colors.isLight) 4.dp else 0.dp,
            contentPadding = PaddingValues(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route

                    val innerBg by animateColorAsState(
                        targetValue = when {
                            isSelected && colors.isGlassStyle -> Color.White.copy(alpha = 0.16f)
                            isSelected -> colors.primary.copy(alpha = 0.20f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(110, easing = FastOutSlowInEasing),
                        label = "nav_capsule_bg"
                    )
                    val innerBorder by animateColorAsState(
                        targetValue = when {
                            isSelected && colors.isGlassStyle -> Color.White.copy(alpha = 0.42f)
                            isSelected -> colors.primary.copy(alpha = 0.50f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(110, easing = FastOutSlowInEasing),
                        label = "nav_capsule_border"
                    )
                    val contentTint by animateColorAsState(
                        targetValue = if (isSelected) {
                            if (colors.isGlassStyle) colors.primaryGlow else colors.primary
                        } else {
                            colors.textMuted
                        },
                        animationSpec = tween(110, easing = FastOutSlowInEasing),
                        label = "nav_capsule_tint"
                    )

                    Surface(
                        onClick = {
                            if (!isSelected) {
                                onNavigate(item.route)
                            }
                        },
                        shape = CircleShape,
                        color = innerBg,
                        border = if (isSelected) BorderStroke(1.dp, innerBorder) else null,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag(item.testTag)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(width = 42.dp, height = 22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    tint = contentTint,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (item == BottomNavItem.Recents && unreadMissedCount > 0) {
                                    Surface(
                                        color = CrimsonDecline,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .defaultMinSize(minWidth = 16.dp)
                                    ) {
                                        Text(
                                            text = if (unreadMissedCount > 99) "99+" else unreadMissedCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                .testTag("nav_recents_unread_badge")
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                color = if (isSelected) colors.primaryGlow else colors.textMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
