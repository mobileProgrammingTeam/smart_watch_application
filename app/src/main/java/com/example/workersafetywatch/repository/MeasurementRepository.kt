package com.example.workersafetywatch.repository

import android.util.Log
import com.example.workersafetywatch.model.HealthMeasurement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 최근 생체신호 측정 결과를 보관하는 Repository.
 *
 * 영구 저장이 아닌, 인메모리 상의 최신 측정값만 유지한다.
 * UI(ViewModel)와 MeasurementService가 이 Repository를 통해 데이터를 공유한다.
 */
class MeasurementRepository {

    companion object {
        private const val TAG = "MeasurementRepository"
    }

    private val _latestMeasurement = MutableStateFlow<HealthMeasurement?>(null)
    /** 가장 최근의 측정 결과. UI에서 관찰하여 표시한다. */
    val latestMeasurement: StateFlow<HealthMeasurement?> = _latestMeasurement.asStateFlow()

    /**
     * 새로운 측정 결과를 저장한다.
     */
    fun updateMeasurement(measurement: HealthMeasurement) {
        Log.d(TAG, "Measurement updated: hr=${measurement.heartRate}, spo2=${measurement.spo2}")
        _latestMeasurement.value = measurement
    }
}
