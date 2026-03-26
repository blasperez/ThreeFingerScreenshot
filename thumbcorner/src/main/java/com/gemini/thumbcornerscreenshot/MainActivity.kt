package com.gemini.thumbcornerscreenshot

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.io.DataOutputStream

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRootAccess()

        Toast.makeText(
            this,
            "Enable Thumb Corner Screenshot in LSPosed and reboot.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun requestRootAccess() {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor()
        } catch (_: Throwable) {
        }
    }
}
