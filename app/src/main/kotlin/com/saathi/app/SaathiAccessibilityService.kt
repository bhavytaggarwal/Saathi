package com.saathi.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.provider.AlarmClock
import android.util.Log
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.app.core.Allowlist
import java.io.ByteArrayOutputStream

/** Read-only eyes and the overlay's host. It has no ability to act on any app. */
class SaathiAccessibilityService : AccessibilityService() {
    var assistant: Assistant? = null
        private set

    override fun onServiceConnected() {
        instance = this
        addPhoneApps()
        // Set before start(), because start() fires reminders missed while the service was away,
        // and those reach the assistant through this field.
        val created = Assistant(this)
        assistant = created
        created.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // Only the type, package and screen class are used. The event's own text is never touched.
        assistant?.onEvent(event.eventType, pkg, event.className?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        assistant?.onConfigurationChanged()
    }

    override fun onDestroy() {
        assistant?.shutdown()
        assistant = null
        instance = null
        super.onDestroy()
    }

    /**
     * Finds this phone's own Clock, Calculator and home screen apps, which phone makers name
     * differently, and lets Saathi read them too. Only preinstalled system apps count, so an app
     * she installs later cannot get itself read by answering the same intents.
     */
    private fun addPhoneApps() {
        fun systemAppsFor(intent: Intent): Set<String> {
            @Suppress("DEPRECATION")
            val found = runCatching { packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            return found.mapNotNull { info ->
                val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                app.packageName.takeIf { system && it != packageName }
            }.toSet()
        }
        val added = Allowlist.addPhoneApps(
            clocks = systemAppsFor(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            calculators = systemAppsFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)),
            launchers = systemAppsFor(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
        )
        // Package names only, so the log shows what was found on a new phone.
        Log.i("SaathiAllowlist", "phone apps added: $added")
        if (added.isEmpty()) return
        // The XML list filters events. Without this, these apps would send none.
        runCatching {
            serviceInfo = serviceInfo.apply {
                packageNames = ((packageNames?.toSet() ?: Allowlist.ALL) + added).toTypedArray()
            }
        }
    }

    /** Only the package name of the active window. No content is read here. */
    fun activePackage(): String? = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    /**
     * The single way to reach screen content. Android's packageNames setting filters events only
     * and does not protect the active window, so this is what keeps other apps unread.
     */
    fun allowedRoot(): Pair<AccessibilityNodeInfo, String>? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val pkg = root.packageName?.toString() ?: return null
        return if (Allowlist.isAllowed(pkg)) root to pkg else null
    }

    /** Takes a screenshot on Android 11+. blocked is true when the app forbids screenshots. */
    fun capture(onResult: (jpegBase64: String?, blocked: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(null, false)
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    Thread {
                        val encoded = encode(result)
                        mainExecutor.execute { onResult(encoded, false) }
                    }.start()
                }

                override fun onFailure(errorCode: Int) {
                    onResult(null, errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW)
                }
            },
        )
    }

    private fun encode(result: ScreenshotResult): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val buffer = result.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            val software = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            hardware.recycle()
            val width = 768
            val scaled = if (software.width > width) {
                Bitmap.createScaledBitmap(software, width, software.height * width / software.width, true)
            } else {
                software
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            if (scaled !== software) scaled.recycle()
            software.recycle()
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            buffer.close()
        }
    }

    companion object {
        @Volatile var instance: SaathiAccessibilityService? = null
            private set
    }
}
