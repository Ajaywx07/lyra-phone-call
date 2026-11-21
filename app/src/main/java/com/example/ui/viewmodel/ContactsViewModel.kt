package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.LyraDatabase
import com.example.data.local.preferences.LyraPreferences
import com.example.data.repository.ContactsRepository
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.utils.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContactFilter(val label: String) {
    ALL("All"),
    PRIORITY("Priority"),
    SYSTEM("Device")
}

sealed interface ContactsUiState {
    data object Loading : ContactsUiState
    data class Success(
        val priorityContacts: List<ContactItemData>,
        val regularContacts: List<ContactItemData>,
        val totalContactsCount: Int,
        val isUsingDemoFallback: Boolean
    ) : ContactsUiState
    data class Error(val message: String) : ContactsUiState
}

/**
 * Dedicated ViewModel that retrieves system contacts from Android ContactsContract
 * (merged with local priority & owner profiles) and exposes them for LazyColumn rendering,
 * instant caller identification lookup, and priority contact management.
 */
class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = LyraDatabase.getDatabase(context)
    private val preferences = LyraPreferences(context)
    private val contactsRepository = ContactsRepository(
        context = context,
        priorityContactDao = database.priorityContactDao(),
        preferences = preferences
    )

    private val _allContacts = MutableStateFlow<List<ContactItemData>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ContactFilter.ALL)
    val selectedFilter: StateFlow<ContactFilter> = _selectedFilter.asStateFlow()

    private var loadJob: Job? = null
    private val _hasContactsPermission = MutableStateFlow(false)
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    private val _lookupPhoneNumber = MutableStateFlow("")
    val lookupPhoneNumber: StateFlow<String> = _lookupPhoneNumber.asStateFlow()

    private val _identifiedCaller = MutableStateFlow<CallerInfo?>(null)
    val identifiedCaller: StateFlow<CallerInfo?> = _identifiedCaller.asStateFlow()

    val uiState: StateFlow<ContactsUiState> = combine(
        _allContacts,
        _searchQuery,
        _selectedFilter,
        _isLoading,
        _errorMessage
    ) { contacts, query, filter, isLoading, error ->
        when {
            error != null -> ContactsUiState.Error(error)
            isLoading && contacts.isEmpty() -> ContactsUiState.Loading
            else -> {
                val filteredByTab = when (filter) {
                    ContactFilter.ALL -> contacts
                    ContactFilter.PRIORITY -> contacts.filter { it.isPriority || it.isOwner }
                    ContactFilter.SYSTEM -> contacts.filter { !it.isDemoData }
                }

                val filtered = if (query.isBlank()) {
                    filteredByTab
                } else {
                    filteredByTab.filter { contact ->
                        contact.name.contains(query, ignoreCase = true) ||
                            contact.phoneNumber.contains(query, ignoreCase = true) ||
                            contact.label.contains(query, ignoreCase = true)
                    }
                }

                val priority = filtered.filter { it.isPriority || it.isOwner }
                val regular = filtered.filter { !it.isPriority && !it.isOwner }
                val usingDemo = contacts.isNotEmpty() && contacts.all { it.isDemoData }

                ContactsUiState.Success(
                    priorityContacts = priority,
                    regularContacts = regular,
                    totalContactsCount = contacts.size,
                    isUsingDemoFallback = usingDemo
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContactsUiState.Loading
    )

    init {
        loadSystemContacts()
    }

    fun loadSystemContacts() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = _allContacts.value.isEmpty()
            _errorMessage.value = null
            _hasContactsPermission.value = withContext(Dispatchers.IO) {
                PermissionHelper.hasContactsPermission(context)
            }
            try {
                val retrieved = contactsRepository.getContacts()
                _allContacts.value = retrieved
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to load system contacts."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: ContactFilter) {
        _selectedFilter.value = filter
    }

    fun togglePriority(contact: ContactItemData) {
        viewModelScope.launch {
            try {
                contactsRepository.togglePriorityContact(contact)
                _allContacts.value = contactsRepository.getContacts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to update priority status."
            }
        }
    }

    fun addPriorityContact(name: String, phoneNumber: String, isPriority: Boolean = true) {
        if (name.isBlank() || phoneNumber.isBlank()) return
        viewModelScope.launch {
            try {
                contactsRepository.addCustomContact(name, phoneNumber, isPriority)
                _allContacts.value = contactsRepository.getContacts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to add contact."
            }
        }
    }

    fun updateContact(
        original: ContactItemData,
        newName: String,
        newPhone: String,
        newPhotoUri: String?,
        isPriority: Boolean,
        onUpdated: (ContactItemData) -> Unit = {}
    ) {
        if (newName.isBlank() || newPhone.isBlank()) return
        viewModelScope.launch {
            try {
                val updated = contactsRepository.updateContact(
                    original = original,
                    newName = newName,
                    newPhoneNumber = newPhone,
                    newPhotoUri = newPhotoUri,
                    isPriority = isPriority
                )
                _allContacts.value = contactsRepository.getContacts()
                onUpdated(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to update contact."
            }
        }
    }

    fun deleteContact(contact: ContactItemData, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                contactsRepository.deleteContact(contact)
                _allContacts.value = contactsRepository.getContacts()
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to delete contact."
            }
        }
    }

    fun onLookupPhoneChange(number: String) {
        _lookupPhoneNumber.value = number
        if (number.isBlank()) {
            _identifiedCaller.value = null
        }
    }

    fun identifyCaller(phoneNumber: String = _lookupPhoneNumber.value) {
        if (phoneNumber.isBlank()) {
            _identifiedCaller.value = null
            return
        }
        viewModelScope.launch {
            try {
                _identifiedCaller.value = contactsRepository.resolveCaller(phoneNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Caller identification failed."
            }
        }
    }

    fun clearCallerLookup() {
        _lookupPhoneNumber.value = ""
        _identifiedCaller.value = null
    }
}
