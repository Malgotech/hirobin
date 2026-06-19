package com.yourcompany.hirobin

import android.os.Handler
import android.os.Looper
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

object CallEventBus {

    private const val CHANNEL = "com.yourcompany.hirobin/calls"

    private var flutterEngine: FlutterEngine? = null
    private val callLogs = mutableListOf<Map<String, Any>>()
    private val userContext = mutableMapOf<String, String>()

    fun setFlutterEngine(engine: FlutterEngine) {
        flutterEngine = engine
    }

    fun onIncomingCall(callerNumber: String) {
        val log = mapOf(
            "caller" to callerNumber,
            "timestamp" to System.currentTimeMillis(),
            "status" to "ringing"
        )
        callLogs.add(0, log)

        val messenger = flutterEngine?.dartExecutor?.binaryMessenger ?: return
        Handler(Looper.getMainLooper()).post {
            MethodChannel(messenger, CHANNEL).invokeMethod("onIncomingCall", log)
        }
    }

    fun getCallLogs(): List<Map<String, Any>> = callLogs.toList()

    fun updateUserContext(context: Map<String, String>) {
        userContext.putAll(context)
    }

    fun getUserContext(): Map<String, String> = userContext.toMap()

    fun onAudioLevel(rms: Float) {
        val messenger = flutterEngine?.dartExecutor?.binaryMessenger ?: return
        Handler(Looper.getMainLooper()).post {
            MethodChannel(messenger, CHANNEL).invokeMethod("onAudioLevel", rms)
        }
    }

    fun onAudioStateChanged(state: String, error: String?) {
        val messenger = flutterEngine?.dartExecutor?.binaryMessenger ?: return
        val args = mapOf("state" to state, "error" to (error ?: ""))
        Handler(Looper.getMainLooper()).post {
            MethodChannel(messenger, CHANNEL).invokeMethod("onAudioStateChanged", args)
        }
    }
}
