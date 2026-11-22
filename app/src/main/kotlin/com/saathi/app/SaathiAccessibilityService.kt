package com.saathi.app

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.saathi.app.core.Allowlist
import com.saathi.app.core.Lines
import java.io.ByteArrayOutputStream

/** Read-only eyes and the overlay's host. It has no ability to act on any app. */
class SaathiAccessibilityService : AccessibilityService() {
    var assistant: Assistant? = null
        private set

    // This phone's own Clock, Calculator and home apps, and any ask-first app she has allowed for
    // this session. Together with the fixed list they make up the event filter.
    private var phoneApps: Set<String> = emptySet()
    private var sessionApps: Set<String> = emptySet()
    @Volatile private var steppingOutForBanking = false

    override fun onServiceConnected() {
        instance = this
        steppingOutForBanking = false
        // Stop any pending setup TTS from MainActivity or in-app Assistant immediately.
        VoiceIO.stopAllSpeaking()
        cancelReEnableNotification()
        Prefs(this).apply {
            pausedForBanking = false
            onboardingCompleted = true
        }
        addPhoneApps()
        // Set before start(), because start() fires reminders missed while the service was away,
        // and those reach the assistant through this field.
        val created = Assistant(this)
        assistant = created
        created.start(announceConnected = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // 1. Strict Banking & UPI Apps (Google Pay, PhonePe, Paytm, YONO SBI, HDFC, ICICI, Axis,
        // Kotak, bob World, PNB One): post 1-tap re-enable notification, remove overlays, save
        // pausedForBanking = true, speak reactive reassurance, and call disableSelf().
        if (pkg in STRICT_BANK_PACKAGES) {
            if (!steppingOutForBanking) {
                steppingOutForBanking = true
                Log.i("SaathiPrivacy", "Strict bank/UPI app $pkg detected; stepping out with disableSelf()")
                Prefs(this).pausedForBanking = true
                postReEnableNotification()
                assistant?.stepOutForStrictBankApp(pkg, Lines.BANK_STEP_OUT_SPEECH)
                disableSelf()
            }
            return
        }
        // 2. Other financial or credential packages: immediately pause the AI overlay and
        // ignore screen contents without disabling the service.
        if (Allowlist.isFinancialOrSecureApp(pkg)) {
            assistant?.onSecureAppEntered(pkg)
            return
        }
        // Only the type, package and screen class are used. The event's own text is never touched.
        assistant?.onEvent(event.eventType, pkg, event.className?.toString())
    }

    /**
     * Proactively disables Accessibility as soon as a merchant checkout / payment-options screen
     * (e.g. Flipkart / Amazon Payment Options -> Google Pay) is detected, BEFORE GPay launches.
     */
    fun proactiveCheckoutStepOut(pkg: String) {
        if (steppingOutForBanking) return
        steppingOutForBanking = true
        Log.i("SaathiPrivacy", "Merchant checkout/payment screen in $pkg detected; proactively calling disableSelf()")
        Prefs(this).pausedForBanking = true
        postReEnableNotification()
        assistant?.stepOutForStrictBankApp(pkg, Lines.CHECKOUT_PAYMENT_STEP_OUT_SPEECH)
        disableSelf()
    }

    private fun postReEnableNotification() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                REENABLE_CHANNEL_ID,
                "Saathi Screen Helper",
                NotificationManager.IMPORTANCE_HIGH,
            )
            manager.createNotificationChannel(channel)
            val settingsIntent = AccessibilitySetupGuide.createSettingsIntent(this, newTask = true)
            val pending = PendingIntent.getActivity(
                this,
                REENABLE_NOTIFICATION_ID,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val action = Notification.Action.Builder(null, Lines.ENABLE_ACCESSIBILITY_BUTTON, pending).build()
            val notification = Notification.Builder(this, REENABLE_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(Lines.ENABLE_ACCESSIBILITY_BUTTON)
                .setContentText("Payment ke baad yahan dabakar Saathi wapas chalu karein")
                .setContentIntent(pending)
                .setOngoing(true)
                .setAutoCancel(true)
                .addAction(action)
                .build()
            manager.notify(REENABLE_NOTIFICATION_ID, notification)
        }
    }

    private fun cancelReEnableNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(REENABLE_NOTIFICATION_ID)
        }
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
        phoneApps = added
    }

    /**
     * Records ask-first apps allowed for this help session. Events are delivered for all apps
     * (packageNames is null), while whether a screen may be read is decided by Allowlist.isAllowed.
     */
    fun setSessionPackages(packages: Set<String>): Boolean {
        sessionApps = packages.filterTo(HashSet(), Allowlist::isAskFirst)
        Log.i("SaathiAllowlist", "event packages session=$sessionApps applied=true")
        return true
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
        if (Allowlist.isFinancialOrSecureApp(pkg)) return null
        return if (Allowlist.isAllowed(pkg)) root to pkg else null
    }

    /** Takes a screenshot on Android 11+. Respects FLAG_SECURE windows so passwords/OTPs are never captured. */
    fun capture(onResult: (jpegBase64: String?, blocked: Boolean) -> Unit) {
        val active = activePackage()
        // Only apps that are always allowed and never financial/secure apps.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Allowlist.isFinancialOrSecureApp(active) ||
            Allowlist.tier(active) != Allowlist.Tier.ALLOWED
        ) {
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
                    val isFlagSecure = errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                    if (isFlagSecure) {
                        // In Google Pay or financial apps, a FLAG_SECURE window is a PIN/payment screen:
                        // immediately hide the floating overlay. In ordinary shopping/content apps (e.g.
                        // Flipkart), keep the floating overlay alive and fall back to text DOM only.
                        if (active == Allowlist.GPAY || Allowlist.isFinancialOrSecureApp(active)) {
                            Log.i("SaathiPrivacy", "FLAG_SECURE window in financial/UPI app; pausing overlay")
                            assistant?.onFlagSecureDetected()
                        } else {
                            Log.i("SaathiPrivacy", "FLAG_SECURE window in $active; skipping screenshot and using text DOM")
                        }
                    }
                    onResult(null, isFlagSecure)
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
            // Downscale to 540px width and compress as JPEG quality 60 (~30-40 KB) to minimize latency.
            val width = 540
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
        private const val REENABLE_CHANNEL_ID = "saathi_reenable"
        private const val REENABLE_NOTIFICATION_ID = 2001
        val STRICT_BANK_PACKAGES: Set<String> = Allowlist.STRICT_BANK_PACKAGES

        @Volatile var instance: SaathiAccessibilityService? = null
            private set
    }
}
