package com.yourcompany.hirobin

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Satisfies the ROLE_DIALER requirement.
 *
 * Android will not list an app in "Default Phone App" unless it has an Activity
 * that handles ACTION_DIAL / ACTION_CALL with the DEFAULT category. This Activity
 * fulfils that contract and immediately hands off to MainActivity so the user
 * lands on the normal HiRobin UI.
 */
class DialerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forward = Intent(this, MainActivity::class.java).apply {
            // Carry the original intent data (tel: URI, voicemail:, etc.) through.
            intent.data?.let { data = it }
            intent.action?.let { action = it }
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        startActivity(forward)
        finish()
    }
}
