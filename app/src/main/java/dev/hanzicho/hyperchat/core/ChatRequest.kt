package dev.hanzicho.hyperchat.core

sealed interface ChatRequest {
    data class Guide(val question: String) : ChatRequest
    data class Search(val query: String) : ChatRequest
    data class Invalid(val reason: String) : ChatRequest
}

private val searchInKorean = Regex("""^(.+?)\s+검색\s*(?:해\s*줘|해\s*주세요|해)$""")

fun parseRequest(input: String): ChatRequest {
    val text = input.trim()
    if (text.isEmpty() || text.length > 500 || text.any { it.isISOControl() }) {
        return ChatRequest.Invalid("한 줄로 1~500자 이내의 요청을 입력해 주세요.")
    }
    if (text == "/search" || text.startsWith("/search ")) {
        return searchRequest(text.removePrefix("/search").trim())
    }
    val match = searchInKorean.matchEntire(text)
    return if (match != null) searchRequest(match.groupValues[1].trim()) else ChatRequest.Guide(text)
}

private fun searchRequest(query: String): ChatRequest =
    if (query.length in 1..80) ChatRequest.Search(query)
    else ChatRequest.Invalid("검색어는 1~80자여야 합니다.")
