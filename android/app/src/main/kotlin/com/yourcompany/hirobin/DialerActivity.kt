package com.yourcompany.hirobin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Satisfies the ROLE_DIALER requirement.
 *
 * Android will not list an app in "Default Phone App" unless it has an Activity
 * that handles ACTION_DIAL / ACTION_CALL with the DEFAULT category. This Activity
 * fulfils that contract and immediately hands off to MainActivity so the user
 * lands on the normal HiRobin UI.
 *
 * If launched with a tel: URI (e.g. from a long-press "Call" in Contacts), the
 * URI is forwarded so MainActivity can surface it if needed.
 */
class DialerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forward = Intent(this, MainActivity::class.java).apply {
            // Carry the original intent data (tel: URI, voicemail:, etc.) through
            // so MainActivity can inspect it if required.
            intent.data?.let { data = it }
            intent.action?.let { action = it }
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        startActivity(forward)
        finish()
    }
}
