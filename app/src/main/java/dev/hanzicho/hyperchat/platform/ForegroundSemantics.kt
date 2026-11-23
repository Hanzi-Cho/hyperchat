package dev.hanzicho.hyperchat.platform

import java.util.Locale

/** Selects foreground controls from the live tree, independently of the package name. */
internal object ForegroundSemantics {
    fun searchEntry(nodes: List<SearchNode>): Int? = unique(nodes) { node ->
        if (!button(node) || !searchControl(node)) return@unique 0
        if (node.labels.any { key(it) in searchLabels }) 2 else 1
    }

    fun searchField(nodes: List<SearchNode>, afterEntry: Boolean): Int? = unique(nodes) { node ->
        if (!node.visible || !node.enabled || !node.screen.editable || !node.canSetText) return@unique 0
        when {
            // An editable value may be the previous query. Use the field's hint, ID and role.
            searchControl(node, listOf(node.hint, node.screen.description)) -> 2
            afterEntry && (node.screen.focused || nodes.any { it.visible && !it.screen.editable && searchControl(it) }) -> 1
            else -> 0
        }
    }

    fun searchSubmit(nodes: List<SearchNode>): Int? = unique(nodes) { node ->
        if (!button(node)) return@unique 0
        when {
            key(node.id).contains("searchsubmit") || key(node.id).contains("submitsearch") -> 3
            !searchControl(node) -> 0
            node.labels.any { key(it) in submitLabels } -> 2
            else -> 0
        }
    }

    fun openTarget(nodes: List<SearchNode>, label: String): Int? = matchingButtons(nodes, label, false).singleOrNull()

    fun resultTarget(nodes: List<SearchNode>, query: String): Int? = matchingButtons(nodes, query, true).singleOrNull()

    fun resultSignature(nodes: List<SearchNode>, query: String): String? {
        val row = resultTarget(nodes, query) ?: return null
        val result = nodes[row]
        val hasEditor = nodes.any { it.visible && it.screen.editable && it.canSetText }
        val hasResults = nodes.any { node ->
            node.visible && !node.screen.editable && (node.labels + node.id).any { label ->
                val normalized = key(label)
                "검색결과" in normalized || normalized.startsWith("찾은") || normalized.startsWith("results") ||
                    "searchresult" in normalized
            }
        }
        if (hasEditor && !hasResults) return null
        return listOf(result.screen.viewId, result.labels.joinToString()).joinToString("|")
    }

    fun destinationEvidence(nodes: List<SearchNode>, label: String): Set<ScreenNode> =
        nodes.indices.mapNotNullTo(mutableSetOf()) { index ->
            val node = nodes[index]
            if (!node.visible || node.screen.editable || node.screen.clickable ||
                lineage(nodes, index).drop(1).any { button(nodes[it]) }
            ) return@mapNotNullTo null
            node.screen.takeIf {
                node.labels.any { matches(it, label) || matches(it.substringAfter(':', ""), label) }
            }
        }

    private fun matchingButtons(nodes: List<SearchNode>, label: String, result: Boolean): List<Int> =
        nodes.indices.mapNotNull { index ->
            val node = nodes[index]
            if (!node.visible || node.screen.editable || node.labels.none { matches(it, label) }) return@mapNotNull null
            val lineage = lineage(nodes, index).toList()
            if (lineage.any { ancestor -> nodes[ancestor].screen.editable ||
                    (result && listOf("suggest", "autocomplete", "history", "recent").any { it in key(nodes[ancestor].id) }) }) {
                return@mapNotNull null
            }
            lineage.firstOrNull { button(nodes[it]) }
        }.distinct()

    private fun lineage(nodes: List<SearchNode>, index: Int): Sequence<Int> =
        generateSequence(index) { nodes[it].parent.takeIf { parent -> parent in 0 until it } }.take(12)

    private fun matches(label: String, target: String): Boolean {
        val normalized = key(label)
        return normalized.isNotEmpty() && (normalized == key(target) ||
            (label.substringAfterLast('.', "").lowercase(Locale.ROOT) in fileExtensions &&
                key(label.substringBeforeLast('.')) == key(target)))
    }

    private fun searchControl(node: SearchNode, labels: List<String> = node.labels): Boolean {
        val values = labels + node.id
        if (values.any { label -> ignoredSearchLabels.any { it in key(label) } }) return false
        return values.any { label ->
            "검색" in label || searchWord.containsMatchIn(label) || searchIds.any { key(label).startsWith(it) }
        }
    }

    private fun button(node: SearchNode) = node.visible && node.enabled && node.screen.clickable && !node.screen.editable

    private fun unique(nodes: List<SearchNode>, rank: (SearchNode) -> Int): Int? {
        val ranked = nodes.map(rank)
        val highest = ranked.maxOrNull()?.takeIf { it > 0 } ?: return null
        return ranked.indices.singleOrNull { ranked[it] == highest }
    }

    private fun key(value: String) = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private val searchLabels = setOf("검색", "검색하기", "검색창", "search", "searchbox", "searchbutton")
    private val submitLabels = setOf("검색", "검색하기", "search", "submitsearch")
    private val searchWord = Regex("(?:^|[^a-z0-9])search(?:$|[^a-z0-9])", RegexOption.IGNORE_CASE)
    private val searchIds = setOf("searchbar", "searchbox", "searchentry", "searchbutton", "searchinput", "searchedit", "searchquery", "searchsrc")
    private val ignoredSearchLabels = setOf("clear", "close", "voice", "suggest", "history", "savesearch", "deletesearch",
        "지우", "닫기", "음성", "기록", "저장", "삭제")
    private val fileExtensions = setOf("txt", "md", "pdf", "docx", "xlsx", "jpg", "png")
}
