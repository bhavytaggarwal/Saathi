package com.saathi.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Exists only because Android shares the list of active media sessions through a notification
 * listener. It overrides nothing, so no notification is ever read.
 */
class SaathiMediaListener : NotificationListenerService()

/** Facts about the phone: which app is playing sound and how navigation works. */
class PhoneHelp(context: Context) {
    private val ctx = context.applicationContext

    sealed interface Sound {
        data class FromApp(val label: String, val pkg: String) : Sound
        data object UnknownApp : Sound
        data object Silent : Sound
    }

    val hasMediaAccess: Boolean
        get() {
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
            return enabled.contains(ctx.packageName)
        }

    fun whichAppIsPlaying(): Sound {
        val audio = ctx.getSystemService(AudioManager::class.java)
        if (hasMediaAccess) {
            val sessions = runCatching {
                ctx.getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(ctx, SaathiMediaListener::class.java))
            }.getOrDefault(emptyList())
            val playing = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            if (playing != null) return Sound.FromApp(appLabel(playing.packageName), playing.packageName)
        }
        // Sound with no media session comes from games, ads or short clips. Android hides the
        // owner of that audio from ordinary apps, so all Saathi can say is that something plays.
        return if (audio.isMusicActive) Sound.UnknownApp else Sound.Silent
    }

    private fun appLabel(pkg: String): String = try {
        val manager = ctx.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        "ek app"
    }

    /** "gestures" or "three buttons", so the model can describe the right way to open recent apps. */
    fun navigationMode(): String {
        val id = ctx.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        val mode = if (id != 0) runCatching { ctx.resources.getInteger(id) }.getOrDefault(0) else 0
        return if (mode == 2) "gestures" else "three buttons"
    }
}
