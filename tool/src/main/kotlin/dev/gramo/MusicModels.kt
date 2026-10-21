package dev.gramo

import java.io.File
import kotlinx.serialization.Serializable

data class Track(
    val id: String,
    val title: String,
    val file: File,
)

data class ImportResult(
    val imported: Int,
    val skipped: Int,
)

@Serializable
data class SyncFile(
    val name: String,
    val size: Long = 0,
)

@Serializable
data class SyncManifest(
    val files: List<SyncFile> = emptyList(),
)

object SyncPlanner {

    fun plan(manifest: SyncManifest, existingNames: Set<String>): List<SyncFile> =
        manifest.files.filter { it.name !in existingNames }
}
