package dev.hanzicho.hyperchat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestTest {
    @Test fun explicitSearchRoutesOnlyTheUserQuery() {
        assertEquals(ChatRequest.Search("서울역"), parseRequest("/search 서울역"))
        assertEquals(ChatRequest.Search("서울역"), parseRequest("서울역 검색해 줘"))
    }

    @Test fun aQuestionAboutSearchIsNotAnAction() {
        assertEquals(ChatRequest.Guide("서울역을 어떻게 검색해?"), parseRequest("서울역을 어떻게 검색해?"))
        assertTrue(parseRequest("이 화면에서 검색하려면?") is ChatRequest.Guide)
    }

    @Test fun rejectsEmptyLongAndMultilineCommands() {
        assertTrue(parseRequest("/search") is ChatRequest.Invalid)
        assertTrue(parseRequest("/search " + "가".repeat(81)) is ChatRequest.Invalid)
        assertTrue(parseRequest("/search 서울역\n다른 명령") is ChatRequest.Invalid)
    }
}
