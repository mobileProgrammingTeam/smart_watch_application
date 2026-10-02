package com.example.workersafetywatch.sensor

import android.content.Context
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Samsung Health Sensor SDK의 HealthTrackingService 연결을 관리한다.
 *
 * 이 클래스는 SDK 초기화, 연결 상태 관리, 개별 HealthTracker 생성을 담당한다.
 * 센서별 측정 로직은 [HeartRateSensor], [SpO2Sensor]에서 처리한다.
 */
class HealthSensorManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthSensorManager"
    }

    /** SDK 연결 상태 */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private var healthTrackingService: HealthTrackingService? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var _connectionError: String? = null
    val connectionError: String? get() = _connectionError

    /**
     * Samsung Health Tracking Service에 연결한다.
     *
     * 연결 결과는 [connectionState]를 통해 관찰할 수 있다.
     */
    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING
        ) {
            Log.d(TAG, "Already connected or connecting, skipping")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Connecting to Health Tracking Service...")

        try {
            val connectionListener = object : ConnectionListener {
                override fun onConnectionSuccess() {
                    Log.i(TAG, "Health Sensor Service connected")
                    _connectionState.value = ConnectionState.CONNECTED
                    _connectionError = null
                }

                override fun onConnectionEnded() {
                    Log.w(TAG, "Health Sensor Service connection ended")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }

                override fun onConnectionFailed(error: HealthConnectionException) {
                    val errorMessage = when (error.errorCode) {
                        HealthConnectionException.PLATFORM_NOT_INSTALLED ->
                            "Health Platform이 설치되어 있지 않습니다"
                        HealthConnectionException.OLD_PLATFORM_VERSION ->
                            "Health Platform 버전을 업데이트해야 합니다"
                        HealthConnectionException.PACKAGE_NOT_INSTALLED ->
                            "Health Sensor Service가 설치되어 있지 않습니다"
                        HealthConnectionException.BIND_FAILED ->
                            "Health Sensor Service 바인딩에 실패했습니다"
                        else ->
                            "연결 실패: ${error.message}"
                    }
                    Log.e(TAG, "Connection failed: $errorMessage (code=${error.errorCode})")
                    _connectionError = errorMessage
                    _connectionState.value = ConnectionState.ERROR
                }
            }

            healthTrackingService = HealthTrackingService(connectionListener, context)
            healthTrackingService?.connectService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HealthTrackingService", e)
            _connectionError = "SDK 초기화 실패: ${e.message}"
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * 지정한 [HealthTrackerType]의 HealthTracker를 생성한다.
     *
     * @return HealthTracker 또는 해당 센서가 지원되지 않는 경우 null
     */
    fun getTracker(type: HealthTrackerType): HealthTracker? {
        val service = healthTrackingService
        if (service == null) {
            Log.e(TAG, "Cannot get tracker: service is not connected")
            return null
        }

        return try {
            service.getHealthTracker(type).also {
                Log.d(TAG, "Tracker created for type: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tracker for type $type", e)
            null
        }
    }

    /**
     * 현재 기기에서 지원하는 센서 타입 목록을 확인한다.
     */
    fun getAvailableTrackerTypes(): List<HealthTrackerType> {
        val service = healthTrackingService ?: return emptyList()
        return try {
            val types = service.trackingCapability.supportHealthTrackerTypes
            Log.d(TAG, "Available tracker types: $types")
            types
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available tracker types", e)
            emptyList()
        }
    }

    /**
     * 서비스 연결을 해제한다.
     */
    fun disconnect() {
        try {
            healthTrackingService?.disconnectService()
            Log.d(TAG, "Health Tracking Service disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting service", e)
        } finally {
            healthTrackingService = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
}

/**
 * Samsung SDK ConnectionListener.onConnectionFailed에서 전달되는 예외 타입.
 * SDK의 실제 예외 타입 이름에 맞춘 type alias 용도로, SDK 내부의
 * HealthTrackerException 또는 연결 관련 예외를 참조한다.
 *
 * 실제 SDK에서는 `HealthTrackingService.ConnectionListener.onConnectionFailed`
 * 파라미터로 `HealthTrackerException`이 전달된다.
 */
private typealias HealthConnectionException = com.samsung.android.service.health.tracking.HealthTrackerException
