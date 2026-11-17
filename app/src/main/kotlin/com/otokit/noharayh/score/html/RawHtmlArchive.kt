package com.otokit.noharayh.score.html

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** No HTML body is logged or retained as a batch-wide in-memory map. */
internal class RawHtmlArchive(private val directory: File) {
    data class SavedPage(val file: File, val bytes: Long, val sha256: String, val prefix: String)

    init { check(directory.mkdirs() || directory.isDirectory) }

    fun save(name: String, source: InputStream, cancelled: () -> Boolean = { false }): SavedPage {
        require(name.matches(Regex("[a-z0-9_-]+\\.html")))
        val target = File(directory, name)
        check(!target.exists()) { "Page already saved" }
        val pending = File(directory, "$name.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val prefix = java.io.ByteArrayOutputStream()
        var total = 0L
        try {
            FileOutputStream(pending).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 32L * 1024 * 1024) { "Page exceeds 32 MiB" }
                    out.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    val remaining = 256 * 1024 - prefix.size()
                    if (remaining > 0) prefix.write(buffer, 0, minOf(count, remaining))
                }
                out.fd.sync()
            }
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return SavedPage(target, total, digest.digest().joinToString("") { "%02x".format(it) },
                prefix.toString("UTF-8"))
        } catch (error: Exception) {
            pending.delete()
            throw error
        }
    }
}
