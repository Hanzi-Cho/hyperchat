package dev.hanzicho.hyperchat.attachment

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal class AttachmentReadException(message: String) : IOException(message)

private const val MAX_TEXT_BYTES = 64 * 1024
private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024
private const val MAX_IMAGE_SIDE = 1600
private const val MAX_ATTACHMENT_CHARS = 420

internal suspend fun readAttachment(resolver: ContentResolver, uri: Uri, kind: AttachmentKind): DraftAttachment =
    withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            throw AttachmentReadException("Android 선택기에서 파일을 다시 골라 주세요.")
        }
        val name = displayName(resolver, uri, kind)
        val image = kind == AttachmentKind.IMAGE
        val bytes = readBounded(resolver, uri, if (image) MAX_IMAGE_BYTES else MAX_TEXT_BYTES)
        val raw = if (image) readImageText(bytes) else decodeText(bytes)
        val cleaned = raw.filterNot { it.isISOControl() && it != '\n' && it != '\t' }.trim()
        if (!image && cleaned.isBlank()) throw AttachmentReadException("읽을 수 있는 텍스트가 없어요.")
        DraftAttachment(kind, name, cleaned.safePrefix(MAX_ATTACHMENT_CHARS), cleaned.length > MAX_ATTACHMENT_CHARS)
    }

private fun displayName(resolver: ContentResolver, uri: Uri, kind: AttachmentKind): String {
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.filterNot { it.isISOControl() || it.category == CharCategory.FORMAT }?.trim()?.safePrefix(64)
    return name?.takeIf(String::isNotBlank) ?: if (kind == AttachmentKind.IMAGE) "사진" else "텍스트 문서"
}

private suspend fun readBounded(resolver: ContentResolver, uri: Uri, maxBytes: Int): ByteArray {
    return resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) throw AttachmentReadException("파일 읽기가 중단됐어요.")
            if (read > maxBytes - output.size()) throw AttachmentReadException(
                if (maxBytes == MAX_IMAGE_BYTES) "16 MiB 이하의 사진을 골라 주세요." else "64 KiB 이하의 텍스트 문서를 골라 주세요.",
            )
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: throw AttachmentReadException("파일을 열지 못했어요.")
}

private fun decodeText(bytes: ByteArray): String {
    val text = try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    } catch (_: CharacterCodingException) {
        throw AttachmentReadException("UTF-8로 저장한 텍스트 문서를 골라 주세요.")
    }
    if ('\u0000' in text || text.isBlank()) throw AttachmentReadException("읽을 수 있는 텍스트가 없어요.")
    return text
}

private suspend fun readImageText(bytes: ByteArray): String {
    val bitmap = if (Build.VERSION.SDK_INT >= 28) decodeModern(bytes) else decodeLegacy(bytes)
    return try {
        withTimeout(20_000) { recognizeText(bitmap) }
    } catch (_: TimeoutCancellationException) {
        throw AttachmentReadException("사진 속 글자를 읽는 데 시간이 오래 걸려요. 더 작은 사진으로 다시 시도해 주세요.")
    }
}

@RequiresApi(28)
private fun decodeModern(bytes: ByteArray): Bitmap = ImageDecoder.decodeBitmap(
    ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
) { decoder, info, _ ->
    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    val (width, height) = info.size.let { it.width to it.height }
    verifyImageSize(width, height)
    val maxSide = maxOf(width, height)
    if (maxSide > MAX_IMAGE_SIDE) {
        decoder.setTargetSize(
            (width.toLong() * MAX_IMAGE_SIDE / maxSide).toInt().coerceAtLeast(1),
            (height.toLong() * MAX_IMAGE_SIDE / maxSide).toInt().coerceAtLeast(1),
        )
    }
}

private fun decodeLegacy(bytes: ByteArray): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    verifyImageSize(options.outWidth, options.outHeight)
    options.inJustDecodeBounds = false
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > MAX_IMAGE_SIDE) {
        options.inSampleSize *= 2
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw AttachmentReadException("선택한 사진 형식을 읽지 못했어요.")
}

private fun verifyImageSize(width: Int, height: Int) {
    if (width < 1 || height < 1) throw AttachmentReadException("선택한 사진 형식을 읽지 못했어요.")
    if (width.toLong() * height > 64_000_000L) throw AttachmentReadException("해상도가 더 작은 사진을 골라 주세요.")
}

private suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    try {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
            runCatching { recognizer.close() }
            if (continuation.isActive) {
                if (task.isSuccessful) continuation.resume(task.result?.text.orEmpty())
                else continuation.resumeWithException(AttachmentReadException("사진에서 글자를 읽지 못했어요."))
            }
        }
    } catch (_: Exception) {
        runCatching { recognizer.close() }
        continuation.resumeWithException(AttachmentReadException("사진에서 글자를 읽지 못했어요."))
    }
}

private fun String.safePrefix(limit: Int): String = take(limit).let { prefix ->
    if (prefix.lastOrNull()?.isHighSurrogate() == true) prefix.dropLast(1) else prefix
}
