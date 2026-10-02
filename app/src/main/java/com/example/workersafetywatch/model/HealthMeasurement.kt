package com.example.workersafetywatch.model

/**
 * 생체신호 측정 결과 데이터 모델.
 *
 * Galaxy Watch에서 측정한 심박수와 SpO₂를 하나의 객체로 묶어
 * 스마트폰으로 전송하거나 UI에 표시할 때 사용한다.
 *
 * @property workerId 작업자 고유 ID
 * @property heartRate 심박수 (BPM). 측정 실패 시 null
 * @property spo2 산소포화도 (%). 측정 실패 시 null
 * @property measuredAt 측정 시각 (epoch millis)
 * @property heartRateValid 심박수 측정값의 유효 여부
 * @property spo2Valid SpO₂ 측정값의 유효 여부
 */
data class HealthMeasurement(
    val workerId: String,
    val heartRate: Int?,
    val spo2: Int?,
    val measuredAt: Long,
    val heartRateValid: Boolean,
    val spo2Valid: Boolean
)
