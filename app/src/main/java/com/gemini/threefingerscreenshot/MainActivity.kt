package com.gemini.threefingerscreenshot

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.io.DataOutputStream

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request root access so LSPosed and the screenshot command have permission
        requestRootAccess()
        
        // Let the user know the app must be enabled in LSPosed
        Toast.makeText(
            this,
            "¡Por favor, activa el módulo en LSPosed y reinicia tu dispositivo!",
            Toast.LENGTH_LONG
        ).show()
        
        // Close the activity, there's no UI needed
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
            
            if (process.exitValue() == 0) {
                Toast.makeText(this, "Permiso Root concedido exitosamente", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No se pudo obtener acceso Root", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error solicitando acceso Root: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
