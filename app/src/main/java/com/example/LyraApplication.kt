package com.example

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.domain.model.CallStateEnum
import com.example.service.CallStateManager
import com.example.service.LyraCallService
import com.example.service.NotificationHelper
import com.example.utils.ensureFirebaseInitialized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Collections

class LyraApplication : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(24L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .allowRgb565(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        CallStateManager.bindApplicationContext(this)
        // Ensure FirebaseApp is initialized safely (App Check is configured in MainActivity/CallActivity with Intent)
        ensureFirebaseInitialized(this)
        NotificationHelper.createNotificationChannels(this)
        appScope.launch(Dispatchers.IO) {
            com.example.data.local.preferences.LyraPreferences(this@LyraApplication).warmUpCache()
            runCatching {
                com.example.service.ScheduledCallScheduler.rescheduleAllPending(this@LyraApplication)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                startedActivityIds.add(System.identityHashCode(activity))
                syncForegroundState()
            }

            override fun onActivityResumed(activity: Activity) {
                resumedActivityIds.add(System.identityHashCode(activity))
                syncForegroundState()
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivityIds.remove(System.identityHashCode(activity))
                syncForegroundState()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityIds.remove(System.identityHashCode(activity))
                syncForegroundState()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                startedActivityIds.remove(System.identityHashCode(activity))
                resumedActivityIds.remove(System.identityHashCode(activity))
                syncForegroundState()
            }
        })

        // Ensure persistent call foreground service runs for ringing, dialing, active, and voicemail calls
        appScope.launch {
            CallStateManager.callState
                .map { state ->
                    state == CallStateEnum.RINGING ||
                        state == CallStateEnum.DIALING ||
                        state == CallStateEnum.ACTIVE ||
                        state == CallStateEnum.VOICEMAIL
                }
                .distinctUntilChanged()
                .collect { isCallLive ->
                    if (isCallLive) {
                        val serviceIntent = Intent(this@LyraApplication, LyraCallService::class.java)
                        runCatching {
                            ContextCompat.startForegroundService(this@LyraApplication, serviceIntent)
                        }
                    }
                }
        }
    }

    companion object {
        private val startedActivityIds: MutableSet<Int> =
            Collections.synchronizedSet(mutableSetOf())
        private val resumedActivityIds: MutableSet<Int> =
            Collections.synchronizedSet(mutableSetOf())

        private val _isAppInForegroundFlow = MutableStateFlow(false)
        val isAppInForegroundFlow: StateFlow<Boolean> = _isAppInForegroundFlow.asStateFlow()

        private fun syncForegroundState() {
            _isAppInForegroundFlow.value = resumedActivityIds.isNotEmpty() || startedActivityIds.isNotEmpty()
        }

        val isAppInForeground: Boolean
            get() = resumedActivityIds.isNotEmpty() || startedActivityIds.isNotEmpty()
    }
}
