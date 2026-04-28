package com.nicola.rtspviewer

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("cam", Context.MODE_PRIVATE)

        val etIp   = findViewById<EditText>(R.id.et_ip)
        val etUser = findViewById<EditText>(R.id.et_user)
        val etPass = findViewById<EditText>(R.id.et_pass)

        etIp.setText(prefs.getString("ip",   "192.168.1.10"))
        etUser.setText(prefs.getString("user", "admin"))
        etPass.setText(prefs.getString("pass", "admin"))

        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val ip   = etIp.text.toString().trim()
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (ip.isEmpty()) {
                etIp.error = "Inserisci l'IP"
                return@setOnClickListener
            }

            prefs.edit()
                .putString("ip",   ip)
                .putString("user", user)
                .putString("pass", pass)
                .apply()

            // Riavvia go2rtc con la nuova config
            Go2RtcService.stop(this)
            Go2RtcService.start(this)

            Toast.makeText(this, "Salvato — riavvio server…", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
