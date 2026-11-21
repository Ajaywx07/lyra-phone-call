package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LyraTheme
import com.example.utils.SimManager
import com.example.utils.SimSlotInfo

fun shouldPromptForSim(
    preferredSim: String,
    fixDefaultSim: Boolean,
    availableSims: List<SimSlotInfo>
): Boolean = !fixDefaultSim && preferredSim == SimManager.SIM_ASK && availableSims.size >= 2

@Composable
fun SimChooserDialog(
    phoneNumber: String,
    availableSims: List<SimSlotInfo>,
    onSelectSim: (simKey: String, fixForFuture: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LyraTheme.colors
    var rememberAsFixed by remember(phoneNumber) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Calling SIM",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Call $phoneNumber using:",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )

                availableSims.forEach { sim ->
                    Surface(
                        color = colors.background,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
                        onClick = { onSelectSim(sim.simKey, rememberAsFixed) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("choose_${sim.simKey}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = sim.label,
                                tint = colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = sim.label,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = sim.carrierName + (sim.number?.let { " • $it" } ?: ""),
                                    color = colors.primaryGlow,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { rememberAsFixed = !rememberAsFixed }
                ) {
                    Checkbox(
                        checked = rememberAsFixed,
                        onCheckedChange = { rememberAsFixed = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.priorityColor,
                            uncheckedColor = colors.textMuted
                        )
                    )
                    Text(
                        text = "Fix this SIM for future calls",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("sim_chooser_cancel_btn")
            ) {
                Text("Cancel", color = colors.textMuted)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(18.dp)
    )
}
