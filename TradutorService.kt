package com.example.telegramtradutor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de acessibilidade: lê o conteúdo da tela atual
 * (mensagens do Telegram, botões e opções de bots, etc.)
 * SEM interagir com nada — apenas leitura para tradução.
 */
class TradutorService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    fun coletarTextosVisiveis(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val resultado = mutableListOf<String>()
        coletar(root, resultado, 0)
        root.recycle()
        return resultado.distinct()
    }

    private fun coletar(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 80) return
        val texto = node.text?.toString()?.trim()
        if (!texto.isNullOrEmpty() && node.isVisibleToUser) out.add(texto)
        // Botões costumam ter o rótulo no contentDescription
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() &&
            node.className?.toString()?.contains("Button", ignoreCase = true) == true) {
            out.add(desc)
        }
        for (i in 0 until node.childCount) {
            coletar(node.getChild(i), out, depth + 1)
        }
    }

    override fun onServiceConnected() { instancia = this }
    override fun onDestroy() { instancia = null; super.onDestroy() }

    companion object { var instancia: TradutorService? = null }
}
