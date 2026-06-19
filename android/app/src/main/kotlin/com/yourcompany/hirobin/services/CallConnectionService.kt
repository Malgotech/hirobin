package com.yourcompany.hirobin.services

import android.media.AudioManager
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import com.yourcompany.hirobin.CallEventBus

class HiRobinConnection : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
    }

    var audioManager: AudioManager? = null

    override fun onAnswer() {
        super.onAnswer()
        setActive()
        audioManager?.let { CallAudioManager.startStreaming(this, it) }
    }

    override fun onReject() {
        super.onReject()
        CallAudioManager.stopStreaming()
        CallConnectionService.pendingConnection = null
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        CallAudioManager.stopStreaming()
        CallConnectionService.pendingConnection = null
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }
}

class CallConnectionService : ConnectionService() {

    companion object {
        // Holds the ringing connection so debug tooling can answer it programmatically.
        // Cleared to null by HiRobinConnection on disconnect/reject.
        var pendingConnection: HiRobinConnection? = null
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val callerNumber = request?.address?.schemeSpecificPart ?: "Unknown"
        val connection = HiRobinConnection().apply {
            audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        }
        connection.setRinging()
        CallEventBus.onIncomingCall(callerNumber)
        pendingConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
    }
}
