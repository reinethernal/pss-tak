package soy.engindearing.omnitak.mobile.domain

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ATAK-compatible Mission Package helpers for photo markers (`b-i-x-i`).
 *
 * Layout:
 * ```
 * MANIFEST/manifest.xml
 * attachments/<uid>/photo.jpg
 * ```
 */
object PhotoMarkerPackage {
    const val IMAGE_TYPE = "b-i-x-i"

    fun buildZip(markerUid: String, jpegBytes: ByteArray, filename: String = "photo.jpg"): ByteArray {
        val entryPath = "attachments/$markerUid/$filename"
        val manifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MissionPackageManifest version="2">
              <Configuration>
                <Parameter name="uid" value="${CotBuilders.xmlEscape(markerUid)}"/>
                <Parameter name="name" value="photo-marker"/>
              </Configuration>
              <Contents>
                <Content ignore="false" zipEntry="${CotBuilders.xmlEscape(entryPath)}">
                  <Parameter name="contentType" value="Image"/>
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
            zip.write(jpegBytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /**
     * Extract the first image bytes from a Mission Package / OTS-wrapped zip.
     * Prefers `attachments/<uid>/…` when [preferUid] is set.
     */
    fun extractImage(zipBytes: ByteArray, preferUid: String? = null): Pair<String, ByteArray>? {
        val images = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(zipBytes.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && isImageName(name)) {
                    images += name to zin.readBytes()
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        if (images.isEmpty()) return null
        if (preferUid != null) {
            images.firstOrNull { it.first.contains("/$preferUid/") || it.first.contains("attachments/$preferUid") }
                ?.let { return it }
        }
        return images.first()
    }

    fun isImageName(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.endsWith(".heic")
    }

    fun cacheFile(dir: File, uid: String): File {
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$uid.jpg")
    }
}
