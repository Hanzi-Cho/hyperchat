package dev.hanzicho.hyperchat.core

import java.util.Locale

sealed interface ScreenCommand {
    data class Search(val query: String, val openResult: Boolean = false) : ScreenCommand
    data class Open(val label: String) : ScreenCommand
    data class Type(val text: String) : ScreenCommand
    data class Navigate(val direction: Direction) : ScreenCommand
    data class Invalid(val reason: String) : ScreenCommand
}

enum class Direction { BACK, SCROLL_UP, SCROLL_DOWN }

private const val MAX_INPUT = 500
private const val MAX_TARGET = 80
private const val INVALID_INPUT = "한 줄로 1~500자 이내의 요청을 입력해 주세요."
private const val OTHER_APP = "현재 화면 안에서만 조작할 수 있어요. 먼저 대상 앱을 열어 주세요."

private val foregroundPrefix = Regex(
    """^(?:(?:이|현재|지금)\s*(?:앱|화면)에서\s*|in (?:this|the current) app(?:,\s*|\s+))""",
    RegexOption.IGNORE_CASE,
)
private val appCommand = Regex(
    """(?:.+\s+)?앱[을를]?\s+(?:열어|찾아)\s*(?:줘|주세요)?|(?:open|find)\s+(?:the\s+)?(?:.+\s+)?app""",
    RegexOption.IGNORE_CASE,
)
private val leadingPlease = Regex("""^please\s+""", RegexOption.IGNORE_CASE)
private val trailingPlease = Regex(""",?\s+please$""", RegexOption.IGNORE_CASE)
private val searchThenOpen = listOf(
    Regex("""(.+?)[을를]?\s+(?:검색\s*(?:하고|해서|한\s*(?:뒤|후|다음)에?)|찾아서)\s*(?:(?:첫\s*(?:번째\s*)?|1\s*번째\s*)결과[를을]?\s*)?열어\s*(?:줘|주세요)?"""),
    Regex("""(?:search(?:\s+for)?|find)\s+(.+?)\s+and\s+open\s+(?:(?:the\s+)?first\s+result|it)""", RegexOption.IGNORE_CASE),
)
private val searchCommand = listOf(
    Regex("""(.+?)[을를]?\s+(?:검색\s*해|찾아)\s*(?:줘|주세요)?"""),
    Regex("""(?:search(?:\s+for)?|find)\s+(.+)""", RegexOption.IGNORE_CASE),
)
private val openCommand = listOf(
    Regex("""(.+?)[을를]?\s+열어\s*(?:줘|주세요)?"""),
    Regex("""open\s+(?:the\s+)?(.+)""", RegexOption.IGNORE_CASE),
)
private val koreanTypeCommand = Regex(
    """(?:(?:검색창|검색\s*칸|입력란|이\s*칸)에\s+)?(.+?)[을를]?\s+(?:입력\s*해|써)\s*(?:줘|주세요)?""",
)
private val englishTypeCommand = Regex("""type\s+(.+)""", RegexOption.IGNORE_CASE)
private val backCommand = Regex("""뒤로(?:\s*(?:가|돌아가)\s*(?:줘|주세요)?)?|(?:go\s+)?back""", RegexOption.IGNORE_CASE)
private val scrollUpCommand = Regex(
    """위로\s*(?:(?:스크롤(?:\s*해)?|올려)\s*(?:줘|주세요)?)?|scroll\s+up""",
    RegexOption.IGNORE_CASE,
)
private val scrollDownCommand = Regex(
    """아래로\s*(?:(?:스크롤(?:\s*해)?|내려)\s*(?:줘|주세요)?)?|scroll\s+down""",
    RegexOption.IGNORE_CASE,
)

/** Returns null for a question or other input that does not match a supported command. */
fun parseScreenCommand(input: String): ScreenCommand? {
    val text = input.trim()
    if (text.isEmpty() || text.length > MAX_INPUT || input.any(Char::isISOControl)) {
        return ScreenCommand.Invalid(INVALID_INPUT)
    }
    if (text == "/search" || text.startsWith("/search ")) {
        return when (val legacy = parseRequest(text)) {
            is ChatRequest.Search -> ScreenCommand.Search(legacy.query)
            is ChatRequest.Invalid -> ScreenCommand.Invalid(legacy.reason)
            is ChatRequest.Guide -> null
        }
    }

    val phrase = foregroundPrefix.replaceFirst(text, "").replaceFirst(leadingPlease, "")
    englishTypeCommand.capture(phrase)?.let { return type(it) }
    val command = phrase.trimEnd('.', '!').trimEnd().replace(trailingPlease, "")
    if (appCommand.matches(command)) return ScreenCommand.Invalid(OTHER_APP)
    searchThenOpen.capture(command)?.let { return search(it, openResult = true) }
    searchCommand.capture(command)?.let { return search(it) }
    openCommand.capture(command)?.let { return open(it) }
    koreanTypeCommand.capture(command)?.let { return type(it) }
    return when {
        backCommand.matches(command) -> ScreenCommand.Navigate(Direction.BACK)
        scrollUpCommand.matches(command) -> ScreenCommand.Navigate(Direction.SCROLL_UP)
        scrollDownCommand.matches(command) -> ScreenCommand.Navigate(Direction.SCROLL_DOWN)
        else -> null
    }
}

private fun List<Regex>.capture(command: String): String? = firstNotNullOfOrNull { it.capture(command) }

private fun Regex.capture(command: String): String? =
    matchEntire(command)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")

private fun search(query: String, openResult: Boolean = false): ScreenCommand =
    if (validValue(query, MAX_TARGET)) ScreenCommand.Search(query, openResult)
    else ScreenCommand.Invalid("검색어는 1~80자여야 합니다.")

private fun open(label: String): ScreenCommand =
    if (validValue(label, MAX_TARGET)) ScreenCommand.Open(label)
    else ScreenCommand.Invalid("열려는 항목 이름은 1~80자여야 합니다.")

private fun type(text: String): ScreenCommand =
    if (validValue(text, MAX_INPUT)) ScreenCommand.Type(text)
    else ScreenCommand.Invalid("입력할 텍스트는 한 줄로 1~500자여야 합니다.")

private fun validValue(value: String, limit: Int): Boolean =
    value.isNotBlank() && value.length <= limit && value.none(Char::isISOControl)

// A plan is exactly one flat object with one or two string properties.
private val jsonString = "\"" + """((?:[^"\\\x00-\x1f]++|\\(?:["\\/bfnrt]|u[0-9A-Fa-f]{4}))*+)""" + "\""
private val jsonCommand = Regex("""\{\s*$jsonString\s*:\s*$jsonString(?:\s*,\s*$jsonString\s*:\s*$jsonString)?\s*\}""")
private val jsonEscape = Regex("""\\(?:u[0-9A-Fa-f]{4}|["\\/bfnrt])""")

/** Validates plan shape and value bounds. The caller checks evidence and confirms execution. */
fun parseModelScreenCommand(json: String): ScreenCommand? {
    if (json.length > 1_200) return null
    val match = jsonCommand.matchEntire(json.trim()) ?: return null
    val (firstKey, firstValue, secondKey, secondValue) = match.destructured
    val fields = mutableMapOf(firstKey to firstValue)
    if (match.groups[3] != null && fields.put(secondKey, secondValue) != null) return null
    if (fields.keys.any { it != "action" && it != "value" }) return null
    val decoded = fields["value"]?.let(::decodeJsonString).orEmpty()
    if (decoded.any(Char::isISOControl)) return null
    val value = decoded.trim()
    val result = when (fields["action"]?.lowercase(Locale.ROOT)) {
        "search" -> search(value)
        "search_open" -> search(value, openResult = true)
        "open" -> open(value)
        "type" -> type(value)
        "back" -> ScreenCommand.Navigate(Direction.BACK).takeIf { "value" !in fields }
        "scroll_up" -> ScreenCommand.Navigate(Direction.SCROLL_UP).takeIf { "value" !in fields }
        "scroll_down" -> ScreenCommand.Navigate(Direction.SCROLL_DOWN).takeIf { "value" !in fields }
        else -> null
    }
    return result?.takeUnless { it is ScreenCommand.Invalid }
}

/** Bind the proposal to both the user's wording and the current labels before previewing it. */
internal fun groundModelScreenCommand(
    request: String,
    proposal: ScreenCommand,
    visibleLabels: List<String>,
): ScreenCommand? {
    val value = when (proposal) {
        is ScreenCommand.Search -> proposal.query
        is ScreenCommand.Open -> proposal.label
        is ScreenCommand.Type -> proposal.text
        else -> return null
    }
    if (!request.contains(value, ignoreCase = true)) return null
    val intent = request.replace(value, " ", ignoreCase = true).lowercase(Locale.ROOT)
    val wantsOpen = openIntent.containsMatchIn(intent)
    val visibleTarget = visibleLabels.firstOrNull { it.equals(value, ignoreCase = true) }
    return when (proposal) {
        is ScreenCommand.Search -> when {
            wantsOpen && visibleTarget != null -> ScreenCommand.Open(visibleTarget)
            searchIntent.containsMatchIn(intent) -> proposal.copy(openResult = proposal.openResult && wantsOpen)
            else -> null
        }
        is ScreenCommand.Open -> proposal.takeIf { wantsOpen }
        is ScreenCommand.Type -> proposal.takeIf { typeIntent.containsMatchIn(intent) }
        else -> null
    }
}

private val openIntent = Regex("열어|눌러|선택|이동|들어가|\\b(?:open|tap|click|go to|take me)\\b")
private val searchIntent = Regex("검색|찾아|\\b(?:search|find|look up)\\b")
private val typeIntent = Regex("입력|써 줘|써줘|\\b(?:type|enter|write)\\b")

private fun decodeJsonString(value: String): String = jsonEscape.replace(value) { escape ->
    when (val code = escape.value[1]) {
        'u' -> escape.value.substring(2).toInt(16).toChar().toString()
        'b' -> "\b"
        'f' -> "\u000c"
        'n' -> "\n"
        'r' -> "\r"
        't' -> "\t"
        else -> code.toString()
    }
}
