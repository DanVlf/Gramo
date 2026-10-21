package dev.gramo

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class ArchiveImporter {

    private val client = OkHttpClient()

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    fun download(
        url: String,
        destination: File,
        cookieHeader: String? = null,
        onProgress: (bytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        val request = Request.Builder()
            .url(url)
            .apply {
                if (!cookieHeader.isNullOrBlank()) {
                    header("Cookie", cookieHeader.trim())
                }
                header("User-Agent", USER_AGENT)
            }
            .build()
        destination.parentFile?.mkdirs()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed with HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty download response")
            val total = if (body.contentLength() > 0) body.contentLength() else null
            var written = 0L
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
        }
        destination
    }

    fun importZip(
        archive: File,
        musicDir: File,
        onProgress: (imported: Int) -> Unit = { _ -> },
    ): Result<ImportResult> = runCatching {
        musicDir.mkdirs()
        var imported = 0
        var skipped = 0
        ZipInputStream(FileInputStream(archive).buffered()).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entry.isDirectory) continue
                val entryName = entry.name.substringAfterLast('/')
                if (entryName.isBlank() || !MusicLibrary.isAudioFile(entryName)) {
                    skipped++
                    continue
                }
                val target = uniqueDestination(musicDir, sanitizeFileName(entryName))
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
                imported++
                onProgress(imported)
            }
        }
        ImportResult(imported = imported, skipped = skipped)
    }

    private fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var counter = 1
        while (true) {
            candidate = if (extension.isEmpty()) {
                File(dir, "$base ($counter)")
            } else {
                File(dir, "$base ($counter).$extension")
            }
            if (!candidate.exists()) return candidate
            counter++
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000]"), "_")
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { "track.mp3" }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_FILE_NAME_LENGTH = 180
        const val USER_AGENT = "Gramo/1.0 (Light Phone III tool)"
    }
}

object FileSizeFormat {

    fun format(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }
}
