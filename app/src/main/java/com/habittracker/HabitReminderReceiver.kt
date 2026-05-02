package com.habittracker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

private const val CHANNEL_ID = "habit_reminder_channel"
private const val NOTIFICATION_ID = 1001
private const val ALARM_BASE_REQUEST_CODE = 2001

// Reminder times: 6:00, 8:30, 11:00, 13:30, 16:00, 18:30, 21:00, 23:00
private val REMINDER_TIMES = listOf(
    6 to 0,
    8 to 30,
    11 to 0,
    13 to 30,
    16 to 0,
    18 to 30,
    21 to 0,
    23 to 0
)

class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Only show notification between 6:00 AM and 11:00 PM
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        if (currentHour < 6 || currentHour > 23) return

        createNotificationChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingTapIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use a unique notification ID based on the current time slot to avoid stacking
        val slotId = NOTIFICATION_ID + currentHour

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Habit Pulse \u2728")
            .setContentText("Don\u2019t break the chain! Open the app and tick today\u2019s habits.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Don\u2019t break the chain! Open the app and tick today\u2019s habits before they lock at 1:00 AM.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingTapIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(slotId, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Habit Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders every 2.5 hours to complete your habits"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        /**
         * Schedules repeating alarms at every 2.5-hour interval from 6:00 AM to 11:00 PM.
         * Times: 6:00, 8:30, 11:00, 13:30, 16:00, 18:30, 21:00, 23:00
         */
        fun schedule(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            REMINDER_TIMES.forEachIndexed { index, (hour, minute) ->
                val intent = Intent(context, HabitReminderReceiver::class.java)
                val requestCode = ALARM_BASE_REQUEST_CODE + index
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    // If the time has already passed today, schedule for tomorrow
                    if (before(Calendar.getInstance())) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            }
        }

        /**
         * Cancels all scheduled reminder alarms.
         */
        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            REMINDER_TIMES.forEachIndexed { index, _ ->
                val intent = Intent(context, HabitReminderReceiver::class.java)
                val requestCode = ALARM_BASE_REQUEST_CODE + index
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}

/**
 * Re-schedules all alarms after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("habit_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("reminder_enabled", false)
            if (enabled) {
                HabitReminderReceiver.schedule(context)
            }
        }
    }
}
