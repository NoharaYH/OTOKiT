package com.otokit.noharayh.storage.files

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun File.writeAtomically(write: (OutputStream) -> Unit) {
    parentFile?.mkdirs()
    val temporary = File(parentFile, "$name.part")
    try {
        FileOutputStream(temporary).use {
            write(it)
            it.fd.sync()
        }
        Files.move(temporary.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temporary.delete()
    }
}
