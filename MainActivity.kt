package com.example.telegramtradutor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val idiomas = linkedMapOf(
        "Português" to "pt",
        "Inglês" to "en",
        "Espanhol" to "es",
        "Francês" to "fr",
        "Alemão" to "de",
        "Italiano" to "it",
        "Russo" to "ru",
        "Árabe" to "ar",
        "Hindi" to "hi",
        "Japonês" to "ja",
        "Coreano" to "ko",
        "Chinês (simplificado)" to "zh"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val spinner = findViewById<Spinner>(R.id.spinnerIdioma)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, idiomas.keys.toList()
        )

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val salvo = prefs.getString("idioma", "pt")
        spinner.setSelection(idiomas.values.indexOf(salvo).coerceAtLeast(0))

        findViewById<Button>(R.id.btnSalvar).setOnClickListener {
            val codigo = idiomas.values.elementAt(spinner.selectedItemPosition)
            prefs.edit().putString("idioma", codigo).apply()
            Toast.makeText(this, "Idioma de destino: $codigo", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAcessibilidade).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnIniciar).setOnClickListener {
            startForegroundService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Tradutor iniciado. Toque na bolha azul sobre o Telegram.",
                Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnParar).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
        }
    }
}
