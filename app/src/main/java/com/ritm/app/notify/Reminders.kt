package com.ritm.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ritm.app.MainActivity
import com.ritm.app.R
import com.ritm.app.data.Store
import com.ritm.app.data.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Reminders {
    const val CH_TASKS = "tasks"
    const val CH_DAILY = "daily"
    const val CH_FOOD = "food"
    const val EXTRA_OPEN = "open"
    const val OPEN_FOOD_CAMERA = "food_camera"
    const val OPEN_PROGRESS_NEW = "progress_new"
    private const val RC_WEEKLY = 30
    private const val NOTIF_WEEKLY = 100004
    private const val RC_FOOD_BASE = 10
    const val MAX_FOOD_TIMES = 6
    private const val NOTIF_FOOD = 100003
    private const val RC_DIGEST = 1
    private const val RC_WORKOUT = 2
    private const val NOTIF_DIGEST = 100001
    private const val NOTIF_WORKOUT = 100002

    const val EXTRA_TYPE = "type"
    const val EXTRA_TASK_ID = "taskId"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_TASKS, "Напоминания о задачах", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Уведомления о задачах в назначенное время"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DAILY, "План на день и тренировки", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Утренний план и напоминание о тренировке"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FOOD, "Напоминания о еде", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминание сфоткать приём пищи"
            }
        )
    }

    private fun pending(ctx: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAlarm(ctx: Context, atMillis: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun taskIntent(ctx: Context, id: Long) = Intent(ctx, AlarmReceiver::class.java)
        .setAction("com.ritm.app.TASK_$id")
        .putExtra(EXTRA_TYPE, "task")
        .putExtra(EXTRA_TASK_ID, id)

    fun scheduleTask(ctx: Context, t: Task) {
        val pi = pending(ctx, t.id.hashCode(), taskIntent(ctx, t.id))
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val time = t.time
        if (t.done || time == null) {
            am.cancel(pi)
            return
        }
        val at = LocalDateTime.of(t.date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis()) {
            am.cancel(pi)
            return
        }
        setAlarm(ctx, at, pi)
    }

    fun cancelTask(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx, id.hashCode(), taskIntent(ctx, id)))
    }

    private fun nextAt(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var dt = LocalDate.now().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (!dt.isAfter(now.plusSeconds(5))) dt = dt.plusDays(1)
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun nextWeekly(day: Int, hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val diff = Math.floorMod(day.coerceIn(1, 7) - today.dayOfWeek.value, 7)
        var dt = today.plusDays(diff.toLong()).atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        if (!dt.isAfter(now.plusSeconds(5))) dt = dt.plusWeeks(1)
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun scheduleDaily(ctx: Context) {
        val s = Store.settings
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return

        val digest = pending(
            ctx, RC_DIGEST,
            Intent(ctx, AlarmReceiver::class.java).setAction("com.ritm.app.DIGEST").putExtra(EXTRA_TYPE, "digest")
        )
        if (s.digestEnabled) setAlarm(ctx, nextAt(s.digestHour, s.digestMinute), digest) else am.cancel(digest)

        val workout = pending(
            ctx, RC_WORKOUT,
            Intent(ctx, AlarmReceiver::class.java).setAction("com.ritm.app.WORKOUT").putExtra(EXTRA_TYPE, "workout")
        )
        if (s.workoutReminder) setAlarm(ctx, nextAt(s.workoutHour, s.workoutMinute), workout) else am.cancel(workout)

        val weekly = pending(
            ctx, RC_WEEKLY,
            Intent(ctx, AlarmReceiver::class.java).setAction("com.ritm.app.WEEKLY").putExtra(EXTRA_TYPE, "weekly")
        )
        if (s.weeklyReminder) setAlarm(ctx, nextWeekly(s.weeklyDay, s.weeklyHour, s.weeklyMinute), weekly) else am.cancel(weekly)

        for (i in 0 until MAX_FOOD_TIMES) {
            val pi = pending(
                ctx, RC_FOOD_BASE + i,
                Intent(ctx, AlarmReceiver::class.java).setAction("com.ritm.app.FOOD_$i").putExtra(EXTRA_TYPE, "food")
            )
            val minutes = s.foodTimes.getOrNull(i)
            if (s.foodReminders && minutes != null) setAlarm(ctx, nextAt(minutes / 60, minutes % 60), pi) else am.cancel(pi)
        }
    }

    fun rescheduleAll(ctx: Context) {
        Store.tasks.forEach { scheduleTask(ctx, it) }
        scheduleDaily(ctx)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, channel: String, title: String, text: String, openExtra: String? = null) {
        if (!canNotify(ctx)) return
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openExtra != null) intent.putExtra(EXTRA_OPEN, openExtra)
        val open = PendingIntent.getActivity(
            ctx, if (openExtra != null) 7 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF7B61C9.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (channel == CH_TASKS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun showDigest(ctx: Context) {
        Store.carryOver()
        val today = LocalDate.now()
        val list = Store.tasks.filter { it.date == today && !it.done }
            .sortedWith(compareBy<Task>({ it.time ?: LocalTime.MAX }, { it.createdAt }))
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val text = if (list.isEmpty()) {
            "На сегодня задач нет. Хорошего дня! ☀️"
        } else {
            list.take(8).joinToString("\n") { t -> "• " + (t.time?.let { it.format(fmt) + " " } ?: "") + t.title } +
                if (list.size > 8) "\n…и ещё ${list.size - 8}" else ""
        }
        show(ctx, NOTIF_DIGEST, CH_DAILY, "План на сегодня · ${list.size}", text)
    }

    fun showWorkout(ctx: Context, force: Boolean = false) {
        val today = LocalDate.now()
        val exercises = Store.program[today.dayOfWeek.value].orEmpty()
        if (!force && (exercises.isEmpty() || Store.logs.any { it.date == today })) return
        val text = if (exercises.isEmpty()) {
            "Сегодня по программе отдых. Так будет выглядеть напоминание в день тренировки."
        } else {
            "Сегодня ${exercises.size} упр.: " + exercises.joinToString(", ") { it.name }
        }
        show(ctx, NOTIF_WORKOUT, CH_DAILY, "Время тренировки 💪", text)
    }

    fun showWeekly(ctx: Context, force: Boolean = false) {
        val recent = Store.checks.any { !it.date.isBefore(LocalDate.now().minusDays(4)) }
        if (!force && recent) return
        val last = Store.checks.maxByOrNull { it.date }?.weight
        val text = "Встань на весы утром натощак и сделай фото в том же месте и свете." +
            (last?.let { " Прошлый вес: ${com.ritm.app.data.numText(it)} кг." } ?: "")
        show(ctx, NOTIF_WEEKLY, CH_DAILY, "Отчёт недели 📸", text, OPEN_PROGRESS_NEW)
    }

    fun showFood(ctx: Context, force: Boolean = false) {
        // если недавно уже фоткал — не дёргаем
        val recent = Store.food.any { System.currentTimeMillis() - it.time < 90 * 60 * 1000L }
        if (!force && recent) return
        val count = Store.food.count { it.date == LocalDate.now() }
        val text = "Нажми, чтобы сразу открыть камеру. Сегодня записано приёмов пищи: $count"
        show(ctx, NOTIF_FOOD, CH_FOOD, "Сфоткай, что ешь 🍽️", text, OPEN_FOOD_CAMERA)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        when (intent.getStringExtra(Reminders.EXTRA_TYPE)) {
            "task" -> {
                val id = intent.getLongExtra(Reminders.EXTRA_TASK_ID, -1L)
                val task = Store.tasks.find { it.id == id } ?: return
                if (task.done) return
                val time = task.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""
                Reminders.show(context, id.hashCode(), Reminders.CH_TASKS, task.title, "Напоминание · $time")
            }
            "digest" -> {
                Reminders.showDigest(context)
                Reminders.scheduleDaily(context)
            }
            "workout" -> {
                Reminders.showWorkout(context)
                Reminders.scheduleDaily(context)
            }
            "weekly" -> {
                Reminders.showWeekly(context)
                Reminders.scheduleDaily(context)
            }
            "food" -> {
                Reminders.showFood(context)
                Reminders.scheduleDaily(context)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Store.carryOver()
        Reminders.rescheduleAll(context)
    }
}
