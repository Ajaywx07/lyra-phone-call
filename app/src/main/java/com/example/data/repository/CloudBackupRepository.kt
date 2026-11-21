package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.remote.CloudContactBackup
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.model.ContactItemData
import com.example.utils.PhoneNumberUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class CloudBackupRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userContactsCollection(userId: String) =
        db.collection("users").document(userId).collection("cloud_contacts")

    fun observeCloudContacts(userId: String): Flow<List<CloudContactBackup>> {
        return userContactsCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CloudContactBackup::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<CloudContactBackup> { it.ownerContact }
                        .thenByDescending { it.priorityContact }
                        .thenBy { it.name.lowercase() }
                )
            }
    }

    suspend fun getCloudContacts(userId: String): Result<List<CloudContactBackup>> {
        val path = "users/$userId/cloud_contacts"
        return try {
            val snapshot = userContactsCollection(userId).get().await()
            val list = snapshot.toObjects(
                CloudContactBackup::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedWith(
                compareByDescending<CloudContactBackup> { it.ownerContact }
                    .thenByDescending { it.priorityContact }
                    .thenBy { it.name.lowercase() }
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getCloudContact(userId: String, contactId: String): Result<CloudContactBackup?> {
        val docRef = userContactsCollection(userId).document(contactId)
        return try {
            val snap = docRef.get().await()
            val item = snap.toObject(
                CloudContactBackup::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun upsertSingleContact(userId: String, contact: CloudContactBackup): Result<String> {
        val docId = contact.id.takeIf { it.matches(VALID_ID_REGEX) } ?: stableDocId(contact.phoneNumber)
        val docRef = userContactsCollection(userId).document(docId)
        return try {
            val existing = docRef.get().await()
            val payload = if (existing.exists()) {
                val created = existing.getTimestamp("createdAt")
                contact.copy(userId = userId, createdAt = created).toCreateMap()
            } else {
                contact.copy(userId = userId, createdAt = null).toCreateMap()
            }
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val op = if (contact.id.isNotBlank()) OperationType.UPDATE else OperationType.CREATE
            handleFirestoreError(e, op, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun backupContacts(userId: String, contacts: List<ContactItemData>): Result<Int> {
        val colPath = "users/$userId/cloud_contacts"
        return try {
            val validContacts = contacts.filter {
                it.phoneNumber.isNotBlank() && it.name.isNotBlank()
            }
            if (validContacts.isEmpty()) return Result.success(0)

            val existingSnapshot = userContactsCollection(userId).get().await()
            val existingCreatedAtById = existingSnapshot.documents.associate { doc ->
                doc.id to doc.getTimestamp("createdAt")
            }

            val chunks = validContacts.chunked(400)
            var totalSaved = 0
            for (chunk in chunks) {
                val batch = db.batch()
                for (contact in chunk) {
                    val docId = stableDocId(contact.phoneNumber)
                    val docRef = userContactsCollection(userId).document(docId)
                    val existingCreatedAt = existingCreatedAtById[docId]
                    val model = CloudContactBackup(
                        id = docId,
                        userId = userId,
                        name = contact.name.trim().take(120),
                        phoneNumber = contact.phoneNumber.trim().take(40),
                        label = contact.label.trim().ifBlank { "Mobile" }.take(40),
                        priorityContact = contact.isPriority,
                        ownerContact = contact.isOwner,
                        createdAt = existingCreatedAt
                    )
                    batch.set(docRef, model.toCreateMap())
                    totalSaved++
                }
                batch.commit().await()
            }
            Result.success(totalSaved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudContact(userId: String, contactId: String): Result<Unit> {
        val docRef = userContactsCollection(userId).document(contactId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

        fun stableDocId(phoneNumber: String): String {
            val core = PhoneNumberUtils.extractCoreDigits(phoneNumber)
            if (core.isNotEmpty() && core.length <= 32) {
                return "c_$core"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(phoneNumber.toByteArray())
                .take(12)
                .joinToString("") { "%02x".format(it) }
            return "c_$digest"
        }
    }
}
