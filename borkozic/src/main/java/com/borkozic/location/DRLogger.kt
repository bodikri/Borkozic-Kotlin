package com.borkozic.location

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * DRLogger — асинхронен файл-базиран логър за Dead Reckoning събития.
 *
 * Записва събитията в Downloads/Borkozic/dr_log_<дата>.txt
 * за анализ след полет/тест. Всеки запис има timestamp.
 *
 * Архитектура:
 * - Логовете се натрупват в неблокиращ ConcurrentLinkedQueue (ring buffer)
 * - Background single-thread executor ги записва на диск на всеки ~500ms
 * - Това НЕ блокира sensor/UI нишката
 * - При close() се flush-ва буфера и се чака записа да завърши
 *
 * Честотно ограничение:
 * - Сензорните данни (SENSOR[...]) се логват максимум 5 пъти/сек (200ms интервал)
 * - DR_LOC и DR_DEBUG се логват максимум 5 пъти/сек
 * - Лifecycle събития (DR_START, DR_STOP, CRASH) се логват винаги без ограничение
 *
 * Честотното ограничение е отделно за всеки сензор тип (ACCEL, GYRO, MAG, BARO)
 * за да не се потискат сензорите един друг.
 */
object DRLogger {
    private const val TAG = "DRLogger"
    private const val LOG_DIR = "Borkozic"
    private const val LOG_PREFIX = "dr_log_"

    // Асинхрон запис — background нишка (re-creatable за нова DR сесия след close)
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var initialized = false
    @Volatile private var shutdown = false

    private var logFile: File? = null
    private var writer: PrintWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // Честотно ограничение за сензорни и позиционни логове (5Hz = 200ms)
    private const val SENSOR_LOG_INTERVAL_MS = 200L
    private const val LOC_LOG_INTERVAL_MS = 200L
    @Volatile private var lastLocLogTime: Long = 0

    // Отделен timestamp за всеки сензор тип (за да не се потискат взаимно)
    private val lastSensorLogTimes = HashMap<String, Long>()

    /**
     * Инициализира лог файла. Извиква се при първо записване.
     */
    private fun init(context: Context) {
        if (initialized) return
        if (shutdown) return  // Не re-init след close (стар код, вече не се използва)
        initialized = true

        // Ако executor-ът е shutdown (след close()), създаваме нов
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, LOG_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            logFile = File(dir, "$LOG_PREFIX$dateStr.txt")
            writer = PrintWriter(FileWriter(logFile, true), true) // append mode, auto-flush
            log("=== DRLogger initialized ===")
            log("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            log("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        } catch (e: Exception) {
            Log.e(TAG, "Не може да създаде лог файл: ${e.message}")
            initialized = false
        }

        // Стартиране на периодичен flush на буфера (всеки 500ms)
        try {
            executor.scheduleWithFixedDelay({
                try {
                    flushBuffer()
                } catch (e: Exception) {
                    Log.e(TAG, "Flush грешка: ${e.message}")
                }
            }, 500, 500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // Executor може да е shutdown при close() — игнорираме
            Log.e(TAG, "Schedule грешка: ${e.message}")
        }
    }

    /**
     * Записва съобщение в лог файла с timestamp.
     * Неблокиращо — добавя към queue, background нишката го записва.
     */
    fun log(context: Context, msg: String) {
        init(context)
        log(msg)
    }

    /**
     * Записва съобщение (без context — използва само ако вече е инициализиран).
     * Неблокиращо — добавя към queue.
     */
    fun log(msg: String) {
        if (shutdown) {
            // След close() — директен запис ако writer е отворен, иначе logcat само
            val ts = dateFormat.format(Date())
            val line = "[$ts] $msg"
            try {
                writer?.println(line)
                writer?.flush()
            } catch (e: Exception) {
                // Игнорираме
            }
            Log.i(TAG, msg)
            return
        }
        val ts = dateFormat.format(Date())
        val line = "[$ts] $msg"
        logQueue.add(line)
        // Logcat само за lifecycle събития (не за сензорни данни — прекалява logcat)
        if (!msg.startsWith("SENSOR[") && !msg.startsWith("DR_LOC:") && !msg.startsWith("DR_DEBUG:")) {
            Log.i(TAG, msg)
        }
    }

    /**
     * Записва DR позиция с детайлни данни.
     * Честотно ограничение: 5Hz (200ms интервал).
     */
    fun logLocation(context: Context, lat: Double, lon: Double, alt: Double,
                    speed: Float, bearing: Float, accuracy: Float) {
        val now = System.currentTimeMillis()
        if (now - lastLocLogTime < LOC_LOG_INTERVAL_MS) return
        lastLocLogTime = now

        val msg = String.format(Locale.US,
            "DR_LOC: lat=%.7f, lon=%.7f, alt=%.1f, speed=%.2f, bearing=%.1f, acc=%.1f",
            lat, lon, alt, speed, bearing, accuracy
        )
        log(context, msg)
    }

    /**
     * Записва сензорни данни.
     * Честотно ограничение: 5Hz (200ms интервал) — отделно за всеки сензор тип.
     */
    fun logSensor(context: Context, sensor: String, values: FloatArray) {
        val now = System.currentTimeMillis()
        val last = lastSensorLogTimes[sensor] ?: 0L
        if (now - last < SENSOR_LOG_INTERVAL_MS) return
        lastSensorLogTimes[sensor] = now

        val msg = String.format(Locale.US,
            "SENSOR[%s]: x=%.4f, y=%.4f, z=%.4f",
            sensor, values[0], values[1], if (values.size > 2) values[2] else 0f
        )
        log(context, msg)
    }

    /**
     * Записва сензорни данни с timestamp от SensorEvent.
     * Честотно ограничение: 5Hz (200ms интервал) — отделно за всеки сензор тип.
     * Използва се за raw логване на сензорни стойности за debug и анализ.
     */
    fun logSensorData(context: Context, sensor: String, values: FloatArray, timestamp: Long) {
        val now = System.currentTimeMillis()
        val last = lastSensorLogTimes[sensor] ?: 0L
        if (now - last < SENSOR_LOG_INTERVAL_MS) return
        lastSensorLogTimes[sensor] = now

        val msg = String.format(Locale.US,
            "SENSOR[%s] ts=%d: x=%.4f, y=%.4f, z=%.4f",
            sensor, timestamp, values[0], values[1], if (values.size > 2) values[2] else 0f
        )
        log(context, msg)
    }

    /**
     * Flush-ва буфера към файла. Извиква се от background нишката.
     */
    private fun flushBuffer() {
        val w = writer ?: return
        var line: String? = logQueue.poll()
        while (line != null) {
            try {
                w.println(line)
            } catch (e: Exception) {
                Log.e(TAG, "Грешка при запис в лог: ${e.message}")
                break
            }
            line = logQueue.poll()
        }
        try {
            w.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Flush грешка: ${e.message}")
        }
    }

    /**
     * Затваря лог файла и спира background нишката.
     * Flush-ва буфера и чака записа да завърши.
     * След close() логовете отиват директно във файла (без queue) или logcat.
     */
    fun close() {
        shutdown = true
        // Последен flush на буфера
        flushBuffer()
        try {
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при затваряне на executor: ${e.message}")
        }
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Грешка при затваряне на лог: ${e.message}")
        }
        writer = null
        initialized = false
        // НЕ оставяме shutdown=true — при нова DR сесия трябва да може да се re-init-не
        // Също изчистваме lastSensorLogTimes за нова сесия
        lastSensorLogTimes.clear()
        lastLocLogTime = 0
        shutdown = false
    }
}