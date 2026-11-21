package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ContactItemData
import com.example.navigation.LyraDestinations
import com.example.ui.components.BadgeType
import com.example.ui.components.CapsuleButton
import com.example.ui.components.CapsuleFilterBar
import com.example.ui.components.CapsuleSearchBar
import com.example.ui.components.ContactCard
import com.example.ui.components.EditContactDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.GlassCard
import com.example.ui.components.ListItemContextMenuDialog
import com.example.ui.components.LyraAmbientBackdrop
import com.example.ui.components.LyraBottomNavBar
import com.example.ui.components.LyraDeleteConfirmationDialog
import com.example.ui.components.LyraTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SimChooserDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.shouldPromptForSim
import com.example.ui.theme.AmberPriority
import com.example.ui.theme.CrimsonDecline
import com.example.ui.theme.LyraTheme
import com.example.ui.viewmodel.ContactFilter
import com.example.ui.viewmodel.ContactsUiState
import com.example.ui.viewmodel.ContactsViewModel
import com.example.ui.viewmodel.LyraMainViewModel

@Composable
fun ContactsScreen(
    mainViewModel: LyraMainViewModel,
    contactsViewModel: ContactsViewModel = viewModel(),
    onContactClick: (ContactItemData) -> Unit,
    onBackClick: () -> Unit,
    onNavigateTab: ((String) -> Unit)? = null
) {
    val colors = LyraTheme.colors
    val uiState by contactsViewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by contactsViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by contactsViewModel.selectedFilter.collectAsStateWithLifecycle()
    val hasContactsPermission by contactsViewModel.hasContactsPermission.collectAsStateWithLifecycle()
    val lookupNumber by contactsViewModel.lookupPhoneNumber.collectAsStateWithLifecycle()
    val identifiedCaller by contactsViewModel.identifiedCaller.collectAsStateWithLifecycle()

    val availableSims by mainViewModel.availableSims.collectAsStateWithLifecycle()
    val preferredSim by mainViewModel.preferredSim.collectAsStateWithLifecycle()
    val fixDefaultSim by mainViewModel.fixDefaultSim.collectAsStateWithLifecycle()
    val unreadMissedCount by mainViewModel.unreadMissedCallCount.collectAsStateWithLifecycle()

    var showCallerIdTester by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<ContactItemData?>(null) }
    var contextMenuContact by remember { mutableStateOf<ContactItemData?>(null) }
    var pendingDeleteContact by remember { mutableStateOf<ContactItemData?>(null) }
    var pendingSimCallNumber by remember { mutableStateOf<String?>(null) }

    val startCallWithSimCheck: (String) -> Unit = { number ->
        if (number.isNotBlank()) {
            if (shouldPromptForSim(preferredSim, fixDefaultSim, availableSims)) {
                pendingSimCallNumber = number
            } else {
                mainViewModel.placeCall(phoneNumber = number, simKeyOverride = preferredSim)
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        contactsViewModel.loadSystemContacts()
        mainViewModel.loadData()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LyraAmbientBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("contacts_screen")
        ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                LyraTopBar(
                    title = "CONTACTS",
                    subtitle = "Tap for profile • Long-press to edit",
                    showBack = onNavigateTab == null,
                    onBackClick = onBackClick,
                    statusText = "",
                    trailingActions = {
                        Surface(
                            onClick = { showCallerIdTester = !showCallerIdTester },
                            shape = CircleShape,
                            color = if (showCallerIdTester) colors.secondary.copy(alpha = 0.22f) else colors.surfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (showCallerIdTester) colors.secondary else colors.glassBorder
                            ),
                            modifier = Modifier.testTag("toggle_caller_id_tester")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Caller ID Check",
                                    tint = colors.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "ID",
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            onClick = { showAddDialog = true },
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("add_priority_contact_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Add priority contact",
                                    tint = colors.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                contactsViewModel.loadSystemContacts()
                                mainViewModel.loadData()
                            },
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("refresh_contacts_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh contacts",
                                    tint = colors.primaryGlow,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                )
            }

            // System Contacts Permission Banner when READ_CONTACTS is not granted
            if (!hasContactsPermission) {
                item {
                    GlassCard(borderColor = AmberPriority.copy(alpha = 0.5f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Contacts,
                                    contentDescription = null,
                                    tint = AmberPriority,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Device Contacts Access Needed",
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant Contacts permission so Lyra can load real device contacts for caller identification and priority alerts. Showing local sample contacts until granted.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            CapsuleButton(
                                text = "Grant Contacts Permission",
                                onClick = {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                modifier = Modifier.testTag("grant_contacts_permission_btn")
                            )
                        }
                    }
                }
            }

            // Capsule Search Bar
            item {
                CapsuleSearchBar(
                    query = searchQuery,
                    onQueryChange = { contactsViewModel.onSearchQueryChange(it) },
                    placeholder = "Search name, number, or label...",
                    testTag = "contact_search_input"
                )
            }

            // Double-Capsule Contact Filter Bar
            item {
                CapsuleFilterBar(
                    items = ContactFilter.entries,
                    selectedItem = selectedFilter,
                    labelProvider = { it.label },
                    onSelect = { filter -> contactsViewModel.onFilterChange(filter) },
                    testTagPrefix = "filter_chip"
                )
            }

            // Caller Identification Quick Tester
            item {
                AnimatedVisibility(visible = showCallerIdTester) {
                    GlassCard(borderColor = colors.secondary.copy(alpha = 0.5f)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Caller Identification Test",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter any phone number (+91, 0-prefix, or 10 digits) to verify how Lyra classifies incoming callers.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = lookupNumber,
                                    onValueChange = { contactsViewModel.onLookupPhoneChange(it) },
                                    placeholder = { Text("+91 98765 43210", color = colors.textMuted, fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.secondary,
                                        unfocusedBorderColor = colors.glassBorder,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    ),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("caller_lookup_input")
                                )
                                CapsuleButton(
                                    text = "Identify",
                                    containerColor = colors.secondary,
                                    contentColor = colors.onSecondaryButton,
                                    onClick = { contactsViewModel.identifyCaller() },
                                    modifier = Modifier.testTag("run_caller_lookup_btn")
                                )
                            }

                            identifiedCaller?.let { result ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = colors.surfaceElevated,
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, colors.glassBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.displayName,
                                                color = colors.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${result.formattedNumber} • ${result.contactLabel}",
                                                color = colors.textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        when {
                                            result.isOwner -> StatusBadge(type = BadgeType.OWNER)
                                            result.isPriority -> StatusBadge(type = BadgeType.PRIORITY)
                                            result.isKnown -> StatusBadge(type = BadgeType.ALLOWED, customText = "Known")
                                            else -> StatusBadge(type = BadgeType.UNKNOWN)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Contact List States
            when (val state = uiState) {
                is ContactsUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = colors.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Reading system contacts...",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                is ContactsUiState.Error -> {
                    item {
                        GlassCard(borderColor = CrimsonDecline.copy(alpha = 0.5f)) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.message,
                                    color = CrimsonDecline,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                CapsuleButton(
                                    text = "Retry",
                                    onClick = { contactsViewModel.loadSystemContacts() }
                                )
                            }
                        }
                    }
                }

                is ContactsUiState.Success -> {
                    if (state.priorityContacts.isNotEmpty()) {
                        item {
                            SectionHeader(title = "★ Priority & Owner (${state.priorityContacts.size})")
                        }

                        items(state.priorityContacts, key = { "priority_${it.id}_${it.phoneNumber}" }) { contact ->
                            ContactCard(
                                contact = contact,
                                onContactClick = {
                                    mainViewModel.selectContact(contact)
                                    onContactClick(contact)
                                },
                                onCallClick = { startCallWithSimCheck(contact.phoneNumber) },
                                onTogglePriority = {
                                    contactsViewModel.togglePriority(contact)
                                    mainViewModel.loadData()
                                },
                                onLongClick = { contextMenuContact = contact }
                            )
                        }
                    }

                    if (state.regularContacts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            SectionHeader(title = "System & Directory (${state.regularContacts.size})")
                        }

                        items(state.regularContacts, key = { "regular_${it.id}_${it.phoneNumber}" }) { contact ->
                            ContactCard(
                                contact = contact,
                                onContactClick = {
                                    mainViewModel.selectContact(contact)
                                    onContactClick(contact)
                                },
                                onCallClick = { startCallWithSimCheck(contact.phoneNumber) },
                                onTogglePriority = {
                                    contactsViewModel.togglePriority(contact)
                                    mainViewModel.loadData()
                                },
                                onLongClick = { contextMenuContact = contact }
                            )
                        }
                    }

                    if (state.priorityContacts.isEmpty() && state.regularContacts.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.People,
                                title = "No Contacts Found",
                                description = if (searchQuery.isNotBlank()) {
                                    "No contacts matching \"$searchQuery\"."
                                } else {
                                    "No contacts loaded for this filter."
                                }
                            )
                        }
                    }
                }
            }
        }

        if (onNavigateTab != null) {
            LyraBottomNavBar(
                currentRoute = LyraDestinations.CONTACTS,
                onNavigate = onNavigateTab,
                unreadMissedCount = unreadMissedCount
            )
        }
    }
    }

    // Long-press Context Menu Dialog (Edit / Delete; delete hidden when Priority switch is ON)
    contextMenuContact?.let { target ->
        ListItemContextMenuDialog(
            title = target.name,
            subtitle = target.phoneNumber,
            canDelete = !target.isOwner && !target.isPriority,
            deleteLabel = "Delete Contact",
            onEditClick = { editingContact = target },
            onDeleteClick = {
                pendingDeleteContact = target
            },
            onDismiss = { contextMenuContact = null }
        )
    }

    pendingDeleteContact?.let { target ->
        LyraDeleteConfirmationDialog(
            title = "Delete Contact?",
            message = "Are you sure you want to permanently delete ${target.name} (${target.phoneNumber})?",
            onConfirm = {
                contactsViewModel.deleteContact(target) {
                    mainViewModel.loadData()
                }
                pendingDeleteContact = null
            },
            onDismiss = { pendingDeleteContact = null }
        )
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newPhone by remember { mutableStateOf("") }
        var markPriority by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Add Priority Contact",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { markPriority = !markPriority }
                    ) {
                        Checkbox(
                            checked = markPriority,
                            onCheckedChange = { markPriority = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primary)
                        )
                        Text(
                            text = "Mark as Priority Caller ★",
                            color = colors.textPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newPhone.isNotBlank()) {
                            contactsViewModel.addPriorityContact(newName, newPhone, markPriority)
                            mainViewModel.loadData()
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Text("Save", color = colors.onPrimaryButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    editingContact?.let { target ->
        EditContactDialog(
            contact = target,
            onDismiss = { editingContact = null },
            onSave = { newName, newPhone, newPhotoUri, isPriority ->
                contactsViewModel.updateContact(
                    original = target,
                    newName = newName,
                    newPhone = newPhone,
                    newPhotoUri = newPhotoUri,
                    isPriority = isPriority
                ) { updated ->
                    mainViewModel.selectContact(updated)
                    mainViewModel.loadData()
                }
                editingContact = null
            }
        )
    }

    pendingSimCallNumber?.let { targetNumber ->
        SimChooserDialog(
            phoneNumber = targetNumber,
            availableSims = availableSims,
            onSelectSim = { simKey, fixForFuture ->
                if (fixForFuture) {
                    mainViewModel.setPreferredSim(simKey)
                    mainViewModel.setFixDefaultSim(true)
                }
                pendingSimCallNumber = null
                mainViewModel.placeCall(
                    phoneNumber = targetNumber,
                    simKeyOverride = simKey
                )
            },
            onDismiss = { pendingSimCallNumber = null }
        )
    }
}
