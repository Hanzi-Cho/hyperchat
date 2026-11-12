package dev.hanzicho.hyperchat.core

import dev.hanzicho.hyperchat.platform.ScreenSnapshot
import java.util.Locale

/** Screen text is observation data. It cannot dispatch an action. */
fun guidePrompt(question: String, snapshot: ScreenSnapshot): String {
    fun clean(value: String, max: Int) = value.filterNot { it.isISOControl() }.take(max)
    val questionText = clean(question, 500)
    val header = "Question: $questionText\nApp: ${clean(snapshot.packageName, 72)}\nVisible controls:\n"
    val tail = "\nRecent Hyperchat events: " + snapshot.events.takeLast(2).joinToString(" | ") { clean(it, 52) } +
        "\nAnswer the question in one sentence using an exact visible control label if relevant."
    val words = questionText.lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.take(16)
    val searchQuestion = "검색" in questionText || "search" in words
    val context = buildString {
        snapshot.nodes.asSequence().sortedByDescending { node ->
            val label = (node.text + " " + node.description).lowercase(Locale.ROOT)
            words.count { it in label } * 4 +
                (if (node.editable) 3 else 0) + (if (node.clickable) 1 else 0) +
                (if (searchQuestion && ("검색" in label || "search" in label)) 6 else 0)
        }.mapNotNull { node ->
            val text = clean(node.text, 80)
            val description = clean(node.description, 64)
            if (text.isBlank() && description.isBlank()) return@mapNotNull null
            "- " + listOf(text, description).filter(String::isNotBlank).distinct().joinToString(" / ") +
                if (node.editable) " (input)" else if (node.clickable) " (button)" else ""
        }.distinct().take(10).forEach { appendLine(it) }
    }.take(1200 - header.length - tail.length)
    return header + context + tail
}

internal fun attachmentPrompt(question: String, text: String, sourceTruncated: Boolean): String {
    val safeQuestion = question.filterNot { it.isISOControl() }.take(500)
    val safeText = text.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
    val head = "Question: $safeQuestion\nAttached text:\n"
    val end = "\nEnd of attached text."
    val partial = "\nAttachment excerpt only."
    val budget = 1200 - head.length - end.length - partial.length
    return head + safeText.take(budget) + end + if (sourceTruncated || safeText.length > budget) partial else ""
}
