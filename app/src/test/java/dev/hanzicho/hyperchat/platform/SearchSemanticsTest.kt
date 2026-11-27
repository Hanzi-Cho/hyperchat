package dev.hanzicho.hyperchat.platform

import dev.hanzicho.hyperchat.core.ResultSelection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSemanticsTest {
    @Test
    fun genericSearchDistinguishesSearchFromUnrelatedAndMutatingControls() {
        val research = node(text = "Research", clickable = true)
        val save = node(text = "Save search", clickable = true)
        val search = node(id = "search_entry", text = "도서 검색", clickable = true)
        assertNull(ForegroundSemantics.searchEntry(listOf(research, save)))
        assertNull(ForegroundSemantics.searchEntry(listOf(node(text = "검색 광고 구매", clickable = true))))
        assertEquals(2, ForegroundSemantics.searchEntry(listOf(research, save, search)))
        assertNull(ForegroundSemantics.searchEntry(listOf(search, search)))
    }

    @Test
    fun genericResultRequiresEvidenceBeyondTheInputOrAutocomplete() {
        val field = node(id = "search_input", text = "달빛 정원", editable = true)
        val suggestionList = node(id = "search_suggestions")
        val suggestion = node(text = "달빛 정원", clickable = true, parent = 1)
        val result = node(text = "달빛 정원", clickable = true)

        assertNull(ForegroundSemantics.resultSignature(listOf(field, suggestionList, suggestion), "달빛 정원"))
        assertNull(ForegroundSemantics.resultSignature(listOf(field, result), "달빛 정원"))
        assertNotNull(ForegroundSemantics.resultSignature(listOf(result), "달빛 정원"))
        assertNull(ForegroundSemantics.resultTarget(listOf(result, result), "달빛 정원"))
    }

    @Test
    fun duplicateSearchTargetsAreRejected() {
        val nodes = listOf(
            node(text = "검색", clickable = true),
            node(description = "검색하기", clickable = true),
        )

        assertNull(SearchSemantics.findEntry(NAVER_MAP_PACKAGE, nodes))
        assertNull(SearchSemantics.findSubmit(NAVER_MAP_PACKAGE, nodes))
    }

    @Test fun soleEditorAfterSearchEntryRequiresItsOwnSearchEvidence() {
        val email = node(id = "account_email", text = "Search", editable = true)
            .copy(hint = "Email", screen = ScreenNode("Search", "", "account_email", false, true, focused = true))
        val ui = listOf(email, node(text = "Search", clickable = true))
        assertNull(SearchSemantics.findInput(NAVER_MAP_PACKAGE, ui, afterEntry = true))
        assertNull(ForegroundSemantics.searchField(ui, afterEntry = true))
        assertEquals(0, SearchSemantics.findInput(NAVER_MAP_PACKAGE, listOf(email.copy(searchEditor = true)), true))
    }

    @Test
    fun fixtureRequiresItsOwnVisibleResultForTheQuery() {
        val input = node(id = "search_input", text = "서울역", editable = true)
        val result = node(id = "search_results", text = "검색 결과: 서울역")

        assertEquals(0, SearchSemantics.findInput(FIXTURE_PACKAGE, listOf(input), false))
        assertNull(SearchSemantics.resultSignature(FIXTURE_PACKAGE, listOf(input), "서울역"))
        assertNull(SearchSemantics.resultSignature(FIXTURE_PACKAGE, listOf(result.copy(visible = false)), "서울역"))
        assertNull(SearchSemantics.resultSignature(FIXTURE_PACKAGE, listOf(result), "부산역"))
        assertEquals("검색 결과: 서울역", SearchSemantics.resultSignature(FIXTURE_PACKAGE, listOf(result), "서울역"))
    }

    @Test
    fun matchingAutocompleteTextRequiresIndependentResultSurface() {
        val input = node(id = "search_input", text = "서울역", editable = true)
        val autocomplete = node(id = "search_suggestions")
        val suggestion = node(text = "서울역", parent = 1)

        assertNull(SearchSemantics.resultSignature(NAVER_MAP_PACKAGE, listOf(input, autocomplete, suggestion), "서울역"))

        val results = node(id = "search_result_list")
        assertNotNull(SearchSemantics.resultSignature(NAVER_MAP_PACKAGE, listOf(input, results, suggestion), "서울역"))
    }

    @Test
    fun naverResultRequiresTheCommittedQueryTabAndList() {
        val nodes = listOf(
            node(id = "v_search_result_top_bar_component"),
            node(text = "Seoul Station", parent = 0),
            node(id = "v_search_result_tab_component"),
            node(description = "Places, tab", parent = 2),
            node(id = "v_search_result_list_group_component"),
        )

        assertNull(SearchSemantics.resultSignature(NAVER_MAP_PACKAGE, nodes.dropLast(1), "Seoul Station"))
        assertNull(SearchSemantics.resultSignature(NAVER_MAP_PACKAGE, nodes, "Busan Station"))
        assertEquals("naver-result:seoul station", SearchSemantics.resultSignature(NAVER_MAP_PACKAGE, nodes, "Seoul Station"))
    }

    @Test fun catalogFirstAndExactUseDifferentEvidence() {
        val nodes = listOf(node(), node(id = "$FIXTURE_PACKAGE:id/catalog_matches", parent = 0),
            node(text = "달빛 정원 산책", clickable = true, parent = 0).copy(className = "android.widget.Button"),
            node(text = "달빛 정원", clickable = true, parent = 0).copy(className = "android.widget.Button"))
        assertEquals(2, ForegroundSemantics.resultDestination(FIXTURE_PACKAGE, nodes, "달빛 정원", ResultSelection.FIRST)?.index)
        assertEquals(2, ForegroundSemantics.resultDestination(FIXTURE_PACKAGE, nodes, "달빛", ResultSelection.FIRST)?.index)
        assertEquals(3, ForegroundSemantics.resultDestination(FIXTURE_PACKAGE, nodes, "달빛 정원", ResultSelection.EXACT)?.index)
        assertNull(ForegroundSemantics.resultDestination(FIXTURE_PACKAGE, nodes, "달빛", ResultSelection.EXACT))
        assertNull(ForegroundSemantics.resultDestination("unrelated.app", nodes, "달빛", ResultSelection.FIRST))
        val hiddenTop = nodes.toMutableList().apply { this[2] = this[2].copy(visible = false) }
        assertNull(ForegroundSemantics.resultDestination(FIXTURE_PACKAGE, hiddenTop, "달빛", ResultSelection.FIRST))
    }

    @Test fun genericRankRequiresUniqueAbsoluteFirstDetailInTheResultList() {
        val pkg = "catalog.example"
        val list = node(id = "$pkg:id/search_results")
        val second = node(id = "$pkg:id/detail_link", text = "Moon A", clickable = true, parent = 0).copy(collectionRow = 1)
        val first = node(id = "$pkg:id/detail_link", text = "Moon Z", clickable = true, parent = 0)
            .copy(collectionRow = 0, collectionColumn = 0)
        fun firstOf(vararg rows: SearchNode) = ForegroundSemantics.resultDestination(pkg, listOf(list, *rows), "Moon", ResultSelection.FIRST)
        assertEquals("Moon Z", firstOf(second, first)?.title)
        assertNull(firstOf(second))
        assertNull(firstOf(first, first))
        assertNull(firstOf(first.copy(collectionColumn = 1)))
        assertNull(firstOf(first.copy(checkable = true)))
        assertNull(firstOf(first.copy(screen = first.screen.copy(viewId = "$pkg:id/play"))))
        val indirect = first.copy(screen = first.screen.copy(text = "Astronomy"))
        assertNull(firstOf(indirect))
        assertNull(firstOf(indirect, node(id = "$pkg:id/committed_query", text = "Moon")))
        assertNull(firstOf(indirect, node(id = "$pkg:id/committed_query", text = "Moon", editable = true)))
    }

    @Test fun navigationRequiresAPositiveRoleAndRejectsControlsWithUnknownEffects() {
        val ordinary = node(text = "Continue", clickable = true)
        val tab = ordinary.copy(className = "android.app.ActionBar\$Tab")
        assertNull(ForegroundSemantics.openTarget("example", listOf(ordinary), "Continue"))
        assertEquals(0, ForegroundSemantics.openTarget("example", listOf(tab), "Continue"))
        assertNull(ForegroundSemantics.openTarget("example", listOf(tab.copy(checkable = true)), "Continue"))
        assertNull(ForegroundSemantics.openTarget("example", listOf(tab, tab), "Continue"))
        assertNull(ForegroundSemantics.openTarget("example", listOf(ordinary, node(text = "Display")), "Display"))
        val strip = node().copy(className = "android.widget.TabWidget")
        val close = node(text = "Close", clickable = true, parent = 1)
        val group = listOf(strip, ordinary.copy(parent = 0, collectionRow = 0), close)
        assertEquals(1, ForegroundSemantics.openTarget("example", group, "Continue"))
        assertNull(ForegroundSemantics.openTarget("example", group, "Close"))
    }

    @Test fun vlcTabAndCommanderSearchRequireTheirOwnScreenContracts() {
        val vlc = "org.videolan.vlc"
        val audio = node(id = "$vlc:id/nav_audio", description = "Audio", clickable = true, parent = 0)
        val video = node(id = "$vlc:id/nav_video", description = "Video", clickable = true, parent = 0)
        assertEquals(1, ForegroundSemantics.openTarget(vlc, listOf(node(), audio, video), "Audio"))
        assertNull(ForegroundSemantics.openTarget(vlc, listOf(node(), audio), "Audio"))
        assertNull(ForegroundSemantics.openTarget("other", listOf(node(), audio, video), "Audio"))
        assertEquals(true, ForegroundSemantics.tabSelected(vlc, listOf(node(), audio.copy(selected = true), video), "Audio"))

        val pkg = "com.ghisler.android.TotalCommander"
        val search = node(id = "$pkg:id/search", description = "Search...  ", clickable = true, parent = 0)
            .copy(className = "android.widget.Button")
        val home = listOf(node(), search, node(id = "$pkg:id/bookmarks", parent = 0))
        assertEquals(1, ForegroundSemantics.openTarget(pkg, home, "Search"))
        assertNull(ForegroundSemantics.openTarget(pkg, home.dropLast(1), "Search"))
        val form = listOf(node(id = "$pkg:id/name", editable = true), node(id = "$pkg:id/okbtn", clickable = true))
        assertEquals(true, ForegroundSemantics.searchFormVisible(pkg, form, "Search..."))
        assertEquals(false, ForegroundSemantics.searchFormVisible(pkg, form.dropLast(1), "Search..."))
    }

    @Test fun ankiBrowserDrawerEntryAllowsTheNavigationRowOnly() {
        val pkg = "com.ichi2.anki"
        val browser = node(id = "$pkg:id/nav_browser", clickable = true, parent = 0)
            .copy(className = "androidx.appcompat.widget.LinearLayoutCompat")
        val label = node(id = "$pkg:id/design_menu_item_text", text = "Card browser", parent = 1)
            .copy(className = "android.widget.CheckedTextView", checkable = true)
        val drawer = listOf(node(), browser, label, node(id = "$pkg:id/nav_decks", parent = 0))
        assertEquals(1, ForegroundSemantics.openTarget(pkg, drawer, "Card browser"))
        assertNull(ForegroundSemantics.openTarget(pkg, drawer.dropLast(1), "Card browser"))
        assertNull(ForegroundSemantics.openTarget("other.app", drawer, "Card browser"))
        val page = listOf(node(id = "$pkg:id/subtitle"), node(id = "$pkg:id/action_add_note_from_card_browser", clickable = true),
            node(id = "$pkg:id/action_search", clickable = true))
        assertEquals(true, ForegroundSemantics.cardBrowserVisible(pkg, page, "$pkg:id/nav_browser"))
        assertEquals(false, ForegroundSemantics.cardBrowserVisible(pkg, page.dropLast(1), "$pkg:id/nav_browser"))
    }

    @Test fun aimpSkinButtonsAuthorizeOnlyTheMenuOnItsObservedScreen() {
        val pkg = "com.aimp.player"
        fun skin(label: String) = node(description = label, parent = 0, clickable = true)
            .copy(className = "com.aimp.skinengine.controls.SkinnedButton")
        val home = listOf(node().copy(className = "android.view.ViewGroup"), skin("Main menu"),
            skin("Play"), skin("Voice"), skin("Sound Effects"), skin("Sleep Timer"))
        assertEquals(1, ForegroundSemantics.openTarget(pkg, home, "Main menu"))
        for (other in listOf("Play", "Voice", "Sound Effects", "Sleep Timer")) {
            assertNull(ForegroundSemantics.openTarget(pkg, home, other))
        }
        assertNull(ForegroundSemantics.openTarget(pkg, home.dropLast(1), "Main menu"))
        assertNull(ForegroundSemantics.openTarget("other.app", home, "Main menu"))
        val menu = listOf(node(description = "Queue").copy(className = "android.view.View"), skin("Managing playlists"))
        assertEquals(true, ForegroundSemantics.aimpMenuVisible(pkg, menu, "Main menu"))
        assertEquals(false, ForegroundSemantics.aimpMenuVisible(pkg, menu.drop(1), "Main menu"))
        assertEquals(false, ForegroundSemantics.aimpMenuVisible(pkg, menu, "Play"))
    }

    private fun node(
        id: String = "",
        text: String = "",
        description: String = "",
        parent: Int = -1,
        clickable: Boolean = false,
        editable: Boolean = false,
    ) = SearchNode(
        screen = ScreenNode(text, description, id, clickable, editable),
        parent = parent,
        hint = "",
        enabled = true,
        visible = true,
        showingHint = false,
        canSetText = editable,
    )
}
