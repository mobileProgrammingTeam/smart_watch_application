package com.example.workersafetywatch.communication

import android.content.Context
import android.util.Log
import com.example.workersafetywatch.model.HealthMeasurement
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Google Wearable Data Layer API를 사용하여
 * Galaxy Watch → 스마트폰으로 데이터를 전송하는 클래스.
 *
 * [DataClient]를 사용하여 측정 데이터를 동기화한다.
 * 스마트폰이 일시적으로 연결되지 않은 경우에도
 * Data Layer가 재연결 시 자동으로 동기화해준다.
 */
class PhoneDataSender(context: Context) {

    companion object {
        private const val TAG = "PhoneDataSender"

        /** 생체신호 측정 데이터 경로 */
        const val PATH_HEALTH_MEASUREMENT = "/health/measurement"

        // DataMap 키
        private const val KEY_WORKER_ID = "worker_id"
        private const val KEY_HEART_RATE = "heart_rate"
        private const val KEY_SPO2 = "spo2"
        private const val KEY_MEASURED_AT = "measured_at"
        private const val KEY_HR_VALID = "heart_rate_valid"
        private const val KEY_SPO2_VALID = "spo2_valid"
        private const val KEY_TIMESTAMP = "timestamp"
    }

    private val dataClient: DataClient = Wearable.getDataClient(context)

    /**
     * 생체신호 측정 결과를 스마트폰으로 전송한다.
     *
     * @param measurement 전송할 [HealthMeasurement]
     * @return 전송 성공 여부
     */
    suspend fun sendMeasurement(measurement: HealthMeasurement): Boolean {
        return try {
            val putDataReq = PutDataMapRequest.create(PATH_HEALTH_MEASUREMENT).apply {
                dataMap.putString(KEY_WORKER_ID, measurement.workerId)
                dataMap.putInt(KEY_HEART_RATE, measurement.heartRate ?: -1)
                dataMap.putInt(KEY_SPO2, measurement.spo2 ?: -1)
                dataMap.putLong(KEY_MEASURED_AT, measurement.measuredAt)
                dataMap.putBoolean(KEY_HR_VALID, measurement.heartRateValid)
                dataMap.putBoolean(KEY_SPO2_VALID, measurement.spo2Valid)
                // 타임스탬프를 추가하여 DataClient가 매번 변경으로 인식하도록 한다
                dataMap.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(putDataReq).await()
            Log.i(TAG, "Measurement sent to phone: hr=${measurement.heartRate}, spo2=${measurement.spo2}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send measurement to phone", e)
            false
        }
    }
}
