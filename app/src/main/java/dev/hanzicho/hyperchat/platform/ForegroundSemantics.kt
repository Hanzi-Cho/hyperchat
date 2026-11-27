package dev.hanzicho.hyperchat.platform

import dev.hanzicho.hyperchat.core.ResultSelection
import java.util.Locale

/** Selects foreground controls from the live tree, independently of the package name. */
internal object ForegroundSemantics {
    fun searchEntry(nodes: List<SearchNode>): Int? = unique(nodes) { node ->
        if (!button(node) || node.checkable || !searchControl(node)) return@unique 0
        when {
            node.labels.any { key(it) in searchLabels } -> 2
            node.id in searchEntryIds || node.screen.viewId == "$FIXTURE_PACKAGE:id/catalog_entry" -> 1
            else -> 0
        }
    }

    fun searchField(nodes: List<SearchNode>, afterEntry: Boolean): Int? = unique(nodes) { node ->
        if (!node.visible || !node.enabled || !node.screen.editable || !node.canSetText) return@unique 0
        when {
            // An editable value may be the previous query. Use the field's hint, ID and role.
            searchControl(node, listOf(node.hint, node.screen.description)) -> 2
            node.screen.viewId == "$FIXTURE_PACKAGE:id/catalog_title_field" -> 2
            afterEntry && node.searchEditor -> 1
            else -> 0
        }
    }

    fun searchSubmit(nodes: List<SearchNode>): Int? = unique(nodes) { node ->
        if (!button(node) || node.checkable) return@unique 0
        when {
            key(node.id) in setOf("searchsubmit", "submitsearch") -> 3
            !searchControl(node) -> 0
            node.labels.any { key(it) in submitLabels } -> 2
            else -> 0
        }
    }

    data class Destination(val index: Int, val title: String)

    fun hasMatchingControl(nodes: List<SearchNode>, label: String) = matchingButtons(nodes, label, false).isNotEmpty()

    fun openTarget(pkg: String, nodes: List<SearchNode>, label: String): Int? {
        if (pkg == aimp) {
            if (!isAimpMenuRequest(pkg, label)) return null
            return nodes.indices.singleOrNull { i ->
                val node = nodes[i]
                node.className == aimpButton && button(node) && !node.checkable &&
                    node.screen.description == "Main menu" &&
                    nodes.getOrNull(node.parent)?.className == "android.view.ViewGroup" &&
                    aimpMenuSiblings.all { name -> nodes.any { sibling ->
                        sibling.parent == node.parent && sibling.className == aimpButton &&
                            sibling.visible && sibling.screen.description == name
                    } }
            }
        }
        if (pkg == commander && key(label) in searchLabels) {
            return nodes.indices.singleOrNull { i ->
                val node = nodes[i]
                button(node) && !node.checkable && node.className == "android.widget.Button" &&
                    node.screen.viewId == "$commander:id/search" && node.labels.any { key(it) == key(label) } &&
                    node.parent >= 0 && nodes.any { sibling -> sibling.visible && sibling.parent == node.parent &&
                        sibling.screen.viewId in commanderToolbarIds }
            }
        }
        val index = matchingButtons(nodes, label, false).singleOrNull() ?: return null
        val node = nodes[index]
        // Material bottom navigation may expose its current tab as a checkable menu item.
        if (isVlcNavigation(pkg, nodes, index) || isAnkiCardBrowser(pkg, nodes, index)) return index
        if (hasToggle(nodes, index)) return null
        val id = node.screen.viewId
        val isSearchEntry = searchEntry(nodes) == index && searchField(nodes, false) == null &&
            (id == "$FIXTURE_PACKAGE:id/catalog_entry" && pkg == FIXTURE_PACKAGE ||
                node.id in searchEntryIds && node.labels.any { key(it) in searchLabels })
        if (isSearchEntry || isTab(nodes, index)) return index
        // The Android Settings landing-page row uses android:id/title for the category name.
        if (pkg == "com.android.settings" && key(label) in displayLabels && nodes.indices.any { child ->
                nodes[child].screen.viewId == "android:id/title" && nodes[child].labels.any { matches(it, label) } &&
                    index in lineage(nodes, child)
            }) return index
        return null
    }

    fun backButton(nodes: List<SearchNode>): Int? = listOf("뒤로", "뒤로 이동", "위로 이동", "Navigate up", "Back")
        .flatMap { matchingButtons(nodes, it, false) }.distinct().singleOrNull()?.takeIf { i ->
            !hasToggle(nodes, i) && (nodes[i].id in backIds || lineage(nodes, i).any { nodes[it].id in toolbarIds })
        }

    private fun hasToggle(nodes: List<SearchNode>, index: Int): Boolean = nodes.indices.any { i ->
        nodes[i].checkable && nodes[i].visible && index in lineage(nodes, i)
    }

    private fun isTab(nodes: List<SearchNode>, index: Int): Boolean {
        val node = nodes[index]
        return node.className == "android.app.ActionBar\$Tab" ||
            (nodes.getOrNull(node.parent)?.className == "android.widget.TabWidget" && node.collectionRow != null)
    }

    private fun isVlcNavigation(pkg: String, nodes: List<SearchNode>, index: Int): Boolean {
        if (pkg != "org.videolan.vlc" || nodes[index].screen.viewId !in vlcTabIds) return false
        val parent = nodes[index].parent
        return parent >= 0 && nodes.any { sibling -> sibling.parent == parent && sibling.visible &&
            sibling.screen.viewId != nodes[index].screen.viewId && sibling.screen.viewId in vlcTabIds }
    }

    private fun isAnkiCardBrowser(pkg: String, nodes: List<SearchNode>, index: Int): Boolean {
        if (pkg != anki || nodes[index].screen.viewId != "$anki:id/nav_browser" ||
            nodes[index].className != "androidx.appcompat.widget.LinearLayoutCompat") return false
        val parent = nodes[index].parent
        return parent >= 0 && nodes.any { sibling -> sibling.parent == parent && sibling.visible &&
            sibling.screen.viewId in ankiDrawerIds } && nodes.any { child -> child.parent == index &&
            child.visible && child.className == "android.widget.CheckedTextView" &&
            child.screen.viewId == "$anki:id/design_menu_item_text" }
    }

    fun resultTarget(nodes: List<SearchNode>, query: String): Int? = matchingButtons(nodes, query, true).singleOrNull()

    /** Only an identified read-only result surface can authorize a compound search/open. */
    fun resultDestination(pkg: String, nodes: List<SearchNode>, query: String, selection: ResultSelection): Destination? {
        if (selection == ResultSelection.NONE) return null
        // CatalogActivity places its result buttons immediately after this heading in original sibling order.
        if (pkg == FIXTURE_PACKAGE) {
            val heading = nodes.indices.singleOrNull { nodes[it].visible &&
                nodes[it].screen.viewId == "$FIXTURE_PACKAGE:id/catalog_matches" } ?: return null
            val rows = nodes.indices.filter { it > heading && nodes[it].parent == nodes[heading].parent }
            val row = when (selection) {
                ResultSelection.FIRST -> rows.firstOrNull()
                ResultSelection.EXACT -> rows.singleOrNull { sameTitle(nodes[it].screen.text, query) }
                ResultSelection.NONE -> null
            } ?: return null
            val node = nodes[row]
            if (node.className != "android.widget.Button" || !button(node) || hasToggle(nodes, row) ||
                !node.screen.text.contains(query, ignoreCase = true)) return null
            return Destination(row, node.screen.text)
        }
        // For other apps, a collection index establishes absolute rank. The destination's own
        // resource must identify a detail link, not a playback, purchase, or other row action.
        val surface = nodes.indices.singleOrNull { i -> nodes[i].visible &&
            nodes[i].screen.viewId.startsWith("$pkg:id/") && nodes[i].id in resultListIds } ?: return null
        val rows = nodes.indices.filter { i -> nodes[i].collectionRow != null && surface in lineage(nodes, i).drop(1) }
        val row = when (selection) {
            ResultSelection.FIRST -> rows.singleOrNull { nodes[it].collectionRow == 0 }
                ?.takeIf { nodes[it].collectionColumn == 0 }
            ResultSelection.EXACT -> rows.singleOrNull { i -> nodes[i].labels.any { sameTitle(it, query) } }
            ResultSelection.NONE -> null
        } ?: return null
        val node = nodes[row]
        if (!button(node) || hasToggle(nodes, row) || !node.screen.viewId.startsWith("$pkg:id/") ||
            node.id !in detailLinkIds) return null
        val title = node.screen.text.ifBlank { node.screen.description }
        // A generic header can update before the results do. Without a per-app result/query
        // contract, unrelated titles remain for manual selection even when rank is known.
        if (title.isBlank() || !title.contains(query, ignoreCase = true)) return null
        return Destination(row, title)
    }

    fun tabSelected(pkg: String, nodes: List<SearchNode>, label: String): Boolean {
        // Some tabs cease to advertise ACTION_CLICK as soon as they become selected.
        return nodes.indices.any { child ->
            nodes[child].visible && nodes[child].labels.any { matches(it, label) } &&
                lineage(nodes, child).any { i -> nodes[i].visible && (nodes[i].selected || nodes[i].checked) &&
                    (isVlcNavigation(pkg, nodes, i) || isTab(nodes, i)) }
        }
    }

    fun searchFormVisible(pkg: String, nodes: List<SearchNode>, label: String): Boolean =
        pkg == commander && key(label) in searchLabels &&
            nodes.any { it.visible && it.screen.editable && it.screen.viewId == "$commander:id/name" } &&
            nodes.any { button(it) && it.screen.viewId == "$commander:id/okbtn" }

    fun cardBrowserVisible(pkg: String, nodes: List<SearchNode>, sourceId: String): Boolean =
        pkg == anki && sourceId == "$anki:id/nav_browser" &&
            nodes.any { it.visible && it.screen.viewId == "$anki:id/subtitle" } &&
            nodes.any { button(it) && it.screen.viewId == "$anki:id/action_add_note_from_card_browser" } &&
            nodes.any { button(it) && it.screen.viewId == "$anki:id/action_search" }

    fun isAimpMenuRequest(pkg: String, label: String) = pkg == aimp && sameTitle(label, "Main menu")

    fun requiresVisibleOpenTarget(pkg: String) = pkg == aimp

    fun aimpMenuVisible(pkg: String, nodes: List<SearchNode>, label: String): Boolean =
        isAimpMenuRequest(pkg, label) &&
            nodes.any { it.visible && it.className == "android.view.View" && it.screen.description == "Queue" } &&
            nodes.any { it.visible && it.className == aimpButton && it.screen.description == "Managing playlists" }

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
        generateSequence(index) { nodes[it].parent.takeIf { parent -> parent in 0 until it } }

    private fun matches(label: String, target: String): Boolean {
        return label.isNotBlank() && (sameTitle(label, target) ||
            (label.substringAfterLast('.', "").lowercase(Locale.ROOT) in fileExtensions &&
                sameTitle(label.substringBeforeLast('.'), target)))
    }

    private fun sameTitle(label: String, target: String) = label.isNotBlank() &&
        SearchSemantics.displayText(label).equals(SearchSemantics.displayText(target), ignoreCase = true)

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
    private val searchEntryIds = setOf("search", "search_bar", "search_entry", "search_button", "action_search", "search_box")
    private val displayLabels = setOf("display", "displaytouch", "디스플레이", "디스플레이및터치")
    private val backIds = setOf("back", "navigate_up", "up", "action_back")
    private val toolbarIds = setOf("toolbar", "action_bar", "top_app_bar")
    private val resultListIds = setOf("search_results", "search_result_list", "search_results_list")
    private val detailLinkIds = setOf("detail_link", "open_details", "view_details")
    private val vlcTabIds = setOf("nav_video", "nav_audio", "nav_directories", "nav_playlists", "nav_more")
        .mapTo(mutableSetOf()) { "org.videolan.vlc:id/$it" }
    private const val commander = "com.ghisler.android.TotalCommander"
    private val commanderToolbarIds = setOf("$commander:id/history", "$commander:id/bookmarks")
    private const val anki = "com.ichi2.anki"
    private val ankiDrawerIds = setOf("$anki:id/nav_decks", "$anki:id/nav_stats", "$anki:id/nav_settings")
    private const val aimp = "com.aimp.player"
    private const val aimpButton = "com.aimp.skinengine.controls.SkinnedButton"
    private val aimpMenuSiblings = listOf("Sound Effects", "Sleep Timer")
}
