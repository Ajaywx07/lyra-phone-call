package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.remote.CloudContactBackup
import com.example.domain.model.ContactItemData
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudBackupRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun backupContacts_authenticatedOwner_createsUpdatesAndReadsContacts() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudBackupRepository(firestore)

        val contacts = listOf(
            ContactItemData(
                id = "1",
                name = "Ajay",
                phoneNumber = "+91 98765 43210",
                label = "Owner",
                isPriority = true,
                isOwner = true
            ),
            ContactItemData(
                id = "2",
                name = "Dr. Rohan",
                phoneNumber = "+91 98111 22334",
                label = "Doctor",
                isPriority = true,
                isOwner = false
            )
        )

        val backupResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.backupContacts(uid, contacts)
        }
        assertTrue(backupResult.isSuccess)
        assertEquals(2, backupResult.getOrThrow())

        // Re-run backup to verify update branch preserves createdAt and succeeds
        val reBackupResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.backupContacts(
                uid,
                listOf(contacts.first().copy(name = "Ajay Updated"))
            )
        }
        assertTrue(reBackupResult.isSuccess)

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getCloudContacts(uid)
        }
        assertTrue(fetched.isSuccess)
        val items = fetched.getOrThrow()
        assertTrue(items.any { it.name == "Ajay Updated" && it.ownerContact })
    }

    @Test
    fun observeCloudContacts_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = CloudBackupRepository(firestore)
        val docId = "test_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val savedId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertSingleContact(
                uid,
                CloudContactBackup(
                    id = docId,
                    userId = uid,
                    name = "Realtime Contact",
                    phoneNumber = "+91 90000 11111",
                    label = "Work",
                    priorityContact = true,
                    ownerContact = false
                )
            ).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCloudContacts(uid).first { list ->
                list.any { it.id == savedId && it.name == "Realtime Contact" }
            }
        }
        assertTrue(emitted.any { it.id == savedId })
    }

    @Test
    fun getCloudContact_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudBackupRepository(firestore)
        val docId = "cross_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertSingleContact(
                aliceUid,
                CloudContactBackup(
                    id = docId,
                    userId = aliceUid,
                    name = "Alice Private Contact",
                    phoneNumber = "+91 91234 56789",
                    label = "Mobile",
                    priorityContact = false,
                    ownerContact = false
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudBackupRepository(firestore)
        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getCloudContact(aliceUid, docId)
        }
        assertTrue(crossReadResult.isFailure)
        val firestoreException = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeCloudContacts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = CloudBackupRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeCloudContacts("some_user_uid").first()
            }
        } catch (t: Throwable) {
            caughtException = t
        }
        assertNotNull("Expected PERMISSION_DENIED exception when signed out", caughtException)
        val firestoreException = caughtException!!.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException cause, got $caughtException", firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.lyra@test.com"
        const val BOB_EMAIL = "bob.lyra@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
