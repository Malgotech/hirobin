package com.yourcompany.hirobin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.yourcompany.hirobin.services.CallConnectionService

class DebugCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INCOMING -> simulateIncomingCall(context, intent)
            ACTION_ANSWER   -> answerPendingCall()
            ACTION_HANGUP   -> hangUpPendingCall()
        }
    }

    private fun simulateIncomingCall(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: "5551234"
        Log.d(TAG, "Simulating incoming call from $number")

        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = PhoneAccountHandle(
            ComponentName(context, CallConnectionService::class.java),
            "HiRobin"
        )
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("tel", number, null))
        }

        try {
            telecom.addNewIncomingCall(handle, extras)
            Log.d(TAG, "addNewIncomingCall dispatched for $number")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing MANAGE_OWN_CALLS permission or account not enabled", e)
        }
    }

    private fun answerPendingCall() {
        val conn = CallConnectionService.pendingConnection
        if (conn == null) {
            Log.w(TAG, "No pending connection to answer")
            return
        }
        Log.d(TAG, "Answering pending connection")
        conn.onAnswer()
    }

    private fun hangUpPendingCall() {
        val conn = CallConnectionService.pendingConnection
        if (conn == null) {
            Log.w(TAG, "No pending connection to hang up")
            return
        }
        Log.d(TAG, "Hanging up connection")
        conn.onDisconnect()
    }

    companion object {
        const val ACTION_INCOMING = "com.yourcompany.hirobin.DEBUG_INCOMING_CALL"
        const val ACTION_ANSWER   = "com.yourcompany.hirobin.DEBUG_ANSWER_CALL"
        const val ACTION_HANGUP   = "com.yourcompany.hirobin.DEBUG_HANGUP_CALL"
        private const val TAG = "DebugCallReceiver"
    }
}
