package com.example.workersafetywatch.measurement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.workersafetywatch.R
import com.example.workersafetywatch.communication.PhoneDataSender
import com.example.workersafetywatch.repository.MeasurementRepository
import com.example.workersafetywatch.repository.WorkerRepository
import com.example.workersafetywatch.sensor.HealthSensorManager
import com.example.workersafetywatch.sensor.HeartRateSensor
import com.example.workersafetywatch.sensor.SpO2Sensor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 생체신호 측정을 위한 Foreground Service.
 *
 * 워치 화면이 꺼져 있어도 생체신호 측정을 지속하기 위해
 * Health 타입 Foreground Service로 실행한다.
 *
 * 이 서비스는:
 * 1. Samsung Health Sensor SDK에 연결
 * 2. HeartRate 연속 측정 시작
 * 3. MeasurementScheduler를 시작하여 주기적 측정 수행
 */
class MeasurementService : LifecycleService() {

    companion object {
        private const val TAG = "MeasurementService"
        private const val NOTIFICATION_CHANNEL_ID = "measurement_channel"
        private const val NOTIFICATION_ID = 1001
    }

    // 싱글톤 패턴 — ViewModel이 직접 접근할 수 있도록 한다
    // (학부 프로젝트 수준에서 DI 프레임워크 없이 간단히 공유)
    private lateinit var sensorManager: HealthSensorManager
    private lateinit var heartRateSensor: HeartRateSensor
    private lateinit var spo2Sensor: SpO2Sensor
    private lateinit var phoneDataSender: PhoneDataSender
    private lateinit var workerRepository: WorkerRepository
    private lateinit var scheduler: MeasurementScheduler

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MeasurementService created")

        // 컴포넌트 초기화
        sensorManager = HealthSensorManager(this)
        heartRateSensor = HeartRateSensor(sensorManager)
        spo2Sensor = SpO2Sensor(sensorManager)
        phoneDataSender = PhoneDataSender(this)
        workerRepository = WorkerRepository(this)

        // MeasurementRepository는 싱글톤으로 공유
        val measurementRepo = ServiceLocator.measurementRepository

        scheduler = MeasurementScheduler(
            heartRateSensor = heartRateSensor,
            spo2Sensor = spo2Sensor,
            measurementRepository = measurementRepo,
            phoneDataSender = phoneDataSender,
            scope = lifecycleScope
        )

        // 센서 상태를 ServiceLocator를 통해 공유
        ServiceLocator.healthSensorManager = sensorManager
        ServiceLocator.heartRateSensor = heartRateSensor
        ServiceLocator.scheduler = scheduler
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "MeasurementService onStartCommand")

        // Foreground Service 시작
        startForeground(NOTIFICATION_ID, createNotification())

        // 센서 연결 및 측정 시작
        connectAndStartMeasurement()

        return START_STICKY
    }

    /**
     * Samsung Health Sensor SDK에 연결하고 측정을 시작한다.
     */
    private fun connectAndStartMeasurement() {
        lifecycleScope.launch {
            // SDK 연결 상태를 관찰
            launch {
                sensorManager.connectionState.collect { state ->
                    Log.d(TAG, "Sensor connection state: $state")

                    when (state) {
                        HealthSensorManager.ConnectionState.CONNECTED -> {
                            onSensorConnected()
                        }
                        HealthSensorManager.ConnectionState.ERROR -> {
                            Log.e(TAG, "Sensor connection error: ${sensorManager.connectionError}")
                        }
                        else -> { /* DISCONNECTED, CONNECTING — 대기 */ }
                    }
                }
            }

            // SDK 연결 시작
            sensorManager.connect()
        }
    }

    /**
     * 센서 연결 성공 후 측정을 시작한다.
     */
    private fun onSensorConnected() {
        Log.i(TAG, "Sensor connected, starting measurements")

        // 지원 센서 확인
        val availableTypes = sensorManager.getAvailableTrackerTypes()
        Log.d(TAG, "Available sensor types: $availableTypes")

        // Heart Rate 연속 측정 시작
        val hrStarted = heartRateSensor.startTracking()
        Log.d(TAG, "Heart Rate tracking started: $hrStarted")

        // 작업자 정보를 가져와서 스케줄러 시작
        lifecycleScope.launch {
            val workerInfo = workerRepository.workerInfo.first()
            scheduler.start(workerInfo.workerId)
        }
    }

    /**
     * Foreground Service 알림을 생성한다.
     */
    private fun createNotification(): Notification {
        val channelId = NOTIFICATION_CHANNEL_ID

        // Notification Channel 생성 (API 26+)
        val channel = NotificationChannel(
            channelId,
            getString(R.string.measurement_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.measurement_channel_description)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("생체신호 측정 중...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "MeasurementService destroyed")
        scheduler.stop()
        heartRateSensor.stopTracking()
        sensorManager.disconnect()
        ServiceLocator.healthSensorManager = null
        ServiceLocator.heartRateSensor = null
        ServiceLocator.scheduler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

/**
 * 간단한 서비스 로케이터.
 *
 * MeasurementService와 ViewModel 사이에서 싱글톤 인스턴스를 공유한다.
 * 학부 프로젝트 수준에서 DI 프레임워크(Hilt 등) 없이 간단하게 사용한다.
 */
object ServiceLocator {
    val measurementRepository = MeasurementRepository()
    var healthSensorManager: HealthSensorManager? = null
    var heartRateSensor: HeartRateSensor? = null
    var scheduler: MeasurementScheduler? = null
}
