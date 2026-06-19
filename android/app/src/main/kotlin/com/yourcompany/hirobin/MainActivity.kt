package com.yourcompany.hirobin

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.yourcompany.hirobin.services.CALL_CHANNEL_ID
import com.yourcompany.hirobin.services.CallAudioManager
import com.yourcompany.hirobin.services.CallConnectionService
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
        if (requestCode == REQUEST_ROLE_DIALER && resultCode == Activity.RESULT_OK) {
            // User granted ROLE_DIALER — re-register the phone account now that
            // we hold the role, so Telecom recognises us as the default dialer.
            registerPhoneAccount()
        }
    }

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
        try {
            val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
            val serviceComponent = ComponentName(this, CallConnectionService::class.java)

            // Remove any stale self-managed account left over from a previous install
            // so Telecom doesn't see two conflicting registrations for the same service.
            for (handle in telecomManager.selfManagedPhoneAccounts) {
                if (handle.componentName == serviceComponent) {
                    telecomManager.unregisterPhoneAccount(handle)
                }
            }

            val handle = PhoneAccountHandle(serviceComponent, "HiRobin")
            val account = PhoneAccount.builder(handle, "HiRobin AI Assistant")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build()
            telecomManager.registerPhoneAccount(account)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register phone account", e)
        }
    }
}
