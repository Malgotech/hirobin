package com.yourcompany.hirobin.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import com.yourcompany.hirobin.CallEventBus
import com.yourcompany.hirobin.MainActivity

const val CALL_CHANNEL_ID = "hirobin_active_call"
const val CALL_NOTIFICATION_ID = 1001

class HiRobinConnection : Connection() {

    init {
        // Use VoIP audio mode so audio routing matches the WebSocket stream.
        audioModeIsVoip = true
        // Do not advertise hold capability — Robin never puts calls on hold.
        connectionCapabilities = connectionCapabilities and
                CAPABILITY_HOLD.inv() and
                CAPABILITY_SUPPORT_HOLD.inv()
    }

    var audioManager: AudioManager? = null
    var notificationManager: NotificationManager? = null

    override fun onAnswer() {
        super.onAnswer()
        setActive()
        audioManager?.let { CallAudioManager.startStreaming(this, it) }
    }

    override fun onReject() {
        super.onReject()
        CallAudioManager.stopStreaming()
        notificationManager?.cancel(CALL_NOTIFICATION_ID)
        CallConnectionService.pendingConnection = null
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        CallAudioManager.stopStreaming()
        notificationManager?.cancel(CALL_NOTIFICATION_ID)
        CallConnectionService.pendingConnection = null
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }
}

class CallConnectionService : ConnectionService() {

    companion object {
        var pendingConnection: HiRobinConnection? = null
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val callerNumber = request?.address?.schemeSpecificPart ?: "Unknown"
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        val nm = getSystemService(NotificationManager::class.java)

        val connection = HiRobinConnection().apply {
            this.audioManager = audioManager
            this.notificationManager = nm
        }

        // Signal ringing so Telecom knows the call is being presented to the user.
        connection.setRinging()

        CallEventBus.onIncomingCall(callerNumber)
        pendingConnection = connection
        showCallNotification(callerNumber, nm)

        // Auto-answer after 1500 ms — gives Telecom time to fully set up the
        // connection before we transition to active and open the WebSocket.
        Handler(Looper.getMainLooper()).postDelayed({
            if (connection.state == Connection.STATE_RINGING) {
                connection.onAnswer()
            }
        }, 1500)

        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }

    private fun showCallNotification(caller: String, nm: NotificationManager?) {
        if (nm == null) return

        if (nm.getNotificationChannel(CALL_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CALL_CHANNEL_ID,
                    "Active Call",
                    NotificationManager.IMPORTANCE_HIGH
                )
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
