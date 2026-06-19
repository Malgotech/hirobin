package com.yourcompany.hirobin

import android.content.ComponentName
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.yourcompany.hirobin.services.CallAudioManager
import com.yourcompany.hirobin.services.CallConnectionService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.yourcompany.hirobin/calls"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        CallEventBus.setFlutterEngine(flutterEngine)
        registerPhoneAccount()

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getCallLogs" -> result.success(CallEventBus.getCallLogs())
                    "setBackendUrl" -> {
                        val url = call.arguments as? String
                        if (url != null) {
                            CallAudioManager.setBackendUrl(url)
                            result.success(null)
                        } else {
                            result.error("INVALID_ARGS", "Expected String url", null)
                        }
                    }
                    "updateContext" -> {
                        @Suppress("UNCHECKED_CAST")
                        val ctx = call.arguments as? Map<String, String>
                        if (ctx != null) {
                            CallEventBus.updateUserContext(ctx)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGS", "Expected Map<String, String>", null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun registerPhoneAccount() {
        val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
        val handle = PhoneAccountHandle(
            ComponentName(this, CallConnectionService::class.java),
            "HiRobin"
        )
        val account = PhoneAccount.builder(handle, "HiRobin AI Assistant")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecomManager.registerPhoneAccount(account)
    }
}
