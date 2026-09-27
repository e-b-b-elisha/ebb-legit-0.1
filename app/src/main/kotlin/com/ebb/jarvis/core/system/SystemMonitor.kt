package com.ebb.jarvis.core.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SystemStatus(
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
    val clock: String = "--:--",
    val date: String = "",
    val network: String = "OFFLINE",
    val memoryUsedPercent: Int = 0,
    val storageFreeGb: Double = 0.0,
)

/**
 * The telemetry behind the HUD rings. Polled rather than broadcast-driven: the home
 * screen only renders while it is on top, so a one-second snapshot costs less than
 * the receiver lifecycle it would replace.
 */
class SystemMonitor(private val context: Context) {

    fun statusFlow(): Flow<SystemStatus> = flow {
        while (true) {
            emit(snapshot())
            delay(1_000L)
        }
    }.distinctUntilChanged()

    fun snapshot(): SystemStatus {
        val now = LocalDateTime.now()
        return SystemStatus(
            batteryPercent = batteryPercent(),
            charging = isCharging(),
            clock = now.format(CLOCK),
            date = now.format(DATE).uppercase(),
            network = networkLabel(),
            memoryUsedPercent = memoryUsedPercent(),
            storageFreeGb = storageFreeGb(),
        )
    }

    private fun batteryPercent(): Int = runCatching {
        val manager = context.getSystemService(BatteryManager::class.java)
        manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }.getOrDefault(-1).coerceIn(0, 100)

    private fun isCharging(): Boolean = runCatching {
        // Sticky broadcast: returns immediately, registers nothing.
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            else -> false
        }
    }.getOrDefault(false)

    private fun networkLabel(): String = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching "UNKNOWN"
        val capabilities = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            ?: return@runCatching "OFFLINE"
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "LINKED"
        }
    }.getOrDefault("UNKNOWN")

    private fun memoryUsedPercent(): Int = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java)
            ?: return@runCatching 0
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        if (info.totalMem <= 0L) return@runCatching 0
        (((info.totalMem - info.availMem).toDouble() / info.totalMem) * 100).toInt()
    }.getOrDefault(0).coerceIn(0, 100)

    private fun storageFreeGb(): Double = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        (stat.availableBytes.toDouble() / 1_000_000_000.0)
    }.getOrDefault(0.0)

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
    }
}
