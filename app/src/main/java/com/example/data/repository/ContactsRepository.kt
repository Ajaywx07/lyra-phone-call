package com.example.data.repository

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import com.example.data.local.dao.PriorityContactDao
import com.example.data.local.entity.PriorityContact
import com.example.data.local.preferences.LyraPreferences
import com.example.domain.model.CallerInfo
import com.example.domain.model.ContactItemData
import com.example.utils.PermissionHelper
import com.example.utils.PhoneNumberUtils
import com.example.utils.ProfileMediaStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class ContactsRepository(
    private val context: Context,
    private val priorityContactDao: PriorityContactDao,
    private val preferences: LyraPreferences
) {

    // Built-in labeled sample contacts shown only while READ_CONTACTS permission is not yet granted
    private val demoContacts = listOf(
        ContactItemData(
            id = "demo_1",
            name = "Ajay",
            phoneNumber = "+91 98765 43210",
            label = "Personal (Owner)",
            isPriority = true,
            isOwner = true,
            isDemoData = true
        ),
        ContactItemData(
            id = "demo_2",
            name = "Dr. Rohan Verma",
            phoneNumber = "+91 98111 22334",
            label = "Doctor",
            isPriority = true,
            isOwner = false,
            isDemoData = true
        ),
        ContactItemData(
            id = "demo_3",
            name = "Priya Sharma",
            phoneNumber = "+91 98222 33445",
            label = "Team Lead",
            isPriority = true,
            isOwner = false,
            isDemoData = true
        ),
        ContactItemData(
            id = "demo_4",
            name = "Vikram Malhotra",
            phoneNumber = "+91 98333 44556",
            label = "Client",
            isPriority = false,
            isOwner = false,
            isDemoData = true
        ),
        ContactItemData(
            id = "demo_5",
            name = "Sarah Jenkins",
            phoneNumber = "+1 555 019 2834",
            label = "Project Manager",
            isPriority = false,
            isOwner = false,
            isDemoData = true
        )
    )

    private fun findMatchingOverride(
        allOverrides: List<PriorityContact>,
        contactId: String?,
        phoneNumber: String
    ): PriorityContact? {
        if (!contactId.isNullOrBlank()) {
            val bySource = allOverrides.firstOrNull { it.sourceContactId == contactId }
            if (bySource != null) return bySource
            if (contactId.startsWith("local_")) {
                val localId = contactId.removePrefix("local_").toLongOrNull()
                if (localId != null) {
                    val byLocalId = allOverrides.firstOrNull { it.id == localId }
                    if (byLocalId != null) return byLocalId
                }
            }
        }
        return allOverrides.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, phoneNumber) }
    }

    suspend fun getContacts(): List<ContactItemData> = withContext(Dispatchers.IO) {
        val ownerPhone = preferences.ownerPhone.first()
        val ownerName = preferences.ownerName.first()
        val deletedKeys = preferences.deletedContactKeys.first()
        val allOverrides = priorityContactDao.getAllPriorityContacts().first()
        val overrideBySourceId = HashMap<String, PriorityContact>(allOverrides.size)
        val overrideByLocalId = HashMap<Long, PriorityContact>(allOverrides.size)
        val overrideByCore = HashMap<String, PriorityContact>(allOverrides.size)
        for (o in allOverrides) {
            if (o.sourceContactId.isNotBlank()) overrideBySourceId.putIfAbsent(o.sourceContactId, o)
            overrideByLocalId[o.id] = o
            val core = PhoneNumberUtils.extractCoreDigits(o.phoneNumber)
            if (core.isNotEmpty()) overrideByCore.putIfAbsent(core, o)
        }

        fun findOverrideFast(contactId: String?, phoneCore: String): PriorityContact? {
            if (!contactId.isNullOrBlank()) {
                overrideBySourceId[contactId]?.let { return it }
                if (contactId.startsWith("local_")) {
                    val localId = contactId.removePrefix("local_").toLongOrNull()
                    if (localId != null) {
                        overrideByLocalId[localId]?.let { return it }
                    }
                }
            }
            return if (phoneCore.isNotEmpty()) overrideByCore[phoneCore] else null
        }

        fun isMarkedDeleted(id: String, core: String): Boolean {
            return id in deletedKeys || (core.isNotEmpty() && core in deletedKeys)
        }

        if (!PermissionHelper.hasContactsPermission(context)) {
            val mergedDemo = mutableListOf<ContactItemData>()
            val seenNumbers = mutableSetOf<String>()

            for (demo in demoContacts) {
                val base = if (demo.isOwner) {
                    demo.copy(name = ownerName, phoneNumber = ownerPhone)
                } else {
                    demo
                }
                val baseCore = PhoneNumberUtils.extractCoreDigits(base.phoneNumber)
                val override = findOverrideFast(base.id, baseCore)
                    ?: if (base.isOwner) allOverrides.firstOrNull { it.isOwner } else null
                val finalName = override?.contactName?.takeIf { it.isNotBlank() } ?: base.name
                val finalNumber = override?.phoneNumber?.takeIf { it.isNotBlank() } ?: base.phoneNumber
                val finalCore = PhoneNumberUtils.extractCoreDigits(finalNumber)
                if (!base.isOwner && isMarkedDeleted(base.id, finalCore)) continue
                val finalPhoto = override?.photoUri?.takeIf { it.isNotBlank() } ?: base.photoUri
                val finalPriority = base.isOwner || (override?.isStarred ?: base.isPriority)

                if (finalCore.isNotEmpty()) seenNumbers.add(finalCore)

                mergedDemo.add(
                    base.copy(
                        name = finalName,
                        phoneNumber = PhoneNumberUtils.formatDisplay(finalNumber).ifBlank { finalNumber },
                        photoUri = finalPhoto,
                        isPriority = finalPriority
                    )
                )
            }

            for (p in allOverrides) {
                val core = PhoneNumberUtils.extractCoreDigits(p.phoneNumber)
                if (!p.isOwner && isMarkedDeleted("local_${p.id}", core)) continue
                if (core.isNotEmpty() && seenNumbers.add(core)) {
                    mergedDemo.add(
                        ContactItemData(
                            id = "local_${p.id}",
                            name = p.contactName,
                            phoneNumber = PhoneNumberUtils.formatDisplay(p.phoneNumber).ifBlank { p.phoneNumber },
                            photoUri = p.photoUri.takeIf { it.isNotBlank() },
                            label = p.notes.ifBlank { if (p.isStarred) "Priority" else "Local Contact" },
                            isPriority = p.isStarred || p.isOwner,
                            isOwner = p.isOwner,
                            isDemoData = false
                        )
                    )
                }
            }

            return@withContext mergedDemo.sortedWith(
                compareByDescending<ContactItemData> { it.isOwner }
                    .thenByDescending { it.isPriority }
                    .thenBy { it.name.lowercase() }
            )
        }

        val emailByContactId = loadEmailsByContactId()
        val contactsList = mutableListOf<ContactItemData>()
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )

        var cursor: Cursor? = null
        val seenNumbers = mutableSetOf<String>()
        val matchedOverrideIds = mutableSetOf<Long>()

        try {
            cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) ?: "" else ""
                    val rawName = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val rawNumber = if (numberIdx >= 0) it.getString(numberIdx) ?: "" else ""
                    val systemPhotoUri = if (photoIdx >= 0) it.getString(photoIdx) else null
                    val type = if (typeIdx >= 0) it.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER

                    val rawCore = PhoneNumberUtils.extractCoreDigits(rawNumber)
                    val override = findOverrideFast(id, rawCore)
                    if (override != null) {
                        matchedOverrideIds.add(override.id)
                    }

                    val effectiveName = override?.contactName?.takeIf { n -> n.isNotBlank() } ?: rawName
                    val effectiveNumber = override?.phoneNumber?.takeIf { n -> n.isNotBlank() } ?: rawNumber
                    val effectivePhoto = override?.photoUri?.takeIf { p -> p.isNotBlank() } ?: systemPhotoUri

                    val coreNumber = PhoneNumberUtils.extractCoreDigits(effectiveNumber)
                    if (coreNumber.isEmpty() || !seenNumbers.add(coreNumber)) {
                        continue
                    }

                    val label = when (type) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        else -> "Other"
                    }

                    val isOwner = PhoneNumberUtils.areNumbersEqual(effectiveNumber, ownerPhone) ||
                        (override?.isOwner == true)
                    if (!isOwner && isMarkedDeleted(id, coreNumber)) {
                        continue
                    }
                    val isPriority = isOwner || (override?.isStarred == true)

                    contactsList.add(
                        ContactItemData(
                            id = id.ifBlank { "c_$coreNumber" },
                            name = if (isOwner && !effectiveName.endsWith("(Owner)")) "$effectiveName (Owner)" else effectiveName,
                            phoneNumber = PhoneNumberUtils.formatDisplay(effectiveNumber).ifBlank { effectiveNumber },
                            photoUri = effectivePhoto,
                            email = emailByContactId[id],
                            label = label,
                            isPriority = isPriority,
                            isOwner = isOwner,
                            isDemoData = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        // Also include locally added/edited contacts not already in system Contacts
        for (p in allOverrides) {
            if (p.id in matchedOverrideIds) continue
            val core = PhoneNumberUtils.extractCoreDigits(p.phoneNumber)
            if (!p.isOwner && isMarkedDeleted("local_${p.id}", core)) continue
            if (core.isNotEmpty() && seenNumbers.add(core)) {
                contactsList.add(
                    ContactItemData(
                        id = "local_${p.id}",
                        name = p.contactName,
                        phoneNumber = PhoneNumberUtils.formatDisplay(p.phoneNumber).ifBlank { p.phoneNumber },
                        photoUri = p.photoUri.takeIf { it.isNotBlank() },
                        label = p.notes.ifBlank { if (p.isStarred) "Priority" else "Mobile" },
                        isPriority = p.isStarred || p.isOwner,
                        isOwner = p.isOwner,
                        isDemoData = false
                    )
                )
            }
        }

        contactsList.sortedWith(
            compareByDescending<ContactItemData> { it.isOwner }
                .thenByDescending { it.isPriority }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun loadEmailsByContactId(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null,
                null,
                null
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val addrIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (it.moveToNext()) {
                    val cid = if (idIdx >= 0) it.getString(idIdx) else null
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) else null
                    if (!cid.isNullOrBlank() && !addr.isNullOrBlank() && !map.containsKey(cid)) {
                        map[cid] = addr.trim()
                    }
                }
            }
        } catch (_: Exception) {
            // Best-effort email lookup
        } finally {
            cursor?.close()
        }
        return map
    }

    private data class PhoneLookupMatch(
        val contactId: String,
        val displayName: String,
        val photoUri: String?,
        val label: String,
        val isStarred: Boolean
    )

    private fun lookupSystemContactByPhone(phoneNumber: String): PhoneLookupMatch? {
        if (!PermissionHelper.hasContactsPermission(context) || phoneNumber.isBlank()) return null
        var cursor: Cursor? = null
        return try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            cursor = context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup.TYPE,
                    ContactsContract.PhoneLookup.STARRED
                ),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val typeIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.TYPE)
                val starredIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.STARRED)

                val contactId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                val displayName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val photoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                val isStarred = starredIdx >= 0 && cursor.getInt(starredIdx) == 1
                val label = when (type) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                    else -> "Other"
                }
                if (displayName.isNotBlank() || !photoUri.isNullOrBlank()) {
                    PhoneLookupMatch(
                        contactId = contactId,
                        displayName = displayName,
                        photoUri = photoUri,
                        label = label,
                        isStarred = isStarred
                    )
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Fast O(1) caller resolution by phone number across owner settings, local priority/override DB,
     * and indexed ContactsContract.PhoneLookup (avoiding expensive full contacts + emails scans).
     */
    suspend fun resolveCaller(rawNumber: String?): CallerInfo = withContext(Dispatchers.IO) {
        val number = rawNumber ?: ""
        val formatted = PhoneNumberUtils.formatDisplay(number)
        val snapshot = preferences.getIncomingCallSettingsSnapshot()
        val ownerPhone = snapshot.ownerPhone
        val ownerName = snapshot.ownerName

        val systemMatch = lookupSystemContactByPhone(number)
        val systemPhotoUri = systemMatch?.photoUri
        val allOverrides = priorityContactDao.getAllPriorityContacts().first()
        val overrideMatch = allOverrides.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, number) }

        // 1. Check Owner match
        if (PhoneNumberUtils.areNumbersEqual(number, ownerPhone)) {
            val ownerOverride = overrideMatch ?: allOverrides.firstOrNull { it.isOwner }
            val resolvedPhoto = ownerOverride?.photoUri?.takeIf { it.isNotBlank() } ?: systemPhotoUri
            return@withContext CallerInfo(
                phoneNumber = number,
                formattedNumber = formatted,
                displayName = ownerOverride?.contactName?.takeIf { it.isNotBlank() } ?: ownerName,
                photoUri = resolvedPhoto,
                isKnown = true,
                isPriority = true,
                isOwner = true,
                contactLabel = "Owner / Primary Device"
            )
        }

        // 2. Check Priority / Edited Local DB match
        if (overrideMatch != null) {
            return@withContext CallerInfo(
                phoneNumber = number,
                formattedNumber = formatted,
                displayName = overrideMatch.contactName,
                photoUri = overrideMatch.photoUri.takeIf { it.isNotBlank() } ?: systemPhotoUri,
                isKnown = true,
                isPriority = overrideMatch.isStarred || overrideMatch.isOwner,
                isOwner = overrideMatch.isOwner,
                contactLabel = if (overrideMatch.isStarred) "Priority Contact" else "Saved Contact"
            )
        }

        // 3. Indexed System Contacts Lookup (O(1) via PhoneLookup)
        if (PermissionHelper.hasContactsPermission(context)) {
            if (systemMatch != null) {
                val deletedKeys = preferences.deletedContactKeys.first()
                val core = PhoneNumberUtils.extractCoreDigits(number)
                val isDeleted = (systemMatch.contactId.isNotBlank() && systemMatch.contactId in deletedKeys) ||
                    (core.isNotEmpty() && core in deletedKeys)
                if (!isDeleted && systemMatch.displayName.isNotBlank()) {
                    return@withContext CallerInfo(
                        phoneNumber = number,
                        formattedNumber = formatted,
                        displayName = systemMatch.displayName,
                        photoUri = systemPhotoUri,
                        isKnown = true,
                        isPriority = systemMatch.isStarred,
                        isOwner = false,
                        contactLabel = if (systemMatch.isStarred) "Priority Contact" else systemMatch.label
                    )
                }
            }
        } else {
            val demoMatch = demoContacts.firstOrNull { PhoneNumberUtils.areNumbersEqual(it.phoneNumber, number) }
            if (demoMatch != null) {
                return@withContext CallerInfo(
                    phoneNumber = number,
                    formattedNumber = formatted,
                    displayName = demoMatch.name,
                    photoUri = demoMatch.photoUri,
                    isKnown = true,
                    isPriority = demoMatch.isPriority,
                    isOwner = demoMatch.isOwner,
                    contactLabel = demoMatch.label
                )
            }
        }

        // 4. Unknown Caller
        CallerInfo(
            phoneNumber = number,
            formattedNumber = formatted,
            displayName = if (number.isNotBlank()) formatted else "Unknown Caller",
            photoUri = null,
            isKnown = false,
            isPriority = false,
            isOwner = false,
            contactLabel = "Unknown Caller"
        )
    }

    /**
     * Copies a newly picked photo (e.g. Android Photo Picker content:// URI) into app-internal
     * storage so it survives restarts and Photo Picker temporary URI permission expiry.
     */
    private fun persistPhotoLocally(photoUriString: String?, contactKey: String): String? {
        val raw = photoUriString?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val photosDir = File(context.filesDir, "contact_photos")
        if (raw.startsWith("file://")) {
            val existingFile = runCatching { Uri.parse(raw).path?.let { File(it) } }.getOrNull()
            if (existingFile != null && existingFile.exists()) {
                return raw
            }
        }

        return runCatching {
            if (!photosDir.exists()) photosDir.mkdirs()
            val uri = Uri.parse(raw)
            val safeKey = contactKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "contact" }
            val destFile = File(photosDir, "${safeKey}_${System.currentTimeMillis()}.jpg")

            val maxDim = 512
            val sampledBitmap = ProfileMediaStorage.decodeSampledBitmap(context, uri, maxDim = maxDim)
                ?: return@runCatching null

            val scaled = if (sampledBitmap.width > maxDim || sampledBitmap.height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / sampledBitmap.width, maxDim.toFloat() / sampledBitmap.height)
                val targetW = (sampledBitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (sampledBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true).also {
                    if (it !== sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }
            destFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 86, out)
            }
            scaled.recycle()
            Uri.fromFile(destFile).toString()
        }.getOrNull() ?: raw
    }

    /**
     * Updates a contact's name, phone number, profile photo, and priority flag.
     * Persists locally in Room (+ Owner preferences if applicable) and syncs to Android
     * ContactsContract when WRITE_CONTACTS permission is granted.
     */
    suspend fun updateContact(
        original: ContactItemData,
        newName: String,
        newPhoneNumber: String,
        newPhotoUri: String?,
        isPriority: Boolean
    ): ContactItemData = withContext(Dispatchers.IO) {
        val cleanName = newName.trim().removeSuffix(" (Owner)").ifBlank { original.name.removeSuffix(" (Owner)") }
        val cleanPhoneRaw = newPhoneNumber.trim().ifBlank { original.phoneNumber }
        val formattedPhone = PhoneNumberUtils.formatDisplay(cleanPhoneRaw).ifBlank { cleanPhoneRaw }
        val persistedPhoto = persistPhotoLocally(newPhotoUri, original.id)

        if (original.isOwner) {
            preferences.setOwnerName(cleanName)
            preferences.setOwnerPhone(formattedPhone)
        }

        val allOverrides = priorityContactDao.getAllPriorityContacts().first()
        val existingOverride = findMatchingOverride(allOverrides, original.id, original.phoneNumber)
            ?: if (original.isOwner) allOverrides.firstOrNull { it.isOwner } else null

        val sourceId = when {
            original.id.startsWith("local_") -> existingOverride?.sourceContactId ?: ""
            original.id.startsWith("c_") -> existingOverride?.sourceContactId ?: ""
            else -> original.id
        }

        val updatedRecord = if (existingOverride != null) {
            existingOverride.copy(
                phoneNumber = formattedPhone,
                contactName = cleanName,
                isOwner = original.isOwner || existingOverride.isOwner,
                photoUri = persistedPhoto ?: "",
                isStarred = if (original.isOwner) true else isPriority,
                sourceContactId = sourceId.ifBlank { existingOverride.sourceContactId }
            )
        } else {
            PriorityContact(
                phoneNumber = formattedPhone,
                contactName = cleanName,
                isOwner = original.isOwner,
                notes = original.label.takeIf { it.isNotBlank() && it != "Mobile" } ?: "",
                photoUri = persistedPhoto ?: "",
                isStarred = if (original.isOwner) true else isPriority,
                sourceContactId = sourceId,
                addedTimestamp = System.currentTimeMillis()
            )
        }
        val savedId = priorityContactDao.insertPriorityContact(updatedRecord)
        val restoredKeys = buildSet {
            add(original.id)
            PhoneNumberUtils.extractCoreDigits(cleanPhoneRaw).takeIf { it.isNotEmpty() }?.let { add(it) }
            PhoneNumberUtils.extractCoreDigits(original.phoneNumber).takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        preferences.unmarkContactDeleted(restoredKeys)

        // Best-effort sync to Android system ContactsContract when permitted for real system contacts
        val numericContactId = sourceId.toLongOrNull()
        if (numericContactId != null &&
            !original.isDemoData &&
            PermissionHelper.hasWriteContactsPermission(context)
        ) {
            syncSystemContactChanges(
                contactId = numericContactId,
                newName = cleanName,
                newPhone = formattedPhone,
                photoUri = persistedPhoto
            )
        }

        val displayResultName = if (original.isOwner && !cleanName.endsWith("(Owner)")) {
            "$cleanName (Owner)"
        } else {
            cleanName
        }

        original.copy(
            id = if (original.id.startsWith("local_")) "local_$savedId" else original.id,
            name = displayResultName,
            phoneNumber = formattedPhone,
            photoUri = persistedPhoto,
            isPriority = if (original.isOwner) true else isPriority
        )
    }

    private fun syncSystemContactChanges(
        contactId: Long,
        newName: String,
        newPhone: String,
        photoUri: String?
    ) {
        runCatching {
            val resolver = context.contentResolver
            val ops = ArrayList<ContentProviderOperation>()

            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(
                            contactId.toString(),
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(
                            contactId.toString(),
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, newPhone)
                    .build()
            )

            resolver.applyBatch(ContactsContract.AUTHORITY, ops)

            // Update or insert contact photo on first RawContact
            var rawContactId: Long? = null
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID}=?",
                arrayOf(contactId.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    rawContactId = c.getLong(0)
                }
            }

            rawContactId?.let { rcId ->
                if (photoUri.isNullOrBlank()) {
                    resolver.delete(
                        ContactsContract.Data.CONTENT_URI,
                        "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(
                            rcId.toString(),
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                    )
                } else {
                    val maxSyncDim = 480
                    val bmp = ProfileMediaStorage.decodeSampledBitmap(context, Uri.parse(photoUri), maxDim = maxSyncDim)
                    val photoBytes = if (bmp != null) {
                        val finalBmp = if (bmp.width > maxSyncDim || bmp.height > maxSyncDim) {
                            val ratio = minOf(maxSyncDim.toFloat() / bmp.width, maxSyncDim.toFloat() / bmp.height)
                            val targetW = (bmp.width * ratio).toInt().coerceAtLeast(1)
                            val targetH = (bmp.height * ratio).toInt().coerceAtLeast(1)
                            Bitmap.createScaledBitmap(bmp, targetW, targetH, true).also {
                                if (it !== bmp) bmp.recycle()
                            }
                        } else {
                            bmp
                        }
                        ByteArrayOutputStream().use { out ->
                            finalBmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                            finalBmp.recycle()
                            out.toByteArray()
                        }
                    } else {
                        null
                    }
                    if (photoBytes != null && photoBytes.isNotEmpty()) {
                        val values = ContentValues().apply {
                            put(ContactsContract.Data.RAW_CONTACT_ID, rcId)
                            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                            put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        }
                        val updatedRows = resolver.update(
                            ContactsContract.Data.CONTENT_URI,
                            values,
                            "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                            arrayOf(
                                rcId.toString(),
                                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                            )
                        )
                        if (updatedRows == 0) {
                            resolver.insert(ContactsContract.Data.CONTENT_URI, values)
                        }
                    }
                }
            }
        }
    }

    suspend fun addCustomContact(
        name: String,
        phoneNumber: String,
        isPriority: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val trimmedNumber = PhoneNumberUtils.formatDisplay(phoneNumber.trim()).ifBlank { phoneNumber.trim() }
        val trimmedName = name.trim()
        if (trimmedNumber.isBlank() || trimmedName.isBlank()) return@withContext

        // Prevent duplicate numbers in PriorityContact table
        val existingAll = priorityContactDao.getAllPriorityContacts().first()
        val targetCore = PhoneNumberUtils.extractCoreDigits(trimmedNumber)
        if (targetCore.isNotEmpty()) {
            preferences.unmarkContactDeleted(setOf(targetCore))
        }
        val duplicate = existingAll.firstOrNull {
            targetCore.isNotEmpty() && PhoneNumberUtils.extractCoreDigits(it.phoneNumber) == targetCore
        }
        if (duplicate != null) {
            priorityContactDao.insertPriorityContact(
                duplicate.copy(
                    contactName = trimmedName,
                    isStarred = isPriority || duplicate.isStarred,
                    notes = if (isPriority) "Priority Contact" else duplicate.notes
                )
            )
        } else {
            priorityContactDao.insertPriorityContact(
                PriorityContact(
                    phoneNumber = trimmedNumber,
                    contactName = trimmedName,
                    isOwner = false,
                    notes = if (isPriority) "Priority Contact" else "",
                    isStarred = isPriority,
                    addedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteContact(contact: ContactItemData) = withContext(Dispatchers.IO) {
        if (contact.isOwner) return@withContext
        val allOverrides = priorityContactDao.getAllPriorityContacts().first()
        val matchingOverrides = allOverrides.filter {
            it.sourceContactId == contact.id ||
                "local_${it.id}" == contact.id ||
                PhoneNumberUtils.areNumbersEqual(it.phoneNumber, contact.phoneNumber)
        }
        matchingOverrides.forEach { override ->
            priorityContactDao.deletePriorityContactById(override.id)
        }

        val keysToHide = buildSet {
            if (contact.id.isNotBlank()) add(contact.id)
            PhoneNumberUtils.extractCoreDigits(contact.phoneNumber)
                .takeIf { it.isNotEmpty() }
                ?.let { add(it) }
        }
        preferences.markContactDeleted(keysToHide)

        val numericId = contact.id.toLongOrNull()
            ?: matchingOverrides.firstNotNullOfOrNull { it.sourceContactId.toLongOrNull() }
        if (numericId != null &&
            !contact.isDemoData &&
            PermissionHelper.hasWriteContactsPermission(context)
        ) {
            runCatching {
                context.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    "${ContactsContract.RawContacts.CONTACT_ID}=?",
                    arrayOf(numericId.toString())
                )
            }
        }
    }

    suspend fun togglePriorityContact(contact: ContactItemData) = withContext(Dispatchers.IO) {
        val formatted = PhoneNumberUtils.formatDisplay(contact.phoneNumber)
        val allOverrides = priorityContactDao.getAllPriorityContacts().first()
        val existing = findMatchingOverride(allOverrides, contact.id, contact.phoneNumber)
            ?: priorityContactDao.getPriorityContact(formatted)

        if (existing != null) {
            if (existing.isStarred) {
                val hasCustomEdits = existing.photoUri.isNotBlank() || existing.sourceContactId.isNotBlank()
                if (hasCustomEdits) {
                    priorityContactDao.insertPriorityContact(existing.copy(isStarred = false))
                } else {
                    priorityContactDao.deletePriorityContactById(existing.id)
                }
            } else {
                priorityContactDao.insertPriorityContact(existing.copy(isStarred = true))
            }
        } else {
            priorityContactDao.insertPriorityContact(
                PriorityContact(
                    phoneNumber = formatted.ifBlank { contact.phoneNumber },
                    contactName = contact.name.removeSuffix(" (Owner)"),
                    isOwner = contact.isOwner,
                    notes = "Priority Contact",
                    photoUri = contact.photoUri ?: "",
                    isStarred = true,
                    sourceContactId = if (contact.id.startsWith("local_") || contact.id.startsWith("c_")) "" else contact.id
                )
            )
        }
    }

    suspend fun exportContactsBackup(): String = withContext(Dispatchers.IO) {
        val contacts = getContacts()
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject()
            obj.put("id", contact.id)
            obj.put("name", contact.name)
            obj.put("phoneNumber", contact.phoneNumber)
            obj.put("email", contact.email ?: "")
            obj.put("label", contact.label)
            obj.put("isPriority", contact.isPriority)
            jsonArray.put(obj)
        }
        val root = JSONObject()
        root.put("app", "Lyra Calling Assistant")
        root.put("version", "1.1")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("totalContacts", contacts.size)
        root.put("contacts", jsonArray)
        root.toString(2)
    }

    suspend fun restoreContactsBackup(jsonContent: String): Int = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonContent)
            val array = root.optJSONArray("contacts") ?: return@withContext 0
            val existingPriority = priorityContactDao.getAllPriorityContacts().first()
            val seenCores = existingPriority.map { PhoneNumberUtils.extractCoreDigits(it.phoneNumber) }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            var importedCount = 0
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name").trim()
                val number = obj.optString("phoneNumber").trim()
                val isPriority = obj.optBoolean("isPriority", false)
                val core = PhoneNumberUtils.extractCoreDigits(number)
                if (core.isNotEmpty() && name.isNotBlank() && seenCores.add(core)) {
                    priorityContactDao.insertPriorityContact(
                        PriorityContact(
                            phoneNumber = PhoneNumberUtils.formatDisplay(number),
                            contactName = name,
                            isOwner = false,
                            notes = if (isPriority) "Priority Contact" else "",
                            isStarred = isPriority
                        )
                    )
                    importedCount++
                }
            }
            importedCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
