package com.example.workersafetywatch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.workersafetywatch.measurement.MeasurementService
import com.example.workersafetywatch.model.HealthMeasurement
import com.example.workersafetywatch.model.WorkerInfo
import com.example.workersafetywatch.sensor.HealthSensorManager
import com.example.workersafetywatch.viewmodel.WatchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Galaxy Watch 메인 화면.
 *
 * Jetpack Compose for Wear OS를 사용하여 다음을 표시한다:
 * - 작업자 정보 (이름, ID, 소속)
 * - 생체신호 (심박수, SpO₂)
 * - 최근 측정 시각
 * - 연결 상태
 *
 * UI는 상태를 관찰(observe)만 하며, 센서를 직접 제어하지 않는다.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var viewModel: WatchViewModel

    /** 런타임 권한 요청 런처 */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "BODY_SENSORS permission granted: $isGranted")
        viewModel.onPermissionResult(isGranted)
        if (isGranted) {
            startMeasurementService()
        } else {
            Log.w(TAG, "BODY_SENSORS permission denied — 측정 불가")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity created")

        viewModel = ViewModelProvider(this)[WatchViewModel::class.java]

        // 권한 확인 및 요청
        checkAndRequestPermission()

        setContent {
            WatchApp(viewModel = viewModel)
        }
    }

    /**
     * BODY_SENSORS 권한을 확인하고, 없으면 요청한다.
     */
    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BODY_SENSORS
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "BODY_SENSORS permission already granted")
                viewModel.onPermissionResult(true)
                startMeasurementService()
            }
            else -> {
                Log.d(TAG, "Requesting BODY_SENSORS permission")
                permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
            }
        }
    }

    /**
     * 생체신호 측정 Foreground Service를 시작한다.
     */
    private fun startMeasurementService() {
        Log.d(TAG, "Starting MeasurementService")
        val intent = Intent(this, MeasurementService::class.java)
        startForegroundService(intent)
    }
}

// ──────────────────────────────────────────────
// Compose UI
// ──────────────────────────────────────────────

/** 색상 정의 */
private object WatchColors {
    val Background = Color(0xFF1A1A2E)
    val CardBackground = Color(0xFF16213E)
    val HeartRateColor = Color(0xFFFF6B6B)
    val SpO2Color = Color(0xFF4ECDC4)
    val TextPrimary = Color(0xFFEEEEEE)
    val TextSecondary = Color(0xFFAAAAAA)
    val TextMuted = Color(0xFF666666)
    val DividerColor = Color(0xFF2A2A4A)
    val ConnectedColor = Color(0xFF4CAF50)
    val DisconnectedColor = Color(0xFFFF9800)
    val ErrorColor = Color(0xFFE53935)
}

/**
 * 워치 앱 전체 화면 구성.
 */
@Composable
fun WatchApp(viewModel: WatchViewModel) {
    val workerInfo by viewModel.workerInfo.collectAsState()
    val measurement by viewModel.latestMeasurement.collectAsState()
    val sensorState by viewModel.sensorConnectionState.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WatchColors.Background)
        ) {
            // 시계 표시
            TimeText()

            // 메인 콘텐츠
            val listState = rememberScalingLazyListState()
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                state = listState
            ) {
                // 상단: 작업자 정보
                item {
                    WorkerInfoSection(workerInfo = workerInfo)
                }

                // 구분선
                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(1.dp)
                            .background(WatchColors.DividerColor)
                    )
                }

                // 하단: 생체신호 정보
                item {
                    if (!permissionGranted) {
                        PermissionDeniedSection()
                    } else {
                        HealthDataSection(measurement = measurement)
                    }
                }

                // 연결 상태
                item {
                    ConnectionStatusSection(sensorState = sensorState)
                }
            }
        }
    }
}

/**
 * 작업자 정보 영역.
 */
@Composable
fun WorkerInfoSection(workerInfo: WorkerInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "작업자 정보",
            fontSize = 10.sp,
            color = WatchColors.TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 작업자 이름
        Text(
            text = workerInfo.workerName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = WatchColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 작업자 ID
        Text(
            text = "ID : ${workerInfo.workerId}",
            fontSize = 11.sp,
            color = WatchColors.TextSecondary,
            textAlign = TextAlign.Center
        )

        // 소속 (있는 경우)
        workerInfo.department?.let { dept ->
            Text(
                text = dept,
                fontSize = 11.sp,
                color = WatchColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 생체신호 정보 영역.
 */
@Composable
fun HealthDataSection(measurement: HealthMeasurement?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "현재 측정값",
            fontSize = 10.sp,
            color = WatchColors.TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (measurement == null) {
            // 측정 전 상태
            Text(
                text = "측정 대기 중...",
                fontSize = 13.sp,
                color = WatchColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        } else {
            // 심박수
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "♥",
                    fontSize = 16.sp,
                    color = WatchColors.HeartRateColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (measurement.heartRateValid && measurement.heartRate != null) {
                        "${measurement.heartRate} BPM"
                    } else {
                        "-- BPM"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (measurement.heartRateValid) {
                        WatchColors.TextPrimary
                    } else {
                        WatchColors.TextMuted
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // SpO₂
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "O₂",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WatchColors.SpO2Color
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (measurement.spo2Valid && measurement.spo2 != null) {
                        "${measurement.spo2} %"
                    } else {
                        "-- %"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (measurement.spo2Valid) {
                        WatchColors.TextPrimary
                    } else {
                        WatchColors.TextMuted
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 최근 측정 시각
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = timeFormat.format(Date(measurement.measuredAt))
            Text(
                text = "최근 측정 : $timeString",
                fontSize = 10.sp,
                color = WatchColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 권한 미허용 상태 표시.
 */
@Composable
fun PermissionDeniedSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠",
            fontSize = 20.sp,
            color = WatchColors.ErrorColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "센서 권한이 필요합니다",
            fontSize = 12.sp,
            color = WatchColors.ErrorColor,
            textAlign = TextAlign.Center
        )
        Text(
            text = "설정에서 권한을 허용해주세요",
            fontSize = 10.sp,
            color = WatchColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 센서/스마트폰 연결 상태 표시.
 */
@Composable
fun ConnectionStatusSection(sensorState: HealthSensorManager.ConnectionState) {
    val (statusText, statusColor) = when (sensorState) {
        HealthSensorManager.ConnectionState.CONNECTED ->
            "센서 연결됨" to WatchColors.ConnectedColor

        HealthSensorManager.ConnectionState.CONNECTING ->
            "연결 중..." to WatchColors.DisconnectedColor

        HealthSensorManager.ConnectionState.DISCONNECTED ->
            "연결 대기" to WatchColors.TextMuted

        HealthSensorManager.ConnectionState.ERROR ->
            "연결 오류" to WatchColors.ErrorColor
    }

    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // 상태 인디케이터 점
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = statusText,
            fontSize = 9.sp,
            color = statusColor
        )
    }
}
