package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ContactItemData
import com.example.ui.theme.LyraTheme

@Composable
fun EditContactDialog(
    contact: ContactItemData,
    onDismiss: () -> Unit,
    onSave: (newName: String, newPhone: String, newPhotoUri: String?, isPriority: Boolean) -> Unit
) {
    val colors = LyraTheme.colors
    var nameInput by remember(contact.id, contact.name) {
        mutableStateOf(contact.name.removeSuffix(" (Owner)"))
    }
    var phoneInput by remember(contact.id, contact.phoneNumber) {
        mutableStateOf(contact.phoneNumber)
    }
    var photoUriInput by remember(contact.id, contact.photoUri) {
        mutableStateOf(contact.photoUri)
    }
    var isPriorityChecked by remember(contact.id, contact.isPriority) {
        mutableStateOf(contact.isPriority)
    }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showRemovePhotoConfirm by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUriInput = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Contact",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Profile Photo Picker (Android Photo Picker - zero storage permissions)
                Box(
                    modifier = Modifier
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("edit_contact_photo_picker"),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    LyraContactAvatar(
                        name = nameInput.ifBlank { contact.name },
                        photoUri = photoUriInput,
                        isOwner = contact.isOwner,
                        isPriority = isPriorityChecked,
                        size = 84.dp
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .border(2.dp, colors.surfaceElevated, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change profile photo",
                            tint = colors.onPrimaryButton,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("change_contact_photo_btn")
                    ) {
                        Text(
                            text = if (photoUriInput.isNullOrBlank()) "Add Photo" else "Change Photo",
                            color = colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (!photoUriInput.isNullOrBlank() && !isPriorityChecked && !contact.isOwner) {
                        TextButton(
                            onClick = { showRemovePhotoConfirm = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("remove_contact_photo_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove photo",
                                tint = colors.declineColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Remove",
                                color = colors.declineColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        validationError = null
                    },
                    label = { Text("Contact Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_contact_name_input")
                )

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = {
                        phoneInput = it
                        validationError = null
                    },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_contact_phone_input")
                )

                if (!contact.isOwner) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPriorityChecked = !isPriorityChecked }
                    ) {
                        Checkbox(
                            checked = isPriorityChecked,
                            onCheckedChange = { isPriorityChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.priorityColor,
                                uncheckedColor = colors.textMuted
                            )
                        )
                        Text(
                            text = "Priority Caller ★",
                            color = colors.textPrimary,
                            fontSize = 13.sp
                        )
                    }
                }

                validationError?.let { err ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = err,
                        color = colors.declineColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = nameInput.trim()
                    val trimmedPhone = phoneInput.trim()
                    when {
                        trimmedName.isBlank() -> validationError = "Please enter a contact name."
                        trimmedPhone.isBlank() -> validationError = "Please enter a phone number."
                        else -> onSave(trimmedName, trimmedPhone, photoUriInput, isPriorityChecked)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_contact_edit_btn")
            ) {
                Text("Save", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_contact_edit_btn")
            ) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated,
        shape = RoundedCornerShape(18.dp)
    )

    if (showRemovePhotoConfirm) {
        LyraDeleteConfirmationDialog(
            title = "Remove Contact Photo?",
            message = "Are you sure you want to remove the profile photo for ${nameInput.ifBlank { contact.name }}?",
            confirmText = "Remove",
            onConfirm = {
                photoUriInput = null
                showRemovePhotoConfirm = false
            },
            onDismiss = { showRemovePhotoConfirm = false }
        )
    }
}
