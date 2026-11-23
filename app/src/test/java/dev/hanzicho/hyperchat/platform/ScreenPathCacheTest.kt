package dev.hanzicho.hyperchat.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPathCacheTest {
    private val app = "dev.sample"

    @Test
    fun structureDoesNotIncludeLabelsOrFieldValues() {
        val search = node("$app:id/search", editable = true)
        val original = listOf(search, node("$app:id/result"))
        val changedContent = listOf(
            search.copy(text = "new search", description = "new description"),
            node("$app:id/result").copy(text = "unrelated result"),
            node("$app:id/result").copy(text = "another row"),
        )
        assertEquals(ScreenPathCache.fingerprint(app, original), ScreenPathCache.fingerprint(app, changedContent))
    }

    @Test
    fun structureChangesWhenResourceOrCapabilitiesChange() {
        val button = node("$app:id/search")
        val original = ScreenPathCache.fingerprint(app, listOf(button))
        assertNotEquals(original, ScreenPathCache.fingerprint(app, listOf(button.copy(editable = true))))
        assertNotEquals(original, ScreenPathCache.fingerprint(app, listOf(button.copy(viewId = "$app:id/other"))))
    }

    @Test
    fun onlyResourcesFromAppAndAndroidCanBeReplayed() {
        assertTrue(ScreenPathCache.isStableResource(app, "$app:id/search_input"))
        assertTrue(ScreenPathCache.isStableResource(app, "android:id/search_src_text"))
        assertFalse(ScreenPathCache.isStableResource(app, "com.other:id/search_input"))
        assertFalse(ScreenPathCache.isStableResource(app, "찾으려는 항목"))
        assertFalse(ScreenPathCache.isStableResource(app, "$app:id/"))
        assertNull(ScreenPathCache.fingerprint(app, listOf(node("com.other:id/search_input"), node(""))))
    }

    private fun node(viewId: String, editable: Boolean = false) = ScreenNode(
        text = "old search",
        description = "old description",
        viewId = viewId,
        clickable = !editable,
        editable = editable,
    )
}
