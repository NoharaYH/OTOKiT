package com.otokit.noharayh.logged.scoresync

import com.otokit.noharayh.storage.files.writeAtomically
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant

/** 日志独占自己的路径与索引；不读取成绩模块的私有目录。 */
internal class ScoreSyncLogFiles(val root: File) {
    fun directory(game: String, taskId: String): File {
        require(game in setOf("maimai", "chunithm") && taskId.matches(Regex("[A-Za-z0-9_-]{1,160}")))
        return File(File(root, game), taskId)
    }

    fun index(directory: File): JSONObject = JSONObject(File(directory, "index.json").readText(Charsets.UTF_8))

    fun writeIndex(directory: File, index: JSONObject) {
        File(directory, "index.json").writeAtomically { it.write(index.toString().toByteArray(Charsets.UTF_8)) }
    }

    fun directories(): List<File> = root.listFiles().orEmpty().filter { it.isDirectory }.flatMap { game ->
        game.listFiles().orEmpty().filter { it.isDirectory && File(it, "index.json").isFile }
    }

    fun ref(index: JSONObject) = ScoreSyncLogRef(index.getString("taskId"), index.getString("game"),
        Instant.parse(index.getString("startedAt")))

    fun summary(directory: File, index: JSONObject): ScoreSyncLogSummary = ScoreSyncLogSummary(
        ref(index), index.getString("status"), index.optBoolean("writing"),
        directory.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() },
        index.optString("batchId").takeIf { it.isNotEmpty() }, index.getString("fileStamp"),
        index.optString("recordingError").takeIf { it.isNotEmpty() },
    )

    /** 进程被杀可能留下半行。仅移去最后一个换行之后的字节，保留所有完整事件。 */
    fun recoverTail(file: File) {
        if (!file.isFile) return
        RandomAccessFile(file, "rw").use { output ->
            var length = output.length()
            while (length > 0) {
                output.seek(length - 1)
                if (output.read() == '\n'.code) break
                length--
            }
            output.setLength(length)
        }
    }

    fun safeFile(directory: File, name: String): File {
        val file = File(directory, name)
        if (file.canonicalFile.parentFile != directory.canonicalFile) throw IOException("Invalid log file reference")
        return file
    }
}
