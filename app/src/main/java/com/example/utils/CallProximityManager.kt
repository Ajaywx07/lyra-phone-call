package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages hardware proximity detection during active or dialing phone calls.
 * When a call is ongoing and speakerphone is off, acquires PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK
 * and monitors Sensor.TYPE_PROXIMITY so holding the phone to the ear turns off the screen
 * and prevents accidental touches.
 */
object CallProximityManager : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isSensorRegistered = false

    private val _isNearEar = MutableStateFlow(false)
    val isNearEar: StateFlow<Boolean> = _isNearEar.asStateFlow()

    private val _isProximityLockHeld = MutableStateFlow(false)
    val isProximityLockHeld: StateFlow<Boolean> = _isProximityLockHeld.asStateFlow()

    @Synchronized
    @SuppressLint("WakelockTimeout")
    fun updateCallAudioState(
        context: Context,
        isCallOngoing: Boolean,
        isSpeakerOn: Boolean
    ) {
        val shouldEnableProximity = isCallOngoing && !isSpeakerOn
        if (!shouldEnableProximity) {
            releaseInternal(waitForNoProximity = true)
            return
        }

        val appContext = context.applicationContext
        if (sensorManager == null) {
            sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        }

        if (!isSensorRegistered) {
            proximitySensor?.let { sensor ->
                runCatching {
                    sensorManager?.registerListener(
                        this,
                        sensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                    isSensorRegistered = true
                }
            }
        }

        if (wakeLock == null) {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null &&
                powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
            ) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Lyra:CallProximityWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
        }

        val lock = wakeLock
        if (lock != null && !lock.isHeld) {
            runCatching {
                lock.acquire()
                _isProximityLockHeld.value = true
            }
        }
    }

    @Synchronized
    fun release() {
        releaseInternal(waitForNoProximity = false)
    }

    private fun releaseInternal(waitForNoProximity: Boolean) {
        if (isSensorRegistered) {
            runCatching { sensorManager?.unregisterListener(this) }
            isSensorRegistered = false
        }
        _isNearEar.value = false

        val lock = wakeLock
        if (lock != null && lock.isHeld) {
            runCatching {
                if (waitForNoProximity) {
                    lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                } else {
                    lock.release()
                }
            }
        }
        _isProximityLockHeld.value = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        if (sensorEvent.sensor?.type != Sensor.TYPE_PROXIMITY) return
        val distance = sensorEvent.values.firstOrNull() ?: return
        val maxRange = proximitySensor?.maximumRange?.takeIf { it > 0f } ?: 5.0f
        val threshold = minOf(maxRange, 5.0f)
        _isNearEar.value = distance < threshold
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
