package dev.hanzicho.hyperchat.model

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private val LITERT_LM_MAGIC = "LITERTLM".encodeToByteArray()
internal const val MAX_MODEL_BYTES = 1_073_741_824L

/** Copies a candidate into a staging file. The caller owns both streams and publishes the file. */
internal fun copyModelPayload(
    source: InputStream,
    staging: OutputStream,
    maxBytes: Long = MAX_MODEL_BYTES,
    checkActive: () -> Unit,
): Long {
    require(maxBytes >= LITERT_LM_MAGIC.size)
    checkActive()
    val header = ByteArray(LITERT_LM_MAGIC.size)
    try {
        DataInputStream(source).readFully(header)
    } catch (error: EOFException) {
        throw IOException("LiteRT-LM 모델 파일을 읽을 수 없습니다.", error)
    }
    if (!header.contentEquals(LITERT_LM_MAGIC)) {
        throw IOException(".litertlm 형식의 모델 파일을 선택해 주세요.")
    }
    checkActive()
    staging.write(header)
    var copied = header.size.toLong()
    val buffer = ByteArray(256 * 1024)
    while (true) {
        checkActive()
        val count = source.read(buffer)
        if (count < 0) break
        if (count == 0) throw IOException("모델 파일 읽기가 중단됐습니다.")
        if (count > maxBytes - copied) throw IOException("1 GiB 이하의 모델만 가져올 수 있습니다.")
        checkActive()
        staging.write(buffer, 0, count)
        copied += count
    }
    if (copied == header.size.toLong()) throw IOException("모델 파일에 데이터가 없습니다.")
    return copied
}
