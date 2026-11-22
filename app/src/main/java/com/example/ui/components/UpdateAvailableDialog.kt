package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.EmeraldAccept
import com.example.ui.theme.LyraTheme
import com.example.utils.GitHubReleaseInfo

@Composable
fun UpdateAvailableDialog(
    release: GitHubReleaseInfo,
    onDownloadClick: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_available_dialog"),
        containerColor = colors.surfaceElevated,
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = "Update Available",
                tint = colors.primary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Update Available",
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = EmeraldAccept.copy(alpha = 0.16f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, EmeraldAccept.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} → ${release.tagName}",
                        color = EmeraldAccept,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = release.releaseTitle,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.glassBorder)
                ) {
                    Text(
                        text = release.releaseNotes.ifBlank {
                            "A newer GitHub Release (${release.tagName}) is ready. Open GitHub Releases to download and update."
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadClick,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = CircleShape,
                modifier = Modifier.testTag("update_download_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = colors.onPrimaryButton,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (!release.apkDownloadUrl.isNullOrBlank()) "Get APK Release" else "Open Release",
                    color = colors.onPrimaryButton,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onSkipVersion,
                    modifier = Modifier.testTag("update_skip_btn")
                ) {
                    Text("Skip", color = colors.textMuted, fontSize = 12.sp)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("update_later_btn")
                ) {
                    Text("Later", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
        }
    )
}
