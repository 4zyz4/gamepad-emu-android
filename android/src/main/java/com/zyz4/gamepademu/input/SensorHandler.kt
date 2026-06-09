package com.zyz4.gamepademu.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SensorData(
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
)

class SensorHandler(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val displayRotation: Int
        get() {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }

    // ── Initial auto-calibration ──
    private var initCalSumX = 0f
    private var initCalSumY = 0f
    private var initCalSumZ = 0f
    private var initCalSamples = 0
    private var initCalDone = false
    var onCalibrationComplete: ((biasX: Float, biasY: Float, biasZ: Float) -> Unit)? = null

    // ── PC-triggered recalibration buffer ──
    private var pcCalSamples = 0
    private var pcCalTarget = 0
    private var pcCalSumX = 0f
    private var pcCalSumY = 0f
    private var pcCalSumZ = 0f
    private var pcCalCallback: ((Float, Float, Float) -> Unit)? = null

    private var _gyroX = 0f
    private var _gyroY = 0f
    private var _gyroZ = 0f
    private var _accelX = 0f
    private var _accelY = 0f
    private var _accelZ = 0f

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    fun start() {
        val rate = SensorManager.SENSOR_DELAY_GAME
        gyroscope?.let { sensorManager.registerListener(this, it, rate) }
        accelerometer?.let { sensorManager.registerListener(this, it, rate) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun requestPcCalibration(
        sampleCount: Int = 60,
        onComplete: (biasX: Float, biasY: Float, biasZ: Float) -> Unit,
    ) {
        pcCalSamples = 0
        pcCalTarget = sampleCount.coerceIn(10, 300)
        pcCalSumX = 0f
        pcCalSumY = 0f
        pcCalSumZ = 0f
        pcCalCallback = onComplete
    }

    private fun remapToDisplayRotation(
        rawX: Float, rawY: Float, rawZ: Float,
    ): Triple<Float, Float, Float> {
        // gyroX→俯仰(短轴), gyroY→偏航(法线), gyroZ→横滚(长轴)
        return Triple(rawY, rawZ, rawX)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val (rx, ry, rz) = remapToDisplayRotation(
                    event.values[0], event.values[1], event.values[2]
                )

                // Initial auto-calibration (first 60 raw samples)
                if (!initCalDone) {
                    initCalSumX += rx
                    initCalSumY += ry
                    initCalSumZ += rz
                    initCalSamples++
                    if (initCalSamples >= 60) {
                        initCalDone = true
                        val bx = initCalSumX / 60f
                        val by = initCalSumY / 60f
                        val bz = initCalSumZ / 60f
                        onCalibrationComplete?.invoke(bx, by, bz)
                    }
                }

                // PC-triggered recalibration buffer
                if (pcCalCallback != null && pcCalSamples < pcCalTarget) {
                    pcCalSumX += rx
                    pcCalSumY += ry
                    pcCalSumZ += rz
                    pcCalSamples++
                    if (pcCalSamples >= pcCalTarget) {
                        val bx = pcCalSumX / pcCalSamples
                        val by = pcCalSumY / pcCalSamples
                        val bz = pcCalSumZ / pcCalSamples
                        pcCalCallback?.invoke(bx, by, bz)
                        pcCalCallback = null
                    }
                }

                _gyroX = rx
                _gyroY = ry
                _gyroZ = rz
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val (ax, ay, az) = remapToDisplayRotation(
                    event.values[0], event.values[1], event.values[2]
                )
                _accelX = ax
                _accelY = ay
                _accelZ = az
            }
        }
        _sensorData.value = SensorData(_gyroX, _gyroY, _gyroZ, _accelX, _accelY, _accelZ)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
