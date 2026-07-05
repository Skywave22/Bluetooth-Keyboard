package com.bluepilot.remote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SECTION: Air Mouse / Motion controls — sensor source.
 *
 * Streams gyroscope angular velocity (rad/s, device axes) as a cold Flow.
 * TYPE_GYROSCOPE already benefits from Android's factory calibration +
 * sensor fusion; we add our own smoothing/drift handling downstream in
 * [AirMouseCore]. Collect → listener registered; cancel → unregistered
 * (no leaks, no battery drain when unused).
 *
 * NOTE: sensors only exist on real hardware — the UI shows a clear
 * "no gyroscope" state on emulators/devices without one.
 */
data class GyroSample(val x: Float, val y: Float, val z: Float, val timestampNs: Long)

@Singleton
class MotionSensorSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** True when this device has a gyroscope at all. */
    val hasGyroscope: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    /** Gyro stream at game rate (~50Hz). Empty flow when unsupported. */
    fun gyro(): Flow<GyroSample> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (manager == null || sensor == null) {
            Timber.w("gyroscope unavailable")
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(GyroSample(event.values[0], event.values[1], event.values[2], event.timestamp))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        if (!ok) { close(); return@callbackFlow }
        Timber.i("gyro listener registered")
        awaitClose {
            runCatching { manager.unregisterListener(listener) }
            Timber.i("gyro listener unregistered")
        }
    }
}
