Optional independent JVM logic check (no Android SDK required):
kotlinc wear/src/main/java/com/example/workersafety/watch/MeasurementWindow.kt validation/logic_smoke.kt -include-runtime -d /tmp/wslogic.jar
java -jar /tmp/wslogic.jar

Android Studio JUnit tests are in wear/src/test/ and are not executed by this standalone check.
