package com.example.workersafety.watch

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

/** Prototype: continuous sensor reading; every 5/10 minutes publish one summary to Data Layer. */
class SensorService : Service() {
    companion object {
        const val PREFS = "watch_state"
        const val ACTION_START = "workersafety.START"
        const val ACTION_SEND_NOW = "workersafety.SEND_NOW"
        const val EXTRA_MINUTES = "interval_minutes"
        private const val CHANNEL = "worker_safety_sensing"
        private const val NOTIFICATION_ID = 730
        private const val PATH = "/workersafety/summary"
        private const val TAG = "WorkerSafetySensor"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var service: HealthTrackingService? = null
    private var heartTracker: HealthTracker? = null
    private var skinTracker: HealthTracker? = null
    private var intervalMinutes = 5
    private var running = false
    private var window = MeasurementWindow(System.currentTimeMillis())

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            try {
                val tracking = service ?: return
                val available = tracking.trackingCapability.supportHealthTrackerTypes
                if (HealthTrackerType.HEART_RATE_CONTINUOUS in available) {
                    heartTracker = tracking.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                    heartTracker?.setEventListener(heartListener)
                } else updateStatus("심박수 추적 미지원")
                if (HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS in available) {
                    skinTracker = tracking.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
                    skinTracker?.setEventListener(skinListener)
                } else updateStatus("피부온도 미지원: 기기/서비스 버전 확인")
                if (heartTracker != null && skinTracker != null) updateStatus("센서 연결됨 / ${intervalMinutes}분 주기")
            } catch (ex: Exception) {
                Log.e(TAG, "Unable to begin trackers", ex)
                updateStatus("센서 시작 실패: ${ex.javaClass.simpleName}")
            }
        }

        override fun onConnectionEnded() { updateStatus("Health Sensor Service 연결 종료") }
        override fun onConnectionFailed(error: HealthTrackerException) {
            Log.e(TAG, "Health Sensor Service error: ${error.errorCode}", error)
            updateStatus("Health Sensor 연결 오류: ${error.errorCode}")
            // Updating Health Sensor Service / enabling Samsung developer mode may be required.
            // Do not resolve() from a background service; direct the user to the watch settings.
        }
    }

    private val heartListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (point in dataPoints) {
                try {
                    val status = point.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS)
                    val bpm = point.getValue(ValueKey.HeartRateSet.HEART_RATE)
                    if (status == 1 && bpm != null) {
                        window.addHeartRate(bpm, point.timestamp)
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("live_hr", bpm).apply()
                    }
                } catch (ex: Exception) { Log.w(TAG, "Bad HR sample", ex) }
            }
        }
        override fun onError(error: HealthTracker.TrackerError) { updateStatus("심박 센서 오류: $error") }
        override fun onFlushCompleted() = Unit
    }

    private val skinListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
            for (point in dataPoints) {
                try {
                    val status = point.getValue(ValueKey.SkinTemperatureSet.STATUS)
                    val skin = point.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                    val ambient = point.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)
                    if (status == 0 && skin != null) {
                        window.addSkinTemperature(skin.toDouble(), ambient?.toDouble(), point.timestamp)
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putFloat("live_skin", skin).apply()
                    }
                } catch (ex: Exception) { Log.w(TAG, "Bad skin temp sample", ex) }
            }
        }
        override fun onError(error: HealthTracker.TrackerError) { updateStatus("피부온도 센서 오류: $error") }
        override fun onFlushCompleted() = Unit
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            sendSummary()
            handler.postDelayed(this, intervalMinutes * 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND_NOW -> if (running) sendSummary() else updateStatus("먼저 측정을 시작하세요")
            ACTION_START -> {
                if (running) return START_NOT_STICKY
                if (checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                    updateStatus("센서 접근 권한 없음")
                    stopSelf()
                    return START_NOT_STICKY
                }
                intervalMinutes = if (intent.getIntExtra(EXTRA_MINUTES, 5) == 10) 10 else 5
                createNotificationChannel()
                val notification = Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("작업자 안전 센싱 중")
                    .setContentText("심박수·피부온도 수집 / ${intervalMinutes}분 단위 전송")
                    .setOngoing(true)
                    .build()
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                    } else startForeground(NOTIFICATION_ID, notification)
                } catch (ex: Exception) {
                    Log.e(TAG, "FGS start failed", ex)
                    updateStatus("포그라운드 서비스 시작 실패")
                    stopSelf()
                    return START_NOT_STICKY
                }
                running = true
                window = MeasurementWindow(System.currentTimeMillis())
                updateStatus("Health Sensor 연결 중...")
                service = HealthTrackingService(connectionListener, applicationContext)
                service?.connectService()
                handler.postDelayed(ticker, intervalMinutes * 60_000L)
            }
        }
        return START_NOT_STICKY
    }

    private fun sendSummary() {
        val summary = window.closeWindow(System.currentTimeMillis(), intervalMinutes)
        val req = PutDataMapRequest.create(PATH)
        val map = req.dataMap
        map.putLong("window_start", summary.windowStartMs)
        map.putLong("timestamp", summary.windowEndMs)
        map.putInt("interval_minutes", summary.intervalMinutes)
        map.putBoolean("has_hr", summary.averageHeartRate != null)
        summary.averageHeartRate?.let { map.putDouble("hr_avg", it) }
        map.putInt("hr_count", summary.heartRateCount)
        map.putBoolean("has_skin", summary.wristSkinTemperatureC != null)
        summary.wristSkinTemperatureC?.let { map.putDouble("skin_c", it) }
        map.putBoolean("has_ambient", summary.ambientTemperatureC != null)
        summary.ambientTemperatureC?.let { map.putDouble("ambient_c", it) }
        map.putInt("skin_count", summary.temperatureCount)
        // timestamp changes every summary, ensuring each summary updates the same item.
        val request = req.asPutDataRequest().setUrgent()
        try {
            Wearable.getDataClient(this).putDataItem(request)
                .addOnSuccessListener { updateStatus("전송 요청 성공: ${summary.heartRateCount} HR / ${summary.temperatureCount} 피부온도") }
                .addOnFailureListener {
                    Log.e(TAG, "Data Layer write failed", it)
                    updateStatus("전송 요청 실패: 연결 확인")
                }
        } catch (ex: Exception) {
            Log.e(TAG, "Google Play services unavailable", ex)
            updateStatus("Data Layer 사용 불가")
        }
    }

    private fun updateStatus(value: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("status", value).apply()
        Log.i(TAG, value)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "안전 측정", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        try { heartTracker?.unsetEventListener() } catch (_: Exception) {}
        try { skinTracker?.unsetEventListener() } catch (_: Exception) {}
        try { service?.disconnectService() } catch (_: Exception) {}
        updateStatus("측정 종료")
        super.onDestroy()
    }
}
