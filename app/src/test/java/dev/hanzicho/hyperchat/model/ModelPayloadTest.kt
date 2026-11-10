package dev.hanzicho.hyperchat.model

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelPayloadTest {
    @Test
    fun exactSizeLimitCopiesEveryByte() {
        val input = "LITERTLM".encodeToByteArray() + byteArrayOf(0, -1, 5, 17)
        val output = ByteArrayOutputStream()
        assertEquals(input.size.toLong(), copyModelPayload(input.inputStream(), output, input.size.toLong()) {})
        assertArrayEquals(input, output.toByteArray())
    }

    @Test
    fun nonModelIsRejectedBeforeWritingToStaging() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            copyModelPayload("This is not a model".byteInputStream(), output) {}
        }
        assertEquals(0, output.size())
    }

    @Test
    fun partialHeaderAndOversizedPayloadAreRejected() {
        assertThrows(IOException::class.java) {
            copyModelPayload("LITERTL".byteInputStream(), ByteArrayOutputStream()) {}
        }
        assertThrows(IOException::class.java) {
            copyModelPayload("LITERTLM".byteInputStream(), ByteArrayOutputStream()) {}
        }
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            copyModelPayload("LITERTLMbody".byteInputStream(), output, 11) {}
        }
        // Rejection must happen before writing an oversized body to private storage.
        assertEquals(8, output.size())
    }
}
