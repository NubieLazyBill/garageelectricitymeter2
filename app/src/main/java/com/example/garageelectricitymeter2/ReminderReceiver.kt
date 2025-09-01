package com.example.garageelectricitymeter2

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar


class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(context)
        // Переустанавливаем напоминание на следующий месяц
        setupNextMonthlyReminder(context)
    }

    private fun showNotification(context: Context) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, "electricity_reminder_channel")
            .setSmallIcon(android.R.drawable.ic_lock_lock) // 🔒
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 💡
            .setSmallIcon(android.R.drawable.ic_menu_help) // ❓
            .setContentTitle("💡 Напоминание об оплате")
            .setContentText("Не забудьте оплатить электроэнергию за этот месяц!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(1, notification)
    }

    private fun createNotificationChannel(context: Context) {
        // Проверяем, не создан ли уже канал
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel("electricity_reminder_channel") == null) {
            val channel = NotificationChannel(
                "electricity_reminder_channel",
                "Напоминания об оплате",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминания об оплате электроэнергии"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupNextMonthlyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1) // Следующий месяц
            set(Calendar.DAY_OF_MONTH, 14)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // Если время уже прошло сегодня, добавляем месяц
            if (timeInMillis < System.currentTimeMillis()) {
                add(Calendar.MONTH, 1)
            }
        }

        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}