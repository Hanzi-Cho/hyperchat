package dev.hanzicho.hyperchat.core

import dev.hanzicho.hyperchat.platform.ScreenNode
import java.util.Locale

private val listRequests = setOf("버튼 목록", "버튼 목록 보여줘", "/controls", "list controls")

internal fun isControlListRequest(request: String): Boolean =
    request.trim().lowercase(Locale.ROOT) in listRequests

/** Reads from the bounded, sensitive-node-filtered snapshot. Never include an editable value. */
internal fun visibleControlLabels(nodes: List<ScreenNode>): List<String> =
    nodes.asSequence()
        .filter { it.clickable && !it.editable }
        .map { node -> node.description.ifBlank { node.text }.lineSequence().first().trim() }
        .map { label -> label.filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }.take(64) }
        .filter(String::isNotBlank)
        .distinct()
        .take(8)
        .toList()
