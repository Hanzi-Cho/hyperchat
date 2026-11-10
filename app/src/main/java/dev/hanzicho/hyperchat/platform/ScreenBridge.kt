package dev.hanzicho.hyperchat.platform

interface ScreenBridge {
    fun capture(): ScreenSnapshot?
    suspend fun search(query: String, expected: ScreenSnapshot): SearchResult
}

data class ScreenSnapshot(
    val packageName: String,
    val windowId: Int,
    val nodes: List<ScreenNode>,
    val events: List<String>,
)

data class ScreenNode(
    val text: String,
    val description: String,
    val viewId: String,
    val clickable: Boolean,
    val editable: Boolean,
)

enum class SearchOutcome { STOPPED, SUBMITTED, VERIFIED }

data class SearchResult(val outcome: SearchOutcome, val message: String)
