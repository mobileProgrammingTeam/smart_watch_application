package com.example.workersafetywatch.measurement

import android.util.Log
import com.example.workersafetywatch.communication.PhoneDataSender
import com.example.workersafetywatch.model.HealthMeasurement
import com.example.workersafetywatch.repository.MeasurementRepository
import com.example.workersafetywatch.sensor.HeartRateSensor
import com.example.workersafetywatch.sensor.SpO2Sensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 일정 주기로 생체신호 측정을 수행하는 스케줄러.
 *
 * 설정된 간격(기본 60초)마다 다음 작업을 반복한다:
 * 1. HeartRateSensor에서 최근 심박수 확인
 * 2. SpO2Sensor로 SpO₂ 측정 요청
 * 3. 두 결과를 HealthMeasurement로 합산
 * 4. MeasurementRepository에 저장 (→ UI 갱신)
 * 5. PhoneDataSender로 스마트폰에 전송
 */
class MeasurementScheduler(
    private val heartRateSensor: HeartRateSensor,
    private val spo2Sensor: SpO2Sensor,
    private val measurementRepository: MeasurementRepository,
    private val phoneDataSender: PhoneDataSender,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "MeasurementScheduler"

        /** 기본 측정 주기 (밀리초) */
        const val DEFAULT_MEASUREMENT_INTERVAL = 60_000L
    }

    /** 현재 측정 주기 (밀리초) */
    var measurementInterval: Long = DEFAULT_MEASUREMENT_INTERVAL
        set(value) {
            field = value
            Log.d(TAG, "Measurement interval updated: ${value}ms")
            // 주기가 변경되면 스케줄러를 재시작
            if (isRunning) {
                stop()
                start(currentWorkerId)
            }
        }

    private var schedulerJob: Job? = null
    private var isRunning = false
    private var currentWorkerId = ""

    /**
     * 주기적 측정을 시작한다.
     *
     * @param workerId 현재 작업자 ID
     */
    fun start(workerId: String) {
        if (isRunning) {
            Log.d(TAG, "Scheduler already running")
            return
        }

        currentWorkerId = workerId
        isRunning = true
        Log.i(TAG, "Measurement scheduler started (interval: ${measurementInterval}ms)")

        schedulerJob = scope.launch {
            // 첫 측정은 즉시 수행
            performMeasurement()

            // 이후 주기적으로 반복
            while (isActive) {
                delay(measurementInterval)
                performMeasurement()
            }
        }
    }

    /**
     * 주기적 측정을 중지한다.
     */
    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
        isRunning = false
        Log.i(TAG, "Measurement scheduler stopped")
    }

    /**
     * 한 번의 측정 사이클을 수행한다.
     */
    private suspend fun performMeasurement() {
        Log.d(TAG, "Performing measurement cycle...")

        try {
            // 1. 최근 심박수 확인
            val (heartRate, hrValid) = heartRateSensor.getLatestReading()
            Log.d(TAG, "Latest Heart Rate: $heartRate (valid=$hrValid)")

            // 2. SpO₂ 측정 (25~30초 소요)
            val spo2Result = spo2Sensor.measure()
            Log.d(TAG, "SpO2 result: ${spo2Result.spo2} (valid=${spo2Result.isValid})")

            // 3. HealthMeasurement 생성
            val measurement = HealthMeasurement(
                workerId = currentWorkerId,
                heartRate = heartRate,
                spo2 = spo2Result.spo2,
                measuredAt = System.currentTimeMillis(),
                heartRateValid = hrValid,
                spo2Valid = spo2Result.isValid
            )
            Log.i(TAG, "Measurement created: $measurement")

            // 4. Repository에 저장 (→ UI에 반영)
            measurementRepository.updateMeasurement(measurement)

            // 5. 스마트폰으로 전송
            Log.d(TAG, "Sending measurement to phone...")
            val sent = phoneDataSender.sendMeasurement(measurement)
            if (sent) {
                Log.i(TAG, "Measurement sent to phone successfully")
            } else {
                Log.w(TAG, "Failed to send measurement to phone")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Measurement cycle failed", e)
        }
    }

    /**
     * 작업자 ID를 업데이트한다.
     */
    fun updateWorkerId(workerId: String) {
        currentWorkerId = workerId
        Log.d(TAG, "Worker ID updated: $workerId")
    }
}
