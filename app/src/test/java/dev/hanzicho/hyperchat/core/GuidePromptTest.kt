package dev.hanzicho.hyperchat.core

import dev.hanzicho.hyperchat.platform.ScreenNode
import dev.hanzicho.hyperchat.platform.ScreenSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidePromptTest {
    @Test fun attachmentPromptKeepsTheQuestionAndBoundsTheUntrustedExcerpt() {
        val question = "첨부 문서에서 이름을 찾아줘 " + "가".repeat(470)
        val prompt = attachmentPrompt(question, "문서 이름\nOrbit Lantern\u0000" + "나".repeat(1200), false)

        assertTrue(prompt.startsWith("Question: $question"))
        assertTrue(prompt.contains("문서 이름 Orbit Lantern"))
        assertTrue(prompt.none { it == '\u0000' })
        assertTrue(prompt.endsWith("Attachment excerpt only."))
        assertTrue(prompt.length <= 1200)
    }

    @Test fun crowdedScreenKeepsTheQuestionAndSearchLabelWithinModelBudget() {
        val screen = ScreenSnapshot(
            packageName = "com.nhn.android.nmap",
            windowId = 3,
            nodes = (1..200).map { ScreenNode("장소 " + it + "가".repeat(120), "", "", true, false) } +
                ScreenNode("장소 검색", "검색창", "", true, false),
            events = listOf("[OBSERVE] 접근성 화면 확인"),
        )
        val question = "검색 위치를 알려줘 " + "가".repeat(470)
        val prompt = guidePrompt(question, screen)
        assertTrue(prompt.length <= 1200)
        assertTrue(prompt.contains("장소 검색"))
        assertTrue(prompt.startsWith("Question: $question\n"))
    }
}
