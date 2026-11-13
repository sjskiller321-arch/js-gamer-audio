package com.jssofttools.gameraudio

import android.app.*
import android.content.Context
import android.content.Intent

object Notif {
    fun make(ctx: Context, title: String, text: String): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("jsga", "JS Gamer Audio", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(ctx, "jsga")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setOngoing(true).build()
    }
}
