package com.example.jarvisteste.actions

import android.content.Context
import android.content.Intent
import java.text.Normalizer

/**
 * Índice dos aplicativos com ícone na gaveta. Traduz o nome falado ("spotify",
 * "whatsapp", "zap") no pacote que deve ser aberto, tolerando erros de
 * transcrição do reconhecedor.
 *
 * Enumerar todos os apps exige a permissão `QUERY_ALL_PACKAGES` (ver manifesto).
 */
class AppIndex(context: Context) {

    data class AppEntry(val label: String, val packageName: String)

    private val entries: List<AppEntry>
    private val byLabel: Map<String, AppEntry>

    init {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            AppEntry(label, pkg)
        }.distinctBy { it.packageName }

        entries = found
        byLabel = found.associateBy { normalize(it.label) }
    }

    /** Procura o app mais provável para o nome falado, ou `null` se nada casar. */
    fun find(query: String): AppEntry? {
        val q = normalize(query)
        if (q.isEmpty()) return null

        byLabel[q]?.let { return it }

        // Palavra exata dentro do rótulo ("whatsapp" em "WhatsApp Business").
        entries.firstOrNull { normalize(it.label).split(' ').contains(q) }?.let { return it }

        // Rótulo que começa com o termo ("spot" → "Spotify").
        entries.firstOrNull { normalize(it.label).startsWith(q) }?.let { return it }

        // Rótulo que contém o termo.
        entries.firstOrNull { normalize(it.label).contains(q) }?.let { return it }

        // Pacote que contém o termo ("com.spotify.music").
        val compact = q.replace(" ", "")
        entries.firstOrNull { it.packageName.lowercase().contains(compact) }?.let { return it }

        // Último recurso: distância de edição pequena (erros de transcrição).
        val threshold = if (q.length <= 4) 1 else 2
        return entries
            .map { it to levenshtein(q, normalize(it.label)) }
            .filter { it.second <= threshold }
            .minByOrNull { it.second }
            ?.first
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
