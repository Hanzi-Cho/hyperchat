package dev.hanzicho.hyperchat.platform

import java.util.Locale

internal const val NAVER_MAP_PACKAGE = "com.nhn.android.nmap"
internal const val FIXTURE_PACKAGE = "dev.hanzicho.hyperchat.fixture"

internal fun isSearchTarget(packageName: String): Boolean =
    packageName == NAVER_MAP_PACKAGE || packageName == FIXTURE_PACKAGE

internal data class SearchNode(
    val screen: ScreenNode,
    val parent: Int,
    val hint: String,
    val enabled: Boolean,
    val visible: Boolean,
    val showingHint: Boolean,
    val canSetText: Boolean,
) {
    val id: String get() = screen.viewId.substringAfterLast('/')
    val labels: List<String> get() = listOf(screen.text, screen.description, hint)
}

internal object SearchSemantics {
    private val entryIds = setOf("search_bar", "search_box", "search_entry", "search_button")
    private val inputIds = setOf("search_input", "search_edit_text", "search_query", "search_src_text")
    private val entryLabels = setOf(
        "검색", "검색하기", "검색창", "장소검색", "지도검색", "장소주소버스검색",
        "장소버스지하철주소검색", "장소주소버스지하철검색", "search", "searchplaces", "searchonnavermaps",
        "placebussubwayoraddr",
    )
    private val submitLabels = setOf("검색", "검색하기", "search")
    private val placesTabLabels = setOf("places", "placestab", "장소", "장소탭")
    private val resultIds = setOf(
        "search_result", "search_results", "search_result_list", "search_results_list",
        "place_search_results", "place_result_list",
    )

    fun findEntry(packageName: String, nodes: List<SearchNode>): Int? =
        findButton(nodes) { node ->
            if (packageName == FIXTURE_PACKAGE) node.id == "search_entry"
            else node.id in entryIds || node.labels.any { key(it) in entryLabels }
        }

    fun findInput(packageName: String, nodes: List<SearchNode>, afterEntry: Boolean): Int? {
        val fields = nodes.indices.filter { index ->
            val node = nodes[index]
            node.enabled && node.visible && node.screen.editable && node.canSetText
        }
        if (packageName == FIXTURE_PACKAGE) {
            return fields.singleOrNull { nodes[it].id == "search_input" }
        }
        val named = fields.filter { index ->
            val node = nodes[index]
            node.id in inputIds || node.labels.any { key(it) in entryLabels }
        }
        if (named.isNotEmpty()) return named.singleOrNull()
        // An otherwise unnamed field is safe only after an identified search entry was activated.
        return if (afterEntry) fields.singleOrNull() else null
    }

    fun findSubmit(packageName: String, nodes: List<SearchNode>): Int? =
        findButton(nodes) { node ->
            if (packageName == FIXTURE_PACKAGE) node.id == "search_submit"
            else node.id == "search_submit" || node.labels.any { key(it) in submitLabels }
        }

    // Only a result surface with independent query evidence counts. A typed query and
    // matching autocomplete suggestions alone are never sufficient.
    fun resultSignature(packageName: String, nodes: List<SearchNode>, query: String): String? {
        val expected = displayText(query)
        if (packageName == FIXTURE_PACKAGE) {
            return nodes.singleOrNull { node ->
                node.visible && !node.screen.editable && node.id == "search_results" &&
                    displayText(node.screen.text) == displayText("검색 결과: $query")
            }?.screen?.text
        }

        // Observed on NAVER Maps 6.12.1.1. The committed query is in a non-editable
        // result header, not in the autocomplete field. A separate result tab and
        // results container establish arrival at the result screen.
        val headers = nodes.indices.filter { nodes[it].visible && nodes[it].id == "v_search_result_top_bar_component" }
        val committed = nodes.indices.any { index ->
            val node = nodes[index]
            node.visible && !node.screen.editable &&
                (matchesQuery(node.screen.text, expected) || matchesQuery(node.screen.description, expected)) &&
                headers.any { isDescendant(nodes, index, it) }
        }
        val tabs = nodes.indices.filter { nodes[it].visible && nodes[it].id == "v_search_result_tab_component" }
        val placesTab = nodes.indices.any { index ->
            val node = nodes[index]
            node.visible && node.labels.any { key(it) in placesTabLabels } &&
                tabs.any { isDescendant(nodes, index, it) }
        }
        val list = nodes.any { it.visible && it.id == "v_search_result_list_group_component" }
        if (committed && placesTab && list) return "naver-result:" + expected.lowercase(Locale.ROOT)

        val surfaces = nodes.indices.filter { index ->
            val node = nodes[index]
            node.visible && !node.screen.editable && node.id in resultIds
        }
        val resultRows = nodes.indices.filter { index ->
            val node = nodes[index]
            node.visible && !node.screen.editable && !hasEditableAncestor(nodes, index) &&
                (matchesQuery(node.screen.text, expected) || matchesQuery(node.screen.description, expected))
        }
        val match = resultRows.firstOrNull { index ->
            surfaces.any { index == it || isDescendant(nodes, index, it) }
        }
        if (match != null) {
            val row = nodes[match]
            return "${row.id}:${row.screen.text}:${row.screen.description}"
        }

        // A screen-reader summary is also sufficient when it explicitly names the result and query.
        return nodes.indices.firstNotNullOfOrNull { index ->
            val node = nodes[index]
            if (!node.visible || node.screen.editable || hasEditableAncestor(nodes, index)) null else
                listOf(node.screen.text, node.screen.description).firstOrNull { value ->
                    val summary = displayText(value)
                    summary == "검색 결과: $expected" || summary == "검색결과: $expected"
                }
        }
    }

    fun displayText(value: String): String = value.trim().replace(whitespace, " ")

    private val whitespace = Regex("\\s+")

    private fun matchesQuery(value: String, expected: String): Boolean {
        val normalized = displayText(value)
        return normalized.equals(expected, ignoreCase = true) ||
            normalized.substringBefore(',').trim().equals(expected, ignoreCase = true)
    }

    private fun findButton(nodes: List<SearchNode>, matches: (SearchNode) -> Boolean): Int? {
        val candidates = linkedSetOf<Int>()
        nodes.forEachIndexed { index, node ->
            if (!node.enabled || !node.visible || node.screen.editable || !matches(node)) return@forEachIndexed
            var candidate = index
            repeat(3) {
                val ancestor = nodes.getOrNull(candidate) ?: return@forEachIndexed
                if (ancestor.enabled && ancestor.visible && ancestor.screen.clickable && !ancestor.screen.editable) {
                    candidates.add(candidate)
                    return@forEachIndexed
                }
                candidate = ancestor.parent
            }
        }
        return candidates.singleOrNull()
    }

    private fun hasEditableAncestor(nodes: List<SearchNode>, index: Int): Boolean {
        var parent = nodes[index].parent
        while (parent >= 0) {
            if (nodes[parent].screen.editable) return true
            parent = nodes[parent].parent
        }
        return false
    }

    private fun isDescendant(nodes: List<SearchNode>, index: Int, ancestor: Int): Boolean {
        var parent = nodes[index].parent
        while (parent >= 0) {
            if (parent == ancestor) return true
            parent = nodes[parent].parent
        }
        return false
    }

    private fun key(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
