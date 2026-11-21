package com.example.utils

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data class Cancelled(val message: String) : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

object GoogleAuthHelper {

    private const val TAG = "GoogleAuthHelper"

    private fun resolveServerClientId(context: Context): String {
        val generatedResourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        val googleServicesClientId = if (generatedResourceId != 0) {
            context.getString(generatedResourceId).trim()
        } else {
            ""
        }
        return googleServicesClientId.ifBlank { BuildConfig.GOOGLE_WEB_CLIENT_ID.trim() }
    }

    suspend fun signInWithGoogle(activityContext: Context): GoogleSignInOutcome {
        val serverClientId = resolveServerClientId(activityContext)
        if (serverClientId.isBlank()) {
            return GoogleSignInOutcome.Failure(
                "Google Sign-In is not configured. Add the GOOGLE_WEB_CLIENT_ID secret or a Firebase google-services.json."
            )
        }
        val credentialManager = CredentialManager.create(activityContext)

        // Single credential option per request for responsive Credential Manager bottom sheet
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            authenticateFirebaseWithCredential(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Interactive Google Sign-In cancelled or dismissed by Credential Manager", e)
            GoogleSignInOutcome.Cancelled("Sign-in was cancelled. Please select your Google account to continue.")
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credentials found on device", e)
            GoogleSignInOutcome.Failure("No Google account found on this device. Add a Google account in system settings and try again.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error during Google Sign-In", e)
            GoogleSignInOutcome.Failure(e.localizedMessage ?: "Google Sign-In failed.")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            GoogleSignInOutcome.Failure(e.localizedMessage ?: "Unexpected sign-in error.")
        }
    }

    suspend fun trySilentSignIn(activityContext: Context): GoogleSignInOutcome? {
        if (Firebase.auth.currentUser != null) {
            return GoogleSignInOutcome.Success(Firebase.auth.currentUser!!)
        }
        val serverClientId = resolveServerClientId(activityContext)
        if (serverClientId.isBlank()) {
            Log.w(TAG, "Silent Google Sign-In skipped: web client ID is not configured")
            return null
        }
        val credentialManager = CredentialManager.create(activityContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            authenticateFirebaseWithCredential(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled", e)
            null
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No previously authorized Google account for silent sign-in")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In skipped", e)
            null
        }
    }

    private suspend fun authenticateFirebaseWithCredential(
        credential: androidx.credentials.Credential
    ): GoogleSignInOutcome {
        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    val authResult = Firebase.auth.signInWithCredential(firebaseCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        GoogleSignInOutcome.Success(user)
                    } else {
                        GoogleSignInOutcome.Failure("Firebase sign-in returned no user.")
                    }
                } else {
                    GoogleSignInOutcome.Failure("Unsupported credential type returned.")
                }
            }
            else -> GoogleSignInOutcome.Failure("Unexpected credential response.")
        }
    }

    suspend fun signOut(context: Context) {
        try {
            Firebase.auth.signOut()
            val credentialManager = CredentialManager.create(context.applicationContext)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing credential state on sign-out", e)
        }
    }
}
