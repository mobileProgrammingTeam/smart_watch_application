package com.example.workersafetywatch.communication

import android.util.Log
import com.example.workersafetywatch.model.WorkerInfo
import com.example.workersafetywatch.repository.WorkerRepository
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 스마트폰 → Galaxy Watch 방향의 Data Layer 이벤트를 수신하는 서비스.
 *
 * 스마트폰에서 전달되는 작업자 정보 및 설정 변경을 처리한다.
 * AndroidManifest에 등록되어 앱이 실행 중이지 않아도 데이터를 수신할 수 있다.
 */
class WearableDataListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableDataListener"

        /** 작업자 정보 경로 */
        const val PATH_WORKER_INFO = "/worker/info"

        /** 측정 주기 설정 경로 */
        const val PATH_MEASUREMENT_INTERVAL = "/config/measurement_interval"

        // WorkerInfo DataMap 키
        private const val KEY_WORKER_ID = "worker_id"
        private const val KEY_WORKER_NAME = "worker_name"
        private const val KEY_DEPARTMENT = "department"

        // Config DataMap 키
        private const val KEY_INTERVAL_MS = "interval_ms"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var workerRepository: WorkerRepository

    override fun onCreate() {
        super.onCreate()
        workerRepository = WorkerRepository(applicationContext)
        Log.d(TAG, "WearableDataListenerService created")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "Data changed event received: ${dataEvents.count} events")

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                val path = dataItem.uri.path ?: continue

                when {
                    path.startsWith(PATH_WORKER_INFO) -> {
                        handleWorkerInfoUpdate(dataItem)
                    }
                    path.startsWith(PATH_MEASUREMENT_INTERVAL) -> {
                        handleIntervalUpdate(dataItem)
                    }
                    else -> {
                        Log.d(TAG, "Unknown data path: $path")
                    }
                }
            }
        }
    }

    /**
     * 스마트폰에서 전달된 작업자 정보를 처리한다.
     */
    private fun handleWorkerInfoUpdate(dataItem: com.google.android.gms.wearable.DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val workerInfo = WorkerInfo(
                workerId = dataMap.getString(KEY_WORKER_ID, ""),
                workerName = dataMap.getString(KEY_WORKER_NAME, ""),
                department = dataMap.getString(KEY_DEPARTMENT)
            )

            Log.i(TAG, "Worker info received: $workerInfo")

            serviceScope.launch {
                workerRepository.saveWorkerInfo(workerInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process worker info update", e)
        }
    }

    /**
     * 스마트폰에서 전달된 측정 주기 변경을 처리한다.
     */
    private fun handleIntervalUpdate(dataItem: com.google.android.gms.wearable.DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val intervalMs = dataMap.getLong(KEY_INTERVAL_MS)
            Log.i(TAG, "Measurement interval updated: ${intervalMs}ms")
            // TODO: MeasurementScheduler에 전달하여 주기 변경
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process interval update", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "WearableDataListenerService destroyed")
    }
}
