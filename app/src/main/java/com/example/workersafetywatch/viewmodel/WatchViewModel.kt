package com.example.workersafetywatch.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.workersafetywatch.measurement.ServiceLocator
import com.example.workersafetywatch.model.HealthMeasurement
import com.example.workersafetywatch.model.WorkerInfo
import com.example.workersafetywatch.repository.WorkerRepository
import com.example.workersafetywatch.sensor.HealthSensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 워치 메인 화면의 ViewModel.
 *
 * UI에 표시할 데이터를 StateFlow로 노출하며,
 * 센서나 Repository에 직접 접근하지 않고 ServiceLocator를 통해 데이터를 관찰한다.
 *
 * 구조:
 * ```
 * MainActivity → WatchViewModel → Repository / ServiceLocator
 * ```
 */
class WatchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WatchViewModel"
    }

    private val workerRepository = WorkerRepository(application)

    /** 작업자 정보 */
    val workerInfo: StateFlow<WorkerInfo> = workerRepository.workerInfo
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WorkerInfo.DEFAULT
        )

    /** 최근 측정 결과 */
    val latestMeasurement: StateFlow<HealthMeasurement?> =
        ServiceLocator.measurementRepository.latestMeasurement

    /** 센서 연결 상태 */
    private val _sensorConnectionState = MutableStateFlow(
        HealthSensorManager.ConnectionState.DISCONNECTED
    )
    val sensorConnectionState: StateFlow<HealthSensorManager.ConnectionState> =
        _sensorConnectionState.asStateFlow()

    /** 권한 허용 여부 */
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    init {
        // 센서 연결 상태 관찰
        viewModelScope.launch {
            observeSensorState()
        }
    }

    /**
     * 센서 매니저의 연결 상태를 관찰하여 UI에 반영한다.
     */
    private suspend fun observeSensorState() {
        // ServiceLocator에서 센서 매니저가 설정될 때까지 주기적 체크
        while (true) {
            val manager = ServiceLocator.healthSensorManager
            if (manager != null) {
                manager.connectionState.collect { state ->
                    _sensorConnectionState.value = state
                    Log.d(TAG, "Sensor state updated: $state")
                }
                break
            }
            kotlinx.coroutines.delay(500)
        }
    }

    /**
     * 권한 결과를 설정한다.
     */
    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        Log.d(TAG, "Permission granted: $granted")
    }
}
