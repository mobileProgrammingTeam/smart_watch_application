package com.example.workersafety.watch

import org.junit.Assert.*
import org.junit.Test

class MeasurementWindowTest {
    @Test fun averagesOnlyValidHeartRateSamples() {
        val window = MeasurementWindow(1_000)
        window.addHeartRate(70, 1_100)
        window.addHeartRate(90, 1_200)
        window.addHeartRate(0, 1_300) // invalid value
        window.addHeartRate(140, 900) // outside the window
        val result = window.closeWindow(301_000, 5)
        assertEquals(80.0, result.averageHeartRate!!, 0.00001)
        assertEquals(2, result.heartRateCount)
    }
    @Test fun missingSensorsDoNotInventMeasurements() {
        val result = MeasurementWindow(0).closeWindow(300_000, 5)
        assertNull(result.averageHeartRate)
        assertNull(result.wristSkinTemperatureC)
        assertEquals(0, result.heartRateCount)
        assertEquals(0, result.temperatureCount)
    }
    @Test fun rejectsBadSkinTemperaturesAndResetsWindow() {
        val window = MeasurementWindow(0)
        window.addSkinTemperature(31.5, 22.0, 1000)
        window.addSkinTemperature(100.0, 22.0, 2000) // invalid value
        val first = window.closeWindow(300_000, 5)
        assertEquals(31.5, first.wristSkinTemperatureC!!, 0.00001)
        assertEquals(1, first.temperatureCount)
        val second = window.closeWindow(600_000, 5)
        assertNull(second.wristSkinTemperatureC)
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedInterval() { MeasurementWindow(0).closeWindow(120_000, 2) }
}
