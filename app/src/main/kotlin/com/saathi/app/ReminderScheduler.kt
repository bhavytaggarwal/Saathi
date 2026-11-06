package com.saathi.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Reminder(
    val id: Int,
    val title: String,
    val titleHi: String,
    val daily: Boolean,
    val hour: Int,
    val minute: Int,
    val nextAt: Long,
    val fired: Boolean = false,
) {
    fun whenText(): String {
        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(nextAt))
        return if (daily) "Every day, $time" else SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(nextAt))
    }
}

/** Schedules reminders with Android's alarm service and restores them after a restart. */
class ReminderScheduler(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("saathi_reminders", Context.MODE_PRIVATE)
    private val alarms = ctx.getSystemService(AlarmManager::class.java)

    fun all(): List<Reminder> {
        val array = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        return List(array.length()) { index ->
            val json = array.getJSONObject(index)
            Reminder(
                id = json.getInt("id"),
                title = json.getString("title"),
                titleHi = json.getString("titleHi"),
                daily = json.getBoolean("daily"),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
                nextAt = json.getLong("nextAt"),
                fired = json.optBoolean("fired"),
            )
        }.sortedBy { it.nextAt }
    }

    private fun save(items: List<Reminder>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("titleHi", it.titleHi)
                    .put("daily", it.daily)
                    .put("hour", it.hour)
                    .put("minute", it.minute)
                    .put("nextAt", it.nextAt)
                    .put("fired", it.fired)
            )
        }
        // Synchronous: the process may be stopped straight after an alarm fires.
        prefs.edit().putString(KEY, array.toString()).commit()
    }

    private fun nextId(items: List<Reminder>) = (items.maxOfOrNull { it.id } ?: 0) + 1

    fun addOnce(title: String, titleHi: String, at: Long): Reminder {
        val items = all()
        val calendar = Calendar.getInstance().apply { timeInMillis = at }
        val reminder = Reminder(
            nextId(items), title, titleHi, false,
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), at,
        )
        save(items + reminder)
        arm(reminder)
        return reminder
    }

    fun addDaily(title: String, titleHi: String, hour: Int, minute: Int): Reminder {
        val items = all()
        val reminder = Reminder(nextId(items), title, titleHi, true, hour, minute, nextDaily(hour, minute))
        save(items + reminder)
        arm(reminder)
        return reminder
    }

    fun delete(id: Int) {
        alarms.cancel(alarmIntent(id))
        save(all().filter { it.id != id })
        cancelNotification(id)
    }

    fun snooze(id: Int, minutes: Int = 10) {
        val reminder = get(id) ?: return
        val updated = reminder.copy(nextAt = System.currentTimeMillis() + minutes * 60_000L, fired = false)
        save(all().map { if (it.id == id) updated else it })
        arm(updated)
        cancelNotification(id)
    }

    /** "Ho gaya": a one-time reminder is finished. A daily one is already set for tomorrow. */
    fun acknowledge(id: Int) {
        val reminder = get(id)
        if (reminder != null && !reminder.daily) save(all().filter { it.id != id })
        cancelNotification(id)
    }

    fun get(id: Int): Reminder? = all().firstOrNull { it.id == id }

    /** Runs when an alarm fires. Moves a daily reminder on and tells the character. */
    fun fire(id: Int) {
        val reminder = get(id) ?: return
        if (reminder.daily) {
            val updated = reminder.copy(nextAt = nextDaily(reminder.hour, reminder.minute))
            save(all().map { if (it.id == id) updated else it })
            arm(updated)
        } else {
            // Kept until she answers, but marked so a restart cannot fire it a second time.
            save(all().map { if (it.id == id) it.copy(fired = true) else it })
        }
        showNotification(reminder)
        SaathiAccessibilityService.instance?.assistant?.onReminder(reminder)
    }

    /** After a restart: alarms are gone, so set them again and fire any that were missed. */
    fun restoreAll() {
        val now = System.currentTimeMillis()
        for (reminder in all()) {
            when {
                reminder.fired -> if (now - reminder.nextAt > DAY_MS) delete(reminder.id)
                reminder.nextAt <= now -> fire(reminder.id)
                else -> arm(reminder)
            }
        }
    }

    private fun nextDaily(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    private fun alarmIntent(id: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        id,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arm(reminder: Reminder) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) {
            // An alarm clock is the one alarm type phone makers' battery savers rarely delay.
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, ListsActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.nextAt, show), alarmIntent(reminder.id))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.nextAt, alarmIntent(reminder.id))
        }
    }

    private fun actionIntent(action: String, id: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        id * 10 + action.hashCode() % 7,
        Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun showNotification(reminder: Reminder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val notification = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Saathi: yaad dila rahi hoon")
            .setContentText(reminder.title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Ho gaya", actionIntent(ACTION_DONE, reminder.id)).build())
            .addAction(Notification.Action.Builder(null, "10 minute baad", actionIntent(ACTION_SNOOZE, reminder.id)).build())
            .build()
        manager.notify(NOTIFICATION_BASE + reminder.id, notification)
    }

    private fun cancelNotification(id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + id)
    }

    companion object {
        private const val KEY = "reminders"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_BASE = 1000
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        const val ACTION_FIRE = "com.saathi.app.FIRE"
        const val ACTION_DONE = "com.saathi.app.DONE"
        const val ACTION_SNOOZE = "com.saathi.app.SNOOZE"
        const val EXTRA_ID = "id"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, -1)
        if (id < 0) return
        val scheduler = ReminderScheduler(context)
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                // Keeps the phone awake long enough to speak the reminder with the screen off.
                context.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "saathi:reminder")
                    .acquire(20_000L)
                scheduler.fire(id)
            }
            ReminderScheduler.ACTION_DONE -> {
                scheduler.acknowledge(id)
                SaathiAccessibilityService.instance?.assistant?.dismissReminder(id)
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                scheduler.snooze(id)
                SaathiAccessibilityService.instance?.assistant?.dismissReminder(id)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler(context).restoreAll()
    }
}
