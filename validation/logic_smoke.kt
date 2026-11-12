import com.example.workersafety.watch.MeasurementWindow
fun main() {
    val window = MeasurementWindow(1_000)
    window.addHeartRate(70, 1_100)
    window.addHeartRate(90, 1_200)
    window.addHeartRate(0, 1_300)
    window.addHeartRate(140, 500)
    window.addSkinTemperature(31.5, 22.1, 1_400)
    window.addSkinTemperature(110.0, 22.1, 1_500)
    val s = window.closeWindow(301_000, 5)
    check(s.averageHeartRate == 80.0)
    check(s.heartRateCount == 2)
    check(s.wristSkinTemperatureC == 31.5)
    check(s.temperatureCount == 1)
    val reset = window.closeWindow(601_000, 5)
    check(reset.averageHeartRate == null)
    check(reset.wristSkinTemperatureC == null)
    println("LOGIC SMOKE PASS: filtering, mean, missing, reset")
}
