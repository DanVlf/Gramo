package dev.gramo

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.LightAudioItem
import com.thelightphone.sdk.audio.LightAudioPlayback
import com.thelightphone.sdk.audio.LightAudioSource
import com.thelightphone.sdk.audio.LightMediaMetadata
import com.thelightphone.sdk.audio.LightPlayerSpec
import com.thelightphone.toolmanager.LightFileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ImportStatus {
    data class Downloading(
        val label: String,
        val bytes: Long,
        val totalBytes: Long?,
    ) : ImportStatus()

    data class Importing(
        val archiveName: String,
        val imported: Int,
    ) : ImportStatus()

    data class Done(
        val imported: Int,
        val totalTracks: Int,
    ) : ImportStatus()
}

data class GramoUiState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val importStatus: ImportStatus? = null,
    val error: String? = null,
) {
    val currentTrack: Track?
        get() = tracks.getOrNull(currentIndex)
}

class GramoViewModel(
    audio: LightAudio,
    filesDir: File,
) : LightViewModel<Unit>() {

    private val musicDir = File(filesDir, MUSIC_DIR_NAME)
    private val downloadsDir = File(filesDir, DOWNLOADS_DIR_NAME)
    private val uploadsDir = File(
        File(filesDir, LightFileProvider.SHARED_DIR),
        ToolEntryPoint.UPLOADS_PATH,
    )

    val player = audio.newPlayer(
        LightPlayerSpec(playback = LightAudioPlayback.Detached),
    )
    val isPlaying = player.isPlaying
    val positionMs = player.positionMs
    val durationMs = player.durationMs

    private val importer = ArchiveImporter()

    private val _uiState = MutableStateFlow(GramoUiState())
    val uiState: StateFlow<GramoUiState> = _uiState.asStateFlow()

    private var queuedTrackIds: List<String> = emptyList()
    private var importJob: Job? = null

    init {
        refresh()
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        importer.close()
    }

    fun refresh() {
        _uiState.update { state ->
            state.copy(tracks = MusicLibrary.scan(musicDir))
        }
    }

    fun currentTrack(): Track? = _uiState.value.currentTrack

    fun playAt(index: Int) {
        val tracks = _uiState.value.tracks
        if (index !in tracks.indices) return
        if (queuedTrackIds != tracks.map { it.id }) {
            queuedTrackIds = tracks.map { it.id }
            player.setMediaQueue(tracks.map { it.toItem() }, index)
        } else {
            player.seekToDefaultPosition(index)
        }
        _uiState.update { it.copy(currentIndex = index) }
        player.play()
    }

    fun togglePlayPause() {
        if (isPlaying.value) player.pause() else player.play()
    }

    fun skipToNext() = skipBy(1)

    fun skipToPrevious() = skipBy(-1)

    private fun skipBy(delta: Int) {
        val trackCount = _uiState.value.tracks.size
        if (trackCount == 0) return
        val current = _uiState.value.currentIndex
        val target = if (current < 0) 0 else (current + delta).mod(trackCount)
        playAt(target)
    }

    fun importFromUrl(url: String, cookieHeader: String?) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("http://", ignoreCase = true)
        ) {
            _uiState.update { it.copy(error = "Enter a valid http(s) URL.") }
            return
        }
        startImport {
            val label = trimmed.substringAfterLast('/', trimmed).take(40)
            val destination = File(downloadsDir, "download-${System.currentTimeMillis()}.zip")
            _uiState.update { it.copy(importStatus = ImportStatus.Downloading(label, 0L, null)) }
            importer.download(trimmed, destination, cookieHeader) { bytes, total ->
                _uiState.update { state ->
                    (state.importStatus as? ImportStatus.Downloading)?.let { status ->
                        state.copy(
                            importStatus = status.copy(bytes = bytes, totalBytes = total),
                        )
                    } ?: state
                }
            }.fold(
                onSuccess = { file ->
                    val result = importArchive(file).getOrThrow()
                    file.delete()
                    result
                },
                onFailure = { throw it },
            )
        }
    }

    fun importFromToolManager() {
        startImport {
            val archives = uploadsDir.listFiles()
                ?.filter { it.isFile && MusicLibrary.isZipArchive(it.name) }
                .orEmpty()
                .sortedBy { it.name }
            if (archives.isEmpty()) {
                _uiState.update {
                    it.copy(
                        error = "No zip archives found in the Tool Manager upload folder.",
                    )
                }
                return@startImport ImportResult(imported = 0, skipped = 0)
            }
            var imported = 0
            var skipped = 0
            for (archive in archives) {
                val result = importArchive(archive)
                imported += result.getOrNull()?.imported ?: 0
                skipped += result.getOrNull()?.skipped ?: 0
                if (result.isSuccess) {
                    archive.delete()
                }
            }
            ImportResult(imported = imported, skipped = skipped)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun startImport(block: suspend () -> ImportResult) {
        if (importJob?.isActive == true) {
            _uiState.update { it.copy(error = "An import is already running.") }
            return
        }
        importJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = block()
                refresh()
                _uiState.update { state ->
                    state.copy(
                        importStatus = ImportStatus.Done(
                            imported = result.imported,
                            totalTracks = state.tracks.size,
                        ),
                    )
                }
            } catch (error: Exception) {
                _uiState.update { state ->
                    state.copy(
                        importStatus = null,
                        error = error.message ?: "Import failed.",
                    )
                }
            }
        }
    }

    private suspend fun importArchive(archive: File): Result<ImportResult> {
        _uiState.update {
            it.copy(importStatus = ImportStatus.Importing(archive.name, 0))
        }
        return importer.importZip(archive, musicDir) { imported ->
            _uiState.update { state ->
                (state.importStatus as? ImportStatus.Importing)?.let { status ->
                    state.copy(importStatus = status.copy(imported = imported))
                } ?: state
            }
        }
    }

    private fun Track.toItem(): LightAudioItem = LightAudioItem(
        source = LightAudioSource.FileSource(file),
        metadata = LightMediaMetadata(title = title),
        id = id,
    )

    private companion object {
        const val MUSIC_DIR_NAME = "music"
        const val DOWNLOADS_DIR_NAME = "downloads"
    }
}
