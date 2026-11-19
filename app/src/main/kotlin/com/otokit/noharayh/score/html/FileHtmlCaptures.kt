package com.otokit.noharayh.score.html

import com.otokit.noharayh.score.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.UUID
import java.util.Collections

/** Private, immutable HTML plus an atomic batch index. No login URLs/cookies are persisted. */
class FileHtmlCaptures(private val root: File) : HtmlCaptures {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.systemDefault())

    @Synchronized override fun begin(game: ScoreGame, expected: List<HtmlPageKind>, taskId: String, startedAt: Instant): HtmlBatchRef {
        require(expected.isNotEmpty() && expected.distinct().size == expected.size)
        val batch = HtmlBatchRef("${startedAt.toEpochMilli()}-${UUID.randomUUID().toString().take(8)}", game, startedAt)
        val directory = directory(batch)
        check(directory.mkdirs())
        writeIndex(directory, Properties().apply {
            setProperty("version", "1")
            setProperty("game", game.name)
            setProperty("taskId", taskId)
            setProperty("startedAt", batch.startedAt.toString())
            setProperty("fileStamp", stamp.format(startedAt))
            setProperty("identity", "UNIDENTIFIED")
            setProperty("status", HtmlBatchStatus.RUNNING.name)
            setProperty("expected", expected.joinToString(",") { it.name })
        })
        return batch
    }

    @Synchronized override fun save(batch: HtmlBatchRef, kind: HtmlPageKind, source: InputStream, cancelled: () -> Boolean): HtmlPageRef {
        val directory = directory(batch)
        val index = readIndex(directory)
        check(index.getProperty("status") == HtmlBatchStatus.RUNNING.name)
        require(kind.isDiagnostic || kind.name in index.getProperty("expected").split(','))
        val prefix = if (batch.game == ScoreGame.MAIMAI) "MaiRecord" else "ChuRecord"
        val fileStamp = index.getProperty("fileStamp")
        val name = "${prefix}_HTML_${fileStamp}_${kind.suffix}.html"
        val saved = RawHtmlArchive(directory).save(name, source, cancelled)
        val page = HtmlPageRef(batch, kind, name, saved.bytes, saved.sha256, Instant.now())
        index.setProperty("page.${kind.name}.file", name)
        index.setProperty("page.${kind.name}.bytes", saved.bytes.toString())
        index.setProperty("page.${kind.name}.sha256", saved.sha256)
        index.setProperty("page.${kind.name}.capturedAt", page.capturedAt.toString())
        try { writeIndex(directory, index) }
        catch (error: Exception) { saved.file.delete(); throw error }
        return page
    }

    @Synchronized override fun open(page: HtmlPageRef): InputStream {
        val directory = directory(page.batch)
        val index = readIndex(directory)
        require(page.fileName == index.getProperty("page.${page.kind.name}.file"))
        val file = File(directory, page.fileName)
        require(file.canonicalFile.parentFile == directory.canonicalFile)
        check(file.length() == page.bytes && page.sha256 == index.getProperty("page.${page.kind.name}.sha256"))
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == page.sha256) { "Corrupt HTML archive" }
        return file.inputStream()
    }

    @Synchronized override fun recordEvidence(page: HtmlPageRef, evidence: HtmlPageEvidence) {
        val directory = directory(page.batch)
        val index = readIndex(directory)
        check(index.getProperty("page.${page.kind.name}.sha256") == page.sha256)
        index.setProperty("page.${page.kind.name}.validity", evidence.validity.name)
        index.setProperty("page.${page.kind.name}.charts", evidence.chartCount.toString())
        index.setProperty("page.${page.kind.name}.scores", evidence.scoreCount.toString())
        evidence.businessErrorCode?.let { index.setProperty("page.${page.kind.name}.businessErrorCode", it) }
        writeIndex(directory, index)
    }

    @Synchronized override fun list(game: ScoreGame): List<HtmlBatchRef> = Collections.unmodifiableList(
        File(root, "unidentified/${game.key}").listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "batch.properties").isFile }
            .map { directory -> HtmlBatchRef(directory.name, game, Instant.parse(readIndex(directory).getProperty("startedAt"))) }
            .sortedByDescending { it.startedAt })

    @Synchronized override fun read(batch: HtmlBatchRef): HtmlBatchRecord {
        val index = readIndex(directory(batch))
        val pages = HtmlPageKind.entries.mapNotNull { kind ->
            val name = index.getProperty("page.${kind.name}.file") ?: return@mapNotNull null
            HtmlPageRef(batch, kind, name, index.getProperty("page.${kind.name}.bytes").toLong(),
                index.getProperty("page.${kind.name}.sha256"), Instant.parse(index.getProperty("page.${kind.name}.capturedAt")))
        }
        val evidence = pages.mapNotNull { page ->
            index.getProperty("page.${page.kind.name}.validity")?.let { validity -> page.kind to HtmlPageEvidence(
                HtmlValidity.valueOf(validity), index.getProperty("page.${page.kind.name}.charts").toInt(),
                index.getProperty("page.${page.kind.name}.scores").toInt(), index.getProperty("page.${page.kind.name}.businessErrorCode")) }
        }.toMap()
        return HtmlBatchRecord(batch, index.getProperty("taskId"), HtmlBatchStatus.valueOf(index.getProperty("status")),
            Collections.unmodifiableList(index.getProperty("expected").split(',').map { HtmlPageKind.valueOf(it) }),
            Collections.unmodifiableList(pages), Collections.unmodifiableMap(evidence), index.getProperty("fileStamp"))
    }

    @Synchronized override fun finish(batch: HtmlBatchRef, status: HtmlBatchStatus) {
        require(status != HtmlBatchStatus.RUNNING)
        val directory = directory(batch)
        val index = readIndex(directory)
        if (status == HtmlBatchStatus.COMPLETED) {
            check(index.getProperty("expected").split(',').all {
                index.getProperty("page.$it.validity") in listOf(HtmlValidity.VALID.name, HtmlValidity.VALID_EMPTY_SCORES.name)
            }) { "Unverified capture pages" }
        }
        index.setProperty("status", status.name)
        writeIndex(directory, index)
    }

    /** Called only after acquiring the application's single capture lease. Never replays OAuth. */
    @Synchronized override fun recoverInterrupted() {
        ScoreGame.entries.forEach { game ->
            File(root, "unidentified/${game.key}").listFiles()?.filter { it.isDirectory }?.forEach batchLoop@ { directory ->
                if (!File(directory, "batch.properties").isFile) return@batchLoop
                val index = readIndex(directory)
                val indexed = index.stringPropertyNames().filter { it.endsWith(".file") }.map { index.getProperty(it) }.toSet()
                directory.listFiles()?.filter { it.isFile && (it.name.endsWith(".part") || (it.extension == "html" && it.name !in indexed)) }
                    ?.forEach { check(it.delete()) }
                if (index.getProperty("status") == HtmlBatchStatus.RUNNING.name) {
                    index.setProperty("status", HtmlBatchStatus.INTERRUPTED.name)
                    writeIndex(directory, index)
                }
            }
        }
    }

    private fun directory(batch: HtmlBatchRef): File {
        require(batch.id.matches(Regex("[0-9]+-[a-f0-9]{8}")))
        return File(root, "unidentified/${batch.game.key}/${batch.id}")
    }

    private fun readIndex(directory: File) = Properties().apply { File(directory, "batch.properties").inputStream().use { load(it) } }
    private fun writeIndex(directory: File, index: Properties) {
        val pending = File(directory, "batch.properties.part")
        try {
            FileOutputStream(pending).use { stream -> index.store(stream, "OTOKiT HTML capture index"); stream.fd.sync() }
            Files.move(pending.toPath(), File(directory, "batch.properties").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }
}
