package com.example.workersafetywatch.sensor

import android.util.Log
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Samsung Health Sensor SDK를 사용한 SpO₂(산소포화도) 센서 관리 클래스.
 *
 * [HealthTrackerType.SPO2_ON_DEMAND]를 사용하여 요청 시 SpO₂를 측정한다.
 * SpO₂ 측정은 약 25~30초 정도 소요되므로 코루틴 suspend 함수로 구현한다.
 *
 * @param sensorManager [HealthSensorManager] 인스턴스
 */
class SpO2Sensor(private val sensorManager: HealthSensorManager) {

    companion object {
        private const val TAG = "SpO2Sensor"

        /** SpO₂ 측정 완료 상태 코드 */
        private const val SPO2_STATUS_MEASUREMENT_COMPLETED = 2

        /** SpO₂ 측정 타임아웃 (40초 — 측정에 ~30초 + 여유 10초) */
        private const val SPO2_TIMEOUT_MS = 40_000L
    }

    /**
     * SpO₂ 측정 결과.
     *
     * @property spo2 산소포화도 값 (%). 측정 실패 시 null
     * @property isValid 측정값의 유효 여부
     */
    data class SpO2Result(
        val spo2: Int?,
        val isValid: Boolean
    )

    /**
     * SpO₂ 측정을 수행하고 결과를 반환한다.
     *
     * 내부적으로 센서를 시작하고, 측정 완료(또는 타임아웃)까지 대기한 후
     * 결과를 반환하며 센서를 정리한다.
     *
     * @return [SpO2Result] 측정 결과
     */
    suspend fun measure(): SpO2Result {
        Log.i(TAG, "SpO2 measurement started")

        val tracker = sensorManager.getTracker(HealthTrackerType.SPO2_ON_DEMAND)
        if (tracker == null) {
            Log.e(TAG, "SpO2 sensor not available")
            return SpO2Result(spo2 = null, isValid = false)
        }

        // 타임아웃이 걸린 suspend로 SpO₂ 측정 완료를 대기
        val result = withTimeoutOrNull(SPO2_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                        for (dataPoint in dataPoints) {
                            try {
                                val status = dataPoint.getValue(ValueKey.SpO2Set.STATUS)
                                Log.d(TAG, "SpO2 status: $status")

                                if (status == SPO2_STATUS_MEASUREMENT_COMPLETED) {
                                    val spo2Value = dataPoint.getValue(ValueKey.SpO2Set.SPO2)
                                    Log.i(TAG, "SpO2: $spo2Value%")

                                    // 센서 정리 후 결과 반환
                                    try {
                                        tracker.unsetEventListener()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error unsetting listener", e)
                                    }

                                    if (continuation.isActive) {
                                        continuation.resume(
                                            SpO2Result(
                                                spo2 = spo2Value,
                                                isValid = spo2Value in 70..100
                                            )
                                        )
                                    }
                                    return
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing SpO2 data", e)
                            }
                        }
                    }

                    override fun onFlushCompleted() {
                        Log.d(TAG, "SpO2 flush completed")
                    }

                    override fun onError(error: HealthTracker.TrackerError) {
                        Log.e(TAG, "SpO2 tracker error: ${error.name}")
                        try {
                            tracker.unsetEventListener()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unsetting listener on error", e)
                        }

                        if (continuation.isActive) {
                            continuation.resume(SpO2Result(spo2 = null, isValid = false))
                        }
                    }
                }

                // 취소 시 센서 정리
                continuation.invokeOnCancellation {
                    Log.d(TAG, "SpO2 measurement cancelled")
                    try {
                        tracker.unsetEventListener()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unsetting listener on cancellation", e)
                    }
                }

                try {
                    tracker.setEventListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start SpO2 measurement", e)
                    if (continuation.isActive) {
                        continuation.resume(SpO2Result(spo2 = null, isValid = false))
                    }
                }
            }
        }

        if (result == null) {
            Log.w(TAG, "SpO2 measurement timed out")
            try {
                tracker.unsetEventListener()
            } catch (e: Exception) {
                Log.e(TAG, "Error unsetting listener on timeout", e)
            }
            return SpO2Result(spo2 = null, isValid = false)
        }

        return result
    }
}
