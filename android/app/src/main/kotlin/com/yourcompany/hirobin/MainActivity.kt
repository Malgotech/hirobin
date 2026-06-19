package com.yourcompany.hirobin

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import com.yourcompany.hirobin.services.CALL_CHANNEL_ID
import com.yourcompany.hirobin.services.CallAudioManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.yourcompany.hirobin/calls"
    private val REQUEST_ROLE_DIALER = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationChannel()
        requestDialerRoleIfNeeded()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CALL_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CALL_CHANNEL_ID,
                    "Active Call",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private fun requestDialerRoleIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, REQUEST_ROLE_DIALER)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Nothing extra to do after ROLE_DIALER is granted — HiRobinInCallService
        // will start receiving calls automatically as the default dialer's InCallService.
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        CallEventBus.setFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getCallLogs" -> result.success(CallEventBus.getCallLogs())
                    "setBackendUrl" -> {
                        val url = call.arguments as? String
                        if (url != null) {
                            CallAudioManager.setBackendUrl(url)
                            // Persist so HiRobinInCallService can read it even if
                            // MainActivity hasn't run in the current process session.
                            getSharedPreferences("hirobin_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("backend_url", url)
                                .apply()
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

}
