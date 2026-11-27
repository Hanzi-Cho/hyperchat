package dev.hanzicho.hyperchat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCommandTest {
    @Test fun parsesForegroundSearchAndExplicitResultOpening() {
        assertEquals(ScreenCommand.Search("서울역"), parseScreenCommand("/search 서울역"))
        assertEquals(ScreenCommand.Search("서울역"), parseScreenCommand("서울역을 검색해 줘"))
        assertEquals(ScreenCommand.Search("테스트 문서"), parseScreenCommand("테스트 문서 찾아줘"))
        assertEquals(
            ScreenCommand.Search("달빛 정원", ResultSelection.FIRST),
            parseScreenCommand("현재 앱에서 달빛 정원 검색하고 첫 번째 결과를 열어줘."),
        )
        assertEquals(
            ScreenCommand.Search("테스트 문서", ResultSelection.EXACT),
            parseScreenCommand("테스트 문서 찾아서 열어줘"),
        )
        assertEquals(
            ScreenCommand.Search("Moon Garden", ResultSelection.FIRST),
            parseScreenCommand("Please search for Moon Garden and open the first result."),
        )
    }

    @Test fun parsesOpenTypeAndNavigationAsSeparateActions() {
        assertEquals(ScreenCommand.Open("검색창"), parseScreenCommand("검색창을 열어 줘"))
        assertEquals(ScreenCommand.Open("Downloads"), parseScreenCommand("Open the Downloads, please."))
        assertEquals(ScreenCommand.Open("Search..."), parseScreenCommand("Open Search..."))
        assertEquals(ScreenCommand.Open("Search..."), parseScreenCommand("Open \"Search...\""))
        assertEquals(ScreenCommand.Type("테스트 문서"), parseScreenCommand("검색창에 테스트 문서를 입력해 줘"))
        assertEquals(ScreenCommand.Type("Hello!"), parseScreenCommand("Type \"Hello!\""))
        assertEquals(ScreenCommand.Type("Hello!"), parseScreenCommand("Please type Hello!"))
        assertEquals(ScreenCommand.Navigate(Direction.BACK), parseScreenCommand("뒤로 돌아가 주세요"))
        assertEquals(ScreenCommand.Navigate(Direction.SCROLL_UP), parseScreenCommand("위로 스크롤해 줘"))
        assertEquals(ScreenCommand.Navigate(Direction.SCROLL_DOWN), parseScreenCommand("Scroll down."))
    }

    @Test fun leavesQuestionsForGuidanceAndRejectsUnsupportedOrMalformedCommands() {
        assertNull(parseScreenCommand("서울역을 어떻게 검색해?"))
        assertNull(parseScreenCommand("How can I open Settings?"))
        assertNull(parseScreenCommand("서울역 검색하지 마"))
        assertTrue(parseScreenCommand("계산기 앱 열어줘") is ScreenCommand.Invalid)
        assertTrue(parseScreenCommand("Open the Calculator app") is ScreenCommand.Invalid)
        assertTrue(parseScreenCommand("/search") is ScreenCommand.Invalid)
        assertTrue(parseScreenCommand("/search ${"가".repeat(81)}") is ScreenCommand.Invalid)
        assertTrue(parseScreenCommand("/search 서울역\n추가 입력") is ScreenCommand.Invalid)
    }

    @Test fun validatesOneStructuredPlanAndDecodesOnlyItsPayload() {
        assertEquals(
            ScreenCommand.Search("테스트 문서", ResultSelection.EXACT),
            parseModelScreenCommand("""{"value":"테스트 문서","action":"search_open"}"""),
        )
        assertEquals(
            ScreenCommand.Open("A \"B\""),
            parseModelScreenCommand("""{"action":"open","value":"\u0041 \"B\""}"""),
        )
        assertEquals(
            ScreenCommand.Navigate(Direction.BACK),
            parseModelScreenCommand("""{"action":"back"}"""),
        )
    }

    @Test fun rejectsExtraActionsArgumentsAndInvalidJson() {
        assertNull(parseModelScreenCommand("""{"action":"open","value":"검색창","next":"type"}"""))
        assertNull(parseModelScreenCommand("""{"action":"back","action":"scroll_up"}"""))
        assertNull(parseModelScreenCommand("""{"action":"back","value":"text"}"""))
        assertNull(parseModelScreenCommand("""{"action":"search","value":42}"""))
        assertNull(parseModelScreenCommand("""{"action":"type","value":"\ntext"}"""))
        assertNull(parseModelScreenCommand("""{"action":"search","value":"${"a".repeat(81)}"}"""))
        assertNull(parseModelScreenCommand("""{"action":"launch_app","value":"계산기"}"""))
        assertNull(parseModelScreenCommand("""[{"action":"back"},{"action":"scroll_up"}]"""))
    }

    @Test fun groundsRankingInTheRequestRatherThanInTheQuery() {
        val proposed = ScreenCommand.Search("first result", ResultSelection.EXACT)
        assertEquals(proposed, parseScreenCommand("find first result and open it"))
        assertEquals(proposed, groundModelScreenCommand("find first result and open it", proposed, emptyList()))
        val first = ScreenCommand.Search("달빛", ResultSelection.FIRST)
        assertEquals(first, groundModelScreenCommand("달빛 검색해서 첫 번째 결과를 열어", ScreenCommand.Search("달빛"), emptyList()))
    }

    @Test fun groundsAProposedSearchButtonAsNavigationWithoutSubmittingIt() {
        val request = "도서 검색 버튼으로 이동해 줘"
        val proposal = ScreenCommand.Search("도서 검색")
        assertEquals(ScreenCommand.Open("도서 검색"), groundModelScreenCommand(request, proposal, listOf("도서 검색")))
        assertNull(groundModelScreenCommand(request, proposal, emptyList()))
        assertNull(groundModelScreenCommand(request, ScreenCommand.Type("도서 검색"), listOf("도서 검색")))
        assertNull(groundModelScreenCommand(request, ScreenCommand.Open("삭제"), listOf("삭제")))
    }
}
