package com.example.onerow.report

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Listens to the accelerometer (no permission needed) while the keyboard is on screen; stopped the moment it is hidden. */
class ShakeListener(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val detector = ShakeDetector()
    private var on = false

    fun start() {
        if (on || sm == null || sensor == null) return
        on = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!on) return
        sm?.unregisterListener(this); on = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (detector.onSample(e.timestamp / 1_000_000, e.values[0], e.values[1], e.values[2])) onShake()
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
}
