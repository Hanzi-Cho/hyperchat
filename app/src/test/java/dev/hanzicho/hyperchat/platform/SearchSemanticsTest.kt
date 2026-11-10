package dev.hanzicho.hyperchat.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchSemanticsTest {
    @Test
    fun duplicateSearchTargetsAreRejected() {
        val nodes = listOf(
            node(text = "검색", clickable = true),
            node(description = "검색하기", clickable = true),
        )

        assertNull(SearchSemantics.findEntry(NAVER_MAP_PACKAGE, nodes))
        assertNull(SearchSemantics.findSubmit(NAVER_MAP_PACKAGE, nodes))
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
