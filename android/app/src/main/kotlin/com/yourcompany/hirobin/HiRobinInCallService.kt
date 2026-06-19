package com.yourcompany.hirobin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.yourcompany.hirobin.services.CallAudioManager
import com.yourcompany.hirobin.services.CALL_CHANNEL_ID
import com.yourcompany.hirobin.services.CALL_NOTIFICATION_ID

class HiRobinInCallService : InCallService() {

    private var activeCall: Call? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call

        val caller = call.details.handle?.schemeSpecificPart ?: "Unknown"
        CallEventBus.onIncomingCall(caller)
        showCallNotification(caller)

        // Auto-answer after 1500 ms — gives Telecom time to complete call setup.
        handler.postDelayed({
            if (call.state == Call.STATE_RINGING || call.state == Call.STATE_NEW) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                val am = getSystemService(AudioManager::class.java)
                am?.let { CallAudioManager.startStreaming(it) }
            }
        }, 1500)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (call == activeCall) {
            handler.removeCallbacksAndMessages(null)
            CallAudioManager.stopStreaming()
            getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIFICATION_ID)
            activeCall = null
        }
    }

    private fun showCallNotification(caller: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        if (nm.getNotificationChannel(CALL_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CALL_CHANNEL_ID, "Active Call", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Robin is handling your call")
            .setContentText("Caller: $caller")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()

        nm.notify(CALL_NOTIFICATION_ID, notification)
    }
}
