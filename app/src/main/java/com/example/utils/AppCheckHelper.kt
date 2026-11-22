package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and runtime harness.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var appCheckFactoryInstalled = false

/**
 * Ensures the default FirebaseApp is initialized safely across MainActivity, CallActivity,
 * background services, and Direct Boot contexts without throwing if the device is not yet unlocked.
 */
fun ensureFirebaseInitialized(context: Context): Boolean {
    val appCtx = context.applicationContext ?: context
    val userManager = appCtx.getSystemService(Context.USER_SERVICE) as? UserManager
    if (userManager != null && !userManager.isUserUnlocked) {
        Log.w(TAG, "Device is in Direct Boot locked state; deferring Firebase initialization.")
        return false
    }
    return try {
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            true
        } else {
            FirebaseApp.initializeApp(appCtx) != null || FirebaseApp.getApps(appCtx).isNotEmpty()
        }
    } catch (e: Exception) {
        Log.e(TAG, "FirebaseApp.initializeApp failed", e)
        false
    }
}

fun isAppCheckConfigured(): Boolean = appCheckFactoryInstalled

fun configureAppCheck(context: Context, intent: Intent?) {
    val appCtx = context.applicationContext ?: context
    try {
        // 1. Ensure FirebaseApp is initialized safely before touching App Check
        if (!ensureFirebaseInitialized(appCtx)) {
            return
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from Intent extra
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = appCtx.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() is required before DebugAppCheckProviderFactory reads the secret.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else if (appCheckFactoryInstalled) {
                return
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            appCheckFactoryInstalled = true
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            if (appCheckFactoryInstalled) return
            // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
            val resId = appCtx.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", appCtx.packageName)
            if (resId != 0) {
                val siteKey = appCtx.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    appCheckFactoryInstalled = true
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

