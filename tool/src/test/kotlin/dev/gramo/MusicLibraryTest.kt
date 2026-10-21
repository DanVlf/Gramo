package dev.gramo

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncManifestTest {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a serve_music manifest`() {
        val manifest = json.decodeFromString(
            SyncManifest.serializer(),
            """{"files":[{"name":"song a.mp3","size":123},{"name":"song_b.m4a","size":456}]}""",
        )
        assertEquals(2, manifest.files.size)
        assertEquals("song a.mp3", manifest.files[0].name)
        assertEquals(456L, manifest.files[1].size)
    }

    @Test
    fun `empty and unknown fields are tolerated`() {
        val manifest = json.decodeFromString(
            SyncManifest.serializer(),
            """{"files":[],"extra":"ignored"}""",
        )
        assertEquals(0, manifest.files.size)
    }

    @Test
    fun `planner downloads only missing files`() {
        val manifest = SyncManifest(
            files = listOf(
                SyncFile("have.mp3", 1),
                SyncFile("new.mp3", 2),
                SyncFile("gone.mp3", 3),
            ),
        )
        val plan = SyncPlanner.plan(manifest, existingNames = setOf("have.mp3"))
        assertEquals(listOf("new.mp3", "gone.mp3"), plan.map { it.name })
    }
}

class MusicLibraryTest {

    @Test
    fun `detects audio files by extension`() {
        assertTrue(MusicLibrary.isAudioFile("song.MP3"))
        assertTrue(MusicLibrary.isAudioFile("track.m4a"))
        assertTrue(MusicLibrary.isAudioFile("a.flac"))
        assertTrue(!MusicLibrary.isAudioFile("notes.csv"))
        assertTrue(!MusicLibrary.isAudioFile("archive.zip"))
        assertTrue(!MusicLibrary.isAudioFile("noextension"))
    }

    @Test
    fun `detects zip archives`() {
        assertTrue(MusicLibrary.isZipArchive("takeout-20261021T000000Z-001.ZIP"))
        assertTrue(!MusicLibrary.isZipArchive("takeout.tgz"))
    }

    @Test
    fun `titles come from file names`() {
        assertEquals("01 - Blue Hour", MusicLibrary.titleFromFileName("01_-_Blue_Hour.mp3"))
        assertEquals("Track Name", MusicLibrary.titleFromFileName("Track Name.m4a"))
        assertEquals("weird spaces", MusicLibrary.titleFromFileName("weird   spaces.flac"))
        assertEquals("noextension", MusicLibrary.titleFromFileName("noextension"))
    }

    @Test
    fun `scan finds and sorts audio recursively`() {
        val root = Files.createTempDirectory("library").toFile()
        File(root, "sub").mkdirs()
        File(root, "beta song.mp3").writeBytes(byteArrayOf(1))
        File(root, "sub/alpha song.mp3").writeBytes(byteArrayOf(1))
        File(root, "sub/ignored.txt").writeBytes(byteArrayOf(1))

        val tracks = MusicLibrary.scan(root)
        assertEquals(listOf("alpha song", "beta song"), tracks.map { it.title })
        assertEquals(2, tracks.size)
        assertTrue(tracks.all { it.file.isFile })

        root.deleteRecursively()
    }
}

class ArchiveImporterTest {

    private fun writeZip(vararg entries: Pair<String, ByteArray>): File {
        val zip = Files.createTempFile("archive", ".zip").toFile()
        ZipOutputStream(FileOutputStream(zip)).use { stream ->
            entries.forEach { (name, bytes) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(bytes)
                stream.closeEntry()
            }
        }
        return zip
    }

    @Test
    fun `imports only audio entries from nested paths`() {
        val zip = writeZip(
            "Takeout/YouTube and YouTube Music/Music/song a.mp3" to byteArrayOf(1, 2, 3),
            "deep/nested/folder/song_b.m4a" to byteArrayOf(4),
            "Takeout/metadata.csv" to byteArrayOf(5),
        )
        val musicDir = Files.createTempDirectory("music").toFile()
        val importer = ArchiveImporter()

        val result = importer.importZip(zip, musicDir).getOrThrow()

        assertEquals(2, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(setOf("song a.mp3", "song_b.m4a"), musicDir.list()!!.toSet())
        assertEquals(3, File(musicDir, "song a.mp3").length())

        zip.delete()
        musicDir.deleteRecursively()
    }

    @Test
    fun `re-importing the same archive creates unique files`() {
        val zip = writeZip("song.mp3" to byteArrayOf(1))
        val musicDir = Files.createTempDirectory("music").toFile()
        val importer = ArchiveImporter()

        importer.importZip(zip, musicDir).getOrThrow()
        importer.importZip(zip, musicDir).getOrThrow()

        assertEquals(setOf("song.mp3", "song (1).mp3"), musicDir.list()!!.toSet())

        zip.delete()
        musicDir.deleteRecursively()
    }

    @Test
    fun `dangerous entry names are sanitized`() {
        val zip = writeZip("../../evil:name?.mp3" to byteArrayOf(9))
        val musicDir = Files.createTempDirectory("music").toFile()
        val importer = ArchiveImporter()

        val result = importer.importZip(zip, musicDir).getOrThrow()

        assertEquals(1, result.imported)
        val names = musicDir.list()!!.toList()
        assertEquals(1, names.size)
        assertTrue(names[0].endsWith(".mp3"))
        assertEquals(1, musicDir.parentFile.walkTopDown().count { it.name.startsWith("evil") })

        zip.delete()
        musicDir.deleteRecursively()
    }
}
