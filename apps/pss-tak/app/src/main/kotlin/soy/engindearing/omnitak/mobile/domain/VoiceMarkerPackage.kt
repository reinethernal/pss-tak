package soy.engindearing.omnitak.mobile.domain

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ATAK-compatible Mission Package helpers for voice markers (`b-i-x-a`).
 *
 * Layout:
 * ```
 * MANIFEST/manifest.xml
 * attachments/<uid>/voice.m4a
 * ```
 */
object VoiceMarkerPackage {
    const val AUDIO_TYPE = "b-i-x-a"
    const val DEFAULT_FILENAME = "voice.m4a"

    fun buildZip(markerUid: String, audioBytes: ByteArray, filename: String = DEFAULT_FILENAME): ByteArray {
        val entryPath = "attachments/$markerUid/$filename"
        val manifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MissionPackageManifest version="2">
              <Configuration>
                <Parameter name="uid" value="${CotBuilders.xmlEscape(markerUid)}"/>
                <Parameter name="name" value="voice-marker"/>
              </Configuration>
              <Contents>
                <Content ignore="false" zipEntry="${CotBuilders.xmlEscape(entryPath)}">
                  <Parameter name="contentType" value="Audio"/>
                </Content>
              </Contents>
            </MissionPackageManifest>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("MANIFEST/manifest.xml"))
            zip.write(manifest)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(entryPath))
            zip.write(audioBytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    fun extractAudio(zipBytes: ByteArray, preferUid: String? = null): Pair<String, ByteArray>? {
        val audio = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(zipBytes.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && isAudioName(name)) {
                    audio += name to zin.readBytes()
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        if (audio.isEmpty()) return null
        if (preferUid != null) {
            audio.firstOrNull { it.first.contains("/$preferUid/") || it.first.contains("attachments/$preferUid") }
                ?.let { return it }
        }
        return audio.first()
    }

    fun isAudioName(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".m4a") || lower.endsWith(".aac") ||
            lower.endsWith(".mp3") || lower.endsWith(".wav") ||
            lower.endsWith(".ogg") || lower.endsWith(".3gp")
    }

    fun cacheFile(dir: File, uid: String): File {
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$uid.m4a")
    }
}
