package dev.gramo

import java.io.File

data class Track(
    val id: String,
    val title: String,
    val file: File,
)

data class ImportResult(
    val imported: Int,
    val skipped: Int,
)
