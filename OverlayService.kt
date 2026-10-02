package com.example.telegramtradutor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Serviço de primeiro plano (roda em segundo plano com notificação)
 * que exibe a bolha flutuante. Ao tocar na bolha, lê a tela via
 * TradutorService e mostra um painel com as traduções.
 * Tradução 100% no aparelho via ML Kit (nenhuma mensagem sai do celular).
 */
class OverlayService : Service() {

    companion object { const val CANAL = "tradutor_fg" }

    private lateinit var wm: WindowManager
    private var bolha: View? = null
    private var painel: View? = null
    private var ocupado = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        criarCanal()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notificacao = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setContentTitle("Tradutor Telegram ativo")
            .setContentText("Toque na bolha para traduzir a tela atual.")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1, notificacao)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mostrarBolha()
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CANAL, "Serviço do tradutor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun tipoOverlay() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun mostrarBolha() {
        val v = LayoutInflater.from(this).inflate(R.layout.bolha, null)
        v.setOnClickListener { alternarPainel() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipoOverlay(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 250 }
        wm.addView(v, params)
        bolha = v
    }

    private fun alternarPainel() {
        if (painel != null) { fecharPainel(); return }
        val svc = TradutorService.instancia
        if (svc == null) {
            toast("Ative o serviço de acessibilidade primeiro.")
            return
        }
        val textos = svc.coletarTextosVisiveis()
        if (textos.isEmpty()) { toast("Nenhum texto visível encontrado na tela."); return }
        if (ocupado) return
        ocupado = true
        toast("Traduzindo… (na 1ª vez o modelo de idioma é baixado)")

        val alvo = getSharedPreferences("cfg", MODE_PRIVATE)
            .getString("idioma", "pt") ?: "pt"
        val alvoSeguro = if (TranslateLanguage.getAllLanguages().contains(alvo)) alvo
                         else TranslateLanguage.PORTUGUESE

        val detector = LanguageIdentification.getClient()
        detector.identifyLanguage(textos.take(8).joinToString(" "))
            .continueWithTask { tarefa ->
                val origem = tarefa.result?.takeIf {
                    it != "und" && TranslateLanguage.getAllLanguages().contains(it)
                } ?: TranslateLanguage.ENGLISH
                val opcoes = TranslatorOptions.Builder()
                    .setSourceLanguage(origem)
                    .setTargetLanguage(alvoSeguro)
                    .build()
                val tradutor = Translation.getClient(opcoes)
                tradutor.downloadModelIfNeeded().continueWithTask {
                    Tasks.whenAllSuccess<String>(textos.map { tradutor.translate(it) })
                }
            }
            .addOnSuccessListener { traducoes ->
                @Suppress("UNCHECKED_CAST")
                mostrarPainel(textos.zip(traducoes as List<String>))
            }
            .addOnFailureListener { toast("Falha ao traduzir: ${'$'}{it.message}") }
            .addOnCompleteListener { ocupado = false }
    }

    private fun mostrarPainel(pares: List<Pair<String, String>>) {
        fecharPainel()
        val v = LayoutInflater.from(this).inflate(R.layout.painel, null)
        val conteudo = v.findViewById<LinearLayout>(R.id.conteudo)
        for ((orig, trad) in pares) {
            conteudo.addView(TextView(this).apply {
                text = orig; setTextColor(Color.LTGRAY); textSize = 13f
            })
            conteudo.addView(TextView(this).apply {
                text = trad; setTextColor(Color.WHITE); textSize = 15f
                setPadding(0, 0, 0, 24)
            })
        }
        v.findViewById<Button>(R.id.btnFechar).setOnClickListener { fecharPainel() }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.7).toInt(),
            tipoOverlay(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        wm.addView(v, params)
        painel = v
    }

    private fun fecharPainel() {
        painel?.let { wm.removeView(it) }
        painel = null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        fecharPainel()
        bolha?.let { wm.removeView(it) }
        bolha = null
        super.onDestroy()
    }
}
