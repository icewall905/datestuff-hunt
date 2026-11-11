package dk.datestuff.hunt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.Normalizer

/** Mirror of server/unlock.py + shared/esc.js. */
object Game {
    fun norm(s: String?): String {
        var t = (s ?: "").lowercase().replace("æ", "ae").replace("ø", "o").replace("ß", "ss")
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
        return t.filter { it in 'a'..'z' || it in '0'..'9' }
    }

    fun answersOf(step: Step): List<String> = step.answers.filter { norm(it).isNotEmpty() }

    fun check(step: Step, input: String): Boolean {
        val n = norm(input)
        return n.isNotEmpty() && answersOf(step).any { norm(it) == n }
    }

    private fun enabled(c: Campaign) = c.steps.filter { it.enabled }

    fun openSteps(c: Campaign, solved: Set<String>, forced: Set<String> = emptySet()): List<String> {
        val chapters = c.chapters.map { it.id }
        fun cidx(s: Step): Int = chapters.indexOf(s.chapter).let { if (it < 0) chapters.size else it }
        val en = enabled(c)
        val out = mutableListOf<String>()
        for ((i, s) in en.withIndex()) {
            if (s.id in solved) continue
            if (s.id in forced) { out += s.id; continue }
            if (en.any { cidx(it) < cidx(s) && it.id !in solved }) continue
            val after = s.unlock?.after
            val ok = when {
                after == null -> i == 0 || en[i - 1].id in solved
                after is JsonArray -> after.all { (it as? JsonPrimitive)?.contentOrNull in solved }
                after is JsonPrimitive && after.contentOrNull == "start" -> true
                after is JsonPrimitive && after.contentOrNull == "prev" -> i == 0 || en[i - 1].id in solved
                else -> false
            }
            if (ok) out += s.id
        }
        return out
    }

    fun fragments(c: Campaign): Map<String, String> {
        val word = (c.settings.metaWord ?: "").filter { !it.isWhitespace() }.uppercase()
        val ids = enabled(c).filter { it.reward?.fragment == true }.map { it.id }
        if (word.isEmpty() || ids.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, String>()
        word.forEachIndexed { i, ch -> val k = ids[i % ids.size]; out[k] = (out[k] ?: "") + ch }
        return out
    }

    /** metaWord with only letters from solved fragment steps revealed (null = still hidden). */
    fun wordSlots(c: Campaign, solved: Set<String>): List<Char?> {
        val word = (c.settings.metaWord ?: "").filter { !it.isWhitespace() }.uppercase()
        val ids = enabled(c).filter { it.reward?.fragment == true }.map { it.id }
        if (word.isEmpty() || ids.isEmpty()) return emptyList()
        return word.mapIndexed { i, ch -> if (ids[i % ids.size] in solved) ch else null }
    }
}
