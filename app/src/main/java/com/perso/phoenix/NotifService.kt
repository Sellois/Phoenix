package com.perso.phoenix

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateMapOf

class NotifService : NotificationListenerService() {
    companion object {
        val counts = mutableStateMapOf<String, Int>()
        @Volatile var ctrl: MediaController? = null
    }
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(s: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(s: StatusBarNotification?) = refresh()

    private fun refresh() {
        val m = HashMap<String, Int>()
        runCatching {
            activeNotifications.forEach {
                if ((it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && it.isClearable)
                    m[it.packageName] = (m[it.packageName] ?: 0) + 1
            }
        }
        counts.clear(); counts.putAll(m)
        ctrl = runCatching {
            (getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager)
                .getActiveSessions(ComponentName(this, NotifService::class.java)).firstOrNull()
        }.getOrNull()
    }
}
