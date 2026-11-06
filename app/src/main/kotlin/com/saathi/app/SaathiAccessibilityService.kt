package com.saathi.app

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
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
        assistant = Assistant(this).also { it.start() }
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
