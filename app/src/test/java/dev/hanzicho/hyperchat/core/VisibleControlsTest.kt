package dev.hanzicho.hyperchat.core

import dev.hanzicho.hyperchat.platform.ScreenNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleControlsTest {
    @Test fun readsOnlyNamedButtonsWithoutCollectingTypedTextOrDuplicates() {
        val button = ScreenNode("재생", "음악 재생", "", true, false)
        val editor = ScreenNode("작성 중인 내용", "", "", true, true)
        val title = ScreenNode("노래 제목", "", "", false, false)
        assertEquals(listOf("음악 재생"), visibleControlLabels(listOf(button, editor, title, button)))
        assertTrue(visibleControlLabels(listOf(editor, title)).isEmpty())
    }

    @Test fun matchesAnExplicitReadOnlyRequestAndBoundsTheReply() {
        assertTrue(isControlListRequest("버튼 목록"))
        assertTrue(isControlListRequest(" LIST CONTROLS "))
        assertFalse(isControlListRequest("버튼 목록 열어줘"))
        val buttons = (1..12).map { ScreenNode("버튼 $it", "", "", true, false) }
        assertEquals((1..8).map { "버튼 $it" }, visibleControlLabels(buttons))
    }
}
