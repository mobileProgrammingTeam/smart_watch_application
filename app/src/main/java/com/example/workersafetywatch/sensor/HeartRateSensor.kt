package com.example.workersafetywatch.sensor

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Samsung Health Sensor SDK를 사용한 심박수 센서 관리 클래스.
 *
 * [HealthTrackerType.HEART_RATE_CONTINUOUS]를 사용하여
 * 지속적으로 심박수를 측정하고, 가장 최근의 유효한 측정값을 유지한다.
 *
 * @param sensorManager [HealthSensorManager] 인스턴스
 */
class HeartRateSensor(private val sensorManager: HealthSensorManager) {

    companion object {
        private const val TAG = "HeartRateSensor"

        /** 심박수 상태값: 유효한 측정 */
        private const val HR_STATUS_VALID = 1
    }

    private var tracker: HealthTracker? = null
    private var isTracking = false

    /** 가장 최근에 측정된 심박수 (BPM). 미측정 시 null */
    private val _latestHeartRate = MutableStateFlow<Int?>(null)
    val latestHeartRate: StateFlow<Int?> = _latestHeartRate.asStateFlow()

    /** 심박수 측정값의 유효 여부 */
    private val _isValid = MutableStateFlow(false)
    val isValid: StateFlow<Boolean> = _isValid.asStateFlow()

    /** 센서 이벤트 리스너 */
    private val eventListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (dataPoint in dataPoints) {
                try {
                    val status = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                    val heartRate = dataPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)

                    if (status == HR_STATUS_VALID && heartRate > 0) {
                        _latestHeartRate.value = heartRate
                        _isValid.value = true
                        Log.d(TAG, "Heart Rate: $heartRate BPM (valid)")
                    } else {
                        Log.d(TAG, "Heart Rate data received but invalid: hr=$heartRate, status=$status")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing heart rate data", e)
                }
            }
        }

        override fun onFlushCompleted() {
            Log.d(TAG, "Heart rate flush completed")
        }

        override fun onError(error: HealthTracker.TrackerError) {
            Log.e(TAG, "Heart rate tracker error: ${error.name}")
            _isValid.value = false
        }
    }

    /**
     * 심박수 연속 측정을 시작한다.
     *
     * @return 시작 성공 여부
     */
    fun startTracking(): Boolean {
        if (isTracking) {
            Log.d(TAG, "Already tracking heart rate")
            return true
        }

        tracker = sensorManager.getTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
        if (tracker == null) {
            Log.e(TAG, "Heart Rate sensor not available")
            return false
        }

        return try {
            tracker?.setEventListener(eventListener)
            isTracking = true
            Log.i(TAG, "Heart Rate tracking started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start heart rate tracking", e)
            false
        }
    }

    /**
     * 심박수 측정을 중지한다.
     */
    fun stopTracking() {
        try {
            tracker?.unsetEventListener()
            Log.d(TAG, "Heart Rate tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping heart rate tracking", e)
        } finally {
            isTracking = false
        }
    }

    /**
     * 현재 저장된 최근 심박수 값을 반환하고 유효 여부를 함께 반환한다.
     */
    fun getLatestReading(): Pair<Int?, Boolean> {
        return Pair(_latestHeartRate.value, _isValid.value)
    }
}
