package com.ebb.jarvis.core.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap?,
) {
    /** Cheap match surface for both the drawer filter and the model's launch_app tool. */
    val searchKey: String = label.lowercase()
}

/**
 * The installed-app index behind the drawer and the launch_app tool.
 *
 * [LauncherApps] is the launcher-grade source: it reports per-user activities and
 * survives package-visibility filtering. The [android.content.pm.PackageManager] query
 * is the fallback for OEMs that hand back an empty list before the HOME role is granted.
 */
class AppRepository(private val context: Context) {

    @Volatile
    private var cache: List<AppEntry> = emptyList()

    val apps: List<AppEntry> get() = cache

    suspend fun refresh(): List<AppEntry> = withContext(Dispatchers.IO) {
        val self = context.packageName
        val loaded = (queryLauncherApps() + queryPackageManager())
            .distinctBy { it.component.flattenToString() }
            .filter { it.packageName != self }
            .sortedBy { it.searchKey }
        cache = loaded
        loaded
    }

    private fun queryLauncherApps(): List<AppEntry> = try {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user: UserHandle = Process.myUserHandle()
        launcherApps?.getActivityList(null, user)?.mapNotNull { info ->
            val component = info.componentName ?: return@mapNotNull null
            AppEntry(
                label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: component.packageName,
                packageName = component.packageName,
                component = component,
                icon = runCatching { info.getBadgedIcon(0).toImageBitmap() }.getOrNull(),
            )
        }.orEmpty()
    } catch (t: Throwable) {
        Log.w(TAG, "LauncherApps unavailable", t)
        emptyList()
    }

    private fun queryPackageManager(): List<AppEntry> = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            val component = ComponentName(activity.packageName, activity.name)
            AppEntry(
                label = resolved.loadLabel(context.packageManager)?.toString()
                    ?: activity.packageName,
                packageName = activity.packageName,
                component = component,
                icon = runCatching {
                    resolved.loadIcon(context.packageManager).toImageBitmap()
                }.getOrNull(),
            )
        }
    } catch (t: Throwable) {
        Log.w(TAG, "PackageManager query failed", t)
        emptyList()
    }

    fun launch(entry: AppEntry): Boolean = launchComponent(entry.component)

    private fun launchComponent(component: ComponentName): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
        true
    } catch (t: Throwable) {
        Log.w(TAG, "Could not launch $component", t)
        false
    }

    /**
     * Resolves the loose app name a voice command produces ("maps", "whats app")
     * to a real entry: exact label, then prefix, then contained, then squashed-space.
     */
    fun resolve(query: String): AppEntry? {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null
        val candidates = cache
        return candidates.firstOrNull { it.searchKey == needle }
            ?: candidates.firstOrNull { it.searchKey.startsWith(needle) }
            ?: candidates.firstOrNull { it.searchKey.contains(needle) }
            ?: candidates.firstOrNull { it.packageName.lowercase().contains(needle) }
            ?: run {
                val squashed = needle.replace(" ", "")
                candidates.firstOrNull { it.searchKey.replace(" ", "").contains(squashed) }
            }
    }

    private fun android.graphics.drawable.Drawable.toImageBitmap(): ImageBitmap {
        val size = ICON_PX
        return toBitmap(size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private companion object {
        const val TAG = "AppRepository"
        const val ICON_PX = 144
    }
}
