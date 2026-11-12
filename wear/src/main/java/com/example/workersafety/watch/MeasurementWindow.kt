package com.example.workersafety.watch

/** Pure Kotlin logic so its behavior can be checked without a physical watch. */
data class Summary(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val intervalMinutes: Int,
    val averageHeartRate: Double?,
    val heartRateCount: Int,
    val wristSkinTemperatureC: Double?,
    val ambientTemperatureC: Double?,
    val temperatureCount: Int
)

class MeasurementWindow(startMs: Long) {
    private var windowStartMs = startMs
    private var hrSum = 0L
    private var hrCount = 0
    private var lastSkinTemp: Double? = null
    private var lastAmbient: Double? = null
    private var skinCount = 0

    @Synchronized fun addHeartRate(bpm: Int, sampleMs: Long) {
        if (sampleMs < windowStartMs || bpm !in 35..230) return
        hrSum += bpm
        hrCount++
    }

    @Synchronized fun addSkinTemperature(skinC: Double, ambientC: Double?, sampleMs: Long) {
        // Plausibility bounds are data validation, not medical diagnostic thresholds.
        if (sampleMs < windowStartMs || !skinC.isFinite() || skinC !in 10.0..50.0) return
        lastSkinTemp = skinC
        lastAmbient = ambientC?.takeIf { it.isFinite() && it in -30.0..70.0 }
        skinCount++
    }

    /** Returns a snapshot for the finished window, then begins a fresh window. */
    @Synchronized fun closeWindow(endMs: Long, intervalMinutes: Int): Summary {
        require(intervalMinutes == 5 || intervalMinutes == 10) { "Only 5 or 10 minutes supported" }
        val result = Summary(
            windowStartMs, endMs, intervalMinutes,
            if (hrCount > 0) hrSum.toDouble() / hrCount else null,
            hrCount,
            lastSkinTemp,
            lastAmbient,
            skinCount
        )
        windowStartMs = endMs
        hrSum = 0L
        hrCount = 0
        lastSkinTemp = null
        lastAmbient = null
        skinCount = 0
        return result
    }
}
