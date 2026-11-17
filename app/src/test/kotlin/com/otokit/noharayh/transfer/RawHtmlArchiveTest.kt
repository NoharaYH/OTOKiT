package com.otokit.noharayh.transfer

import com.otokit.noharayh.score.html.RawHtmlArchive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.security.MessageDigest

class RawHtmlArchiveTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun severalMegabytesRoundTripWithoutKeepingWholePrefix() {
        val data = ("<div>原始成绩页面</div>\n".repeat(200_000)).toByteArray()
        val saved = RawHtmlArchive(temp.root).save("master.html", data.inputStream())
        assertEquals(data.size.toLong(), saved.bytes)
        assertArrayEquals(data, saved.file.readBytes())
        assertTrue(saved.prefix.length <= 256 * 1024)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }, saved.sha256)
    }

    @Test fun interruptedWriteDoesNotPublishTruncatedHtml() {
        val source = object : InputStream() {
            var count = 0
            override fun read(): Int { if (count++ > 500) throw java.io.IOException(); return 65 }
        }
        assertThrows(java.io.IOException::class.java) { RawHtmlArchive(temp.root).save("master.html", source) }
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }

    @Test fun repeatedDisconnectThenRetryNeverPublishesPartialFiles() {
        val data = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        repeat(10) { round ->
            val directory = java.io.File(temp.root, "round_$round")
            val archive = RawHtmlArchive(directory)
            val disconnected = object : InputStream() {
                var position = 0
                override fun read(): Int = throw UnsupportedOperationException()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (position >= 200_000) throw java.io.EOFException("Injected disconnect")
                    val count = minOf(length, 200_000 - position)
                    data.copyInto(buffer, offset, position, position + count)
                    position += count
                    return count
                }
            }
            assertThrows(java.io.EOFException::class.java) { archive.save("master.html", disconnected) }
            assertTrue(directory.listFiles()!!.isEmpty())
            val saved = archive.save("master.html", data.inputStream())
            assertArrayEquals(data, saved.file.readBytes())
            assertEquals(listOf("master.html"), directory.listFiles()!!.map { it.name })
        }
    }
}
