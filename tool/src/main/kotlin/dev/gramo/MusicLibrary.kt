package dev.gramo

import java.io.File
import java.util.Locale

object MusicLibrary {

    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus")

    fun isAudioFile(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        return extension in AUDIO_EXTENSIONS
    }

    fun isZipArchive(name: String): Boolean =
        name.lowercase(Locale.US).endsWith(".zip")

    fun scan(root: File): List<Track> {
        val files = root.walkTopDown()
            .filter { it.isFile && isAudioFile(it.name) }
            .toList()
        return files
            .map { file ->
                Track(
                    id = file.relativeToOrSelf(root).invariantSeparatorsPath,
                    title = titleFromFileName(file.name),
                    file = file,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    fun titleFromFileName(name: String): String {
        val withoutExtension = name.substringBeforeLast('.', name)
        return withoutExtension
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { name }
    }
}
