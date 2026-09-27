package com.ebb.jarvis.core.ai.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool
import com.ebb.jarvis.core.apps.AppRepository
import com.ebb.jarvis.core.system.SystemMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The hands. Every tool here reaches the system through a public intent or a
 * permission-free manager call, so JARVIS can act on the phone without the app
 * holding SEND_SMS, CALL_PHONE, or calendar write access. Anything that commits
 * on the user's behalf (a message, a call) opens prefilled and waits for a tap.
 */
class DeviceTools(
    private val context: Context,
    private val apps: AppRepository,
    private val system: SystemMonitor,
) {

    fun definitions(): List<Tool> = listOf(
        tool(
            name = "launch_app",
            description = "Open an installed app by name. Use the name the user said; " +
                "matching is fuzzy. Call list_apps first only if a launch has already failed.",
            required = listOf("name"),
            properties = mapOf(
                "name" to schema("string", "App name, e.g. 'Spotify', 'camera', 'whats app'"),
            ),
        ),
        tool(
            name = "list_apps",
            description = "List installed app names, optionally filtered by a substring. " +
                "Use to answer 'what do I have installed' or to disambiguate a failed launch.",
            properties = mapOf(
                "filter" to schema("string", "Optional case-insensitive substring filter"),
            ),
        ),
        tool(
            name = "device_status",
            description = "Current battery level and charge state, network transport, " +
                "memory pressure, free storage, and local date and time.",
        ),
        tool(
            name = "set_timer",
            description = "Start a countdown timer in the phone's clock app.",
            required = listOf("seconds"),
            properties = mapOf(
                "seconds" to schema("integer", "Duration in seconds"),
                "label" to schema("string", "Optional timer label"),
            ),
        ),
        tool(
            name = "set_alarm",
            description = "Set an alarm at a wall-clock time today or tomorrow, " +
                "whichever comes next.",
            required = listOf("hour", "minute"),
            properties = mapOf(
                "hour" to schema("integer", "Hour in 24-hour form, 0-23"),
                "minute" to schema("integer", "Minute, 0-59"),
                "label" to schema("string", "Optional alarm label"),
            ),
        ),
        tool(
            name = "open_url",
            description = "Open a URL in the phone's browser.",
            required = listOf("url"),
            properties = mapOf("url" to schema("string", "Absolute http or https URL")),
        ),
        tool(
            name = "set_torch",
            description = "Turn the camera flash on or off as a torch.",
            required = listOf("on"),
            properties = mapOf("on" to schema("boolean", "true to light it, false to kill it")),
        ),
        tool(
            name = "media_control",
            description = "Send a media key to whatever is currently playing.",
            required = listOf("action"),
            properties = mapOf(
                "action" to schema(
                    "string",
                    "One of: play_pause, next, previous, stop",
                    enum = listOf("play_pause", "next", "previous", "stop"),
                ),
            ),
        ),
        tool(
            name = "create_calendar_event",
            description = "Open the calendar editor prefilled with an event. " +
                "The user confirms the save.",
            required = listOf("title", "start"),
            properties = mapOf(
                "title" to schema("string", "Event title"),
                "start" to schema(
                    "string",
                    "Local start time as ISO-8601 without zone, e.g. 2026-09-28T14:30",
                ),
                "duration_minutes" to schema("integer", "Length in minutes, default 60"),
                "location" to schema("string", "Optional location"),
            ),
        ),
        tool(
            name = "compose_message",
            description = "Open the messaging app with a prefilled SMS. " +
                "It is NOT sent until the user taps send.",
            required = listOf("body"),
            properties = mapOf(
                "number" to schema("string", "Optional phone number"),
                "body" to schema("string", "Message text"),
            ),
        ),
        tool(
            name = "dial_number",
            description = "Open the dialler with a number entered. " +
                "The user places the call.",
            required = listOf("number"),
            properties = mapOf("number" to schema("string", "Phone number to dial")),
        ),
        tool(
            name = "open_settings",
            description = "Open a system settings screen.",
            required = listOf("panel"),
            properties = mapOf(
                "panel" to schema(
                    "string",
                    "Which screen to open",
                    enum = listOf(
                        "main", "wifi", "bluetooth", "display", "sound", "battery",
                        "apps", "location", "date", "home",
                    ),
                ),
            ),
        ),
    )

    suspend fun execute(name: String, input: Map<String, Any?>): ToolOutcome =
        withContext(Dispatchers.Main.immediate) {
            try {
                when (name) {
                    "launch_app" -> launchApp(input.string("name"))
                    "list_apps" -> listApps(input.string("filter"))
                    "device_status" -> deviceStatus()
                    "set_timer" -> setTimer(input.int("seconds"), input.string("label"))
                    "set_alarm" -> setAlarm(
                        input.int("hour"),
                        input.int("minute"),
                        input.string("label"),
                    )
                    "open_url" -> openUrl(input.string("url"))
                    "set_torch" -> setTorch(input.bool("on") == true)
                    "media_control" -> mediaControl(input.string("action"))
                    "create_calendar_event" -> createEvent(
                        input.string("title"),
                        input.string("start"),
                        input.int("duration_minutes") ?: 60,
                        input.string("location"),
                    )
                    "compose_message" -> composeMessage(
                        input.string("number"),
                        input.string("body"),
                    )
                    "dial_number" -> dial(input.string("number"))
                    "open_settings" -> openSettings(input.string("panel"))
                    else -> ToolOutcome.error("Unknown tool: $name")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Tool $name failed", t)
                ToolOutcome.error(t.message ?: t.javaClass.simpleName)
            }
        }

    // --- individual tools ---------------------------------------------------

    private suspend fun launchApp(name: String?): ToolOutcome {
        if (name.isNullOrBlank()) return ToolOutcome.error("No app name given")
        if (apps.apps.isEmpty()) apps.refresh()
        val entry = apps.resolve(name)
            ?: return ToolOutcome.error(
                "No installed app matches \"$name\". Call list_apps to see what is available.",
            )
        return if (apps.launch(entry)) {
            ToolOutcome.ok("Launched ${entry.label}", uiNote = "OPENING ${entry.label.uppercase()}")
        } else {
            ToolOutcome.error("${entry.label} refused to start")
        }
    }

    private suspend fun listApps(filter: String?): ToolOutcome {
        if (apps.apps.isEmpty()) apps.refresh()
        val needle = filter?.trim()?.lowercase().orEmpty()
        val matches = apps.apps
            .filter { needle.isEmpty() || it.searchKey.contains(needle) }
            .map { it.label }
        val shown = matches.take(MAX_LISTED)
        val suffix = if (matches.size > shown.size) {
            " (+${matches.size - shown.size} more)"
        } else {
            ""
        }
        return ToolOutcome.ok("${matches.size} apps: " + shown.joinToString(", ") + suffix)
    }

    private fun deviceStatus(): ToolOutcome {
        val status = system.snapshot()
        val charge = if (status.charging) "charging" else "on battery"
        return ToolOutcome.ok(
            buildString {
                append("Battery ${status.batteryPercent}% ($charge). ")
                append("Network ${status.network}. ")
                append("Memory in use ${status.memoryUsedPercent}%. ")
                append("Free storage ${"%.1f".format(status.storageFreeGb)} GB. ")
                append("Local time ${status.clock} on ${status.date}.")
            },
        )
    }

    private fun setTimer(seconds: Int?, label: String?): ToolOutcome {
        val length = seconds ?: return ToolOutcome.error("No duration given")
        if (length <= 0) return ToolOutcome.error("Duration must be positive")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, length)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!label.isNullOrBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return start(intent, "Timer set for $length seconds", "TIMER · ${length}s")
    }

    private fun setAlarm(hour: Int?, minute: Int?, label: String?): ToolOutcome {
        if (hour == null || minute == null) return ToolOutcome.error("Need both hour and minute")
        if (hour !in 0..23 || minute !in 0..59) return ToolOutcome.error("Time out of range")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (!label.isNullOrBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        val pretty = "%02d:%02d".format(hour, minute)
        return start(intent, "Alarm set for $pretty", "ALARM · $pretty")
    }

    private fun openUrl(url: String?): ToolOutcome {
        val target = url?.trim().orEmpty()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return ToolOutcome.error("Only http and https URLs are allowed")
        }
        return start(
            Intent(Intent.ACTION_VIEW, Uri.parse(target)),
            "Opened $target",
            "BROWSER",
        )
    }

    private fun setTorch(on: Boolean): ToolOutcome {
        val manager = context.getSystemService(CameraManager::class.java)
            ?: return ToolOutcome.error("No camera service")
        val id = manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolOutcome.error("This device has no flash")
        manager.setTorchMode(id, on)
        return ToolOutcome.ok(
            if (on) "Torch on" else "Torch off",
            uiNote = if (on) "TORCH ON" else "TORCH OFF",
        )
    }

    private fun mediaControl(action: String?): ToolOutcome {
        val keyCode = when (action) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return ToolOutcome.error("Unsupported media action: $action")
        }
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return ToolOutcome.error("No audio service")
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ToolOutcome.ok("Sent $action to the active media session")
    }

    private fun createEvent(
        title: String?,
        start: String?,
        durationMinutes: Int,
        location: String?,
    ): ToolOutcome {
        if (title.isNullOrBlank()) return ToolOutcome.error("No event title")
        val startsAt = start?.let { raw ->
            runCatching { LocalDateTime.parse(raw, ISO_LOCAL) }.getOrNull()
        } ?: return ToolOutcome.error(
            "Could not read start time \"$start\"; use ISO-8601 local form like 2026-09-28T14:30",
        )
        val beginMillis = startsAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            .putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                beginMillis + durationMinutes.coerceAtLeast(1) * 60_000L,
            )
        if (!location.isNullOrBlank()) {
            intent.putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        }
        return start(
            intent,
            "Calendar editor open for \"$title\" at $startsAt. The user still has to save it.",
            "CALENDAR DRAFT",
        )
    }

    private fun composeMessage(number: String?, body: String?): ToolOutcome {
        if (body.isNullOrBlank()) return ToolOutcome.error("No message body")
        val uri = if (number.isNullOrBlank()) {
            Uri.parse("smsto:")
        } else {
            Uri.parse("smsto:" + Uri.encode(number))
        }
        val intent = Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", body)
        return start(
            intent,
            "Message composer open with the text prefilled. Not sent; the user taps send.",
            "MESSAGE DRAFT",
        )
    }

    private fun dial(number: String?): ToolOutcome {
        if (number.isNullOrBlank()) return ToolOutcome.error("No number given")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
        return start(
            intent,
            "Dialler open with $number entered. Not dialled; the user places the call.",
            "DIALLER",
        )
    }

    private fun openSettings(panel: String?): ToolOutcome {
        val action = when (panel) {
            "main", null -> Settings.ACTION_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Intent.ACTION_POWER_USAGE_SUMMARY
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "home" -> Settings.ACTION_HOME_SETTINGS
            else -> return ToolOutcome.error("Unknown settings panel: $panel")
        }
        return start(Intent(action), "Opened $panel settings", "SETTINGS")
    }

    // --- plumbing -----------------------------------------------------------

    private fun start(intent: Intent, success: String, uiNote: String?): ToolOutcome {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolOutcome.error("No app on this phone handles that")
        }
        context.startActivity(intent)
        return ToolOutcome.ok(success, uiNote)
    }

    private fun tool(
        name: String,
        description: String,
        required: List<String> = emptyList(),
        properties: Map<String, Map<String, Any>> = emptyMap(),
    ): Tool {
        val props = Tool.InputSchema.Properties.builder().apply {
            properties.forEach { (key, schema) ->
                putAdditionalProperty(key, JsonValue.from(schema))
            }
        }.build()
        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(props)
                    .required(required)
                    .build(),
            )
            .build()
    }

    private fun schema(
        type: String,
        description: String,
        enum: List<String>? = null,
    ): Map<String, Any> = buildMap {
        put("type", type)
        put("description", description)
        if (enum != null) put("enum", enum)
    }

    private fun Map<String, Any?>.string(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() }

    private fun Map<String, Any?>.int(key: String): Int? = when (val raw = this[key]) {
        is Number -> raw.toInt()
        is String -> raw.trim().toIntOrNull()
        else -> null
    }

    private fun Map<String, Any?>.bool(key: String): Boolean? = when (val raw = this[key]) {
        is Boolean -> raw
        is String -> raw.equals("true", ignoreCase = true)
        is Number -> raw.toInt() != 0
        else -> null
    }

    private companion object {
        const val TAG = "DeviceTools"
        const val MAX_LISTED = 60
        val ISO_LOCAL: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}

/**
 * What a tool hands back: [text] goes to the model as the tool result,
 * [uiNote] is the short line the HUD flashes so the user sees the action land.
 */
data class ToolOutcome(
    val text: String,
    val isError: Boolean = false,
    val uiNote: String? = null,
) {
    companion object {
        fun ok(text: String, uiNote: String? = null) = ToolOutcome(text, false, uiNote)
        fun error(text: String) = ToolOutcome(text, true, null)
    }
}
