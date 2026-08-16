package soy.engindearing.omnitak.mobile.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import soy.engindearing.omnitak.mobile.MainActivity
import soy.engindearing.omnitak.mobile.R
import soy.engindearing.omnitak.mobile.data.TakMissionTask

/**
 * Local notifications for mission tasks (new / overdue / return_by warning).
 */
class PsrTaskNotifier(private val context: Context) {

    fun notifyTask(task: TakMissionTask, kind: Kind = Kind.UPDATE) {
        ensureChannel()
        val title = when (kind) {
            Kind.NEW -> "Новое задание"
            Kind.OVERDUE -> "Просрочен возврат"
            Kind.UPDATE -> "Задание"
        }
        val text = buildString {
            append(task.title)
            task.assignee?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            task.returnBy?.takeIf { it.isNotBlank() }?.let { append(" · срок ").append(it) }
        }
        val tap = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_MISSION_SYNC, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId(task.uid),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.mipmap.app_icon)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(
                if (kind == Kind.OVERDUE) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(task.uid), n) }
    }

    /** Diff previous vs next task lists; notify on new uids or overdue transitions. */
    fun notifyDiff(previous: List<TakMissionTask>, next: List<TakMissionTask>) {
        val prevByUid = previous.associateBy { it.uid }
        for (t in next) {
            val old = prevByUid[t.uid]
            when {
                old == null && t.status == "issued" -> notifyTask(t, Kind.NEW)
                t.status == "overdue" && old?.status != "overdue" -> notifyTask(t, Kind.OVERDUE)
            }
        }
    }

    enum class Kind { NEW, OVERDUE, UPDATE }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Задания ПСР", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        const val CHANNEL_ID = "psr_tasks"
        const val EXTRA_OPEN_MISSION_SYNC = "psr_open_mission_sync"
        fun notificationId(uid: String): Int = BASE + (uid.hashCode() and 0xFFFF)
        private const val BASE = 0x5100
    }
}
