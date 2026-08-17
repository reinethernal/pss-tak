package soy.engindearing.omnitak.mobile.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import soy.engindearing.omnitak.mobile.data.CoTEvent
import soy.engindearing.omnitak.mobile.data.CoTSource
import soy.engindearing.omnitak.mobile.data.TakRestApiClient
import java.io.File

/**
 * Publish and receive ATAK-compatible photo markers (`b-i-x-i` + fileshare).
 */
class PhotoMarkerManager(
    private val context: Context,
    private val serverManager: ServerManager,
    private val missionSyncManager: MissionSyncManager,
    private val certVault: soy.engindearing.omnitak.mobile.data.CertVault,
    private val contactStore: ContactStore,
    private val selfCallsign: () -> String,
    private val selfUid: () -> String,
    private val outgoingQueue: OutgoingSendQueue? = null,
) {
    private val cacheDir: File
        get() = File(context.filesDir, "photo_markers").also { if (!it.exists()) it.mkdirs() }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    sealed class PublishResult {
        data class Ok(val event: CoTEvent, val hash: String) : PublishResult()
        data class Queued(val event: CoTEvent) : PublishResult()
        data class Failed(val reason: String) : PublishResult()
    }

    suspend fun publishFromUri(
        uri: Uri,
        lat: Double,
        lon: Double,
        callsign: String = "Photo",
        remarks: String = "",
    ): PublishResult = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val jpeg = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext PublishResult.Failed("Cannot read image")
            publishBytes(jpeg, lat, lon, callsign, remarks)
        } catch (e: Exception) {
            Log.e(TAG, "publishFromUri failed", e)
            PublishResult.Failed(e.message ?: "upload failed")
        } finally {
            _busy.value = false
        }
    }

    suspend fun publishFromFile(
        file: File,
        lat: Double,
        lon: Double,
        callsign: String = "Photo",
        remarks: String = "",
    ): PublishResult = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            if (!file.isFile) return@withContext PublishResult.Failed("Image file missing")
            publishBytes(file.readBytes(), lat, lon, callsign, remarks)
        } catch (e: Exception) {
            Log.e(TAG, "publishFromFile failed", e)
            PublishResult.Failed(e.message ?: "upload failed")
        } finally {
            _busy.value = false
        }
    }

    private suspend fun publishBytes(
        jpegBytes: ByteArray,
        lat: Double,
        lon: Double,
        callsign: String,
        remarks: String,
        allowQueue: Boolean = true,
        existingUid: String? = null,
    ): PublishResult {
        val uid = existingUid?.takeIf { it.isNotBlank() } ?: CotBuilders.newUid()
        val zip = PhotoMarkerPackage.buildZip(uid, jpegBytes)
        val creatorUid = selfUid().ifBlank { uid }
        val upload = missionSyncManager.uploadDataPackage(
            zipBytes = zip,
            filename = "photo-$uid.zip",
            creatorUid = creatorUid,
        )
        val hash = when (upload) {
            is UploadOutcome.Hash -> upload.hash
            is UploadOutcome.NoServer, is UploadOutcome.Failed -> {
                if (allowQueue && outgoingQueue != null) {
                    outgoingQueue.enqueuePhoto(
                        jpegBytes, lat, lon, callsign, remarks, eventUid = uid,
                    )
                    val localFile = PhotoMarkerPackage.cacheFile(cacheDir, uid)
                    localFile.writeBytes(jpegBytes)
                    val event = CoTEvent(
                        uid = uid,
                        type = PhotoMarkerPackage.IMAGE_TYPE,
                        lat = lat,
                        lon = lon,
                        callsign = callsign.ifBlank { "Photo" },
                        remarks = remarks,
                        source = CoTSource.LOCAL,
                        localPhotoPath = localFile.absolutePath,
                    )
                    contactStore.ingest(event)
                    return PublishResult.Queued(event)
                }
                return when (upload) {
                    is UploadOutcome.NoServer ->
                        PublishResult.Failed("No connected server for photo upload")
                    is UploadOutcome.Failed ->
                        PublishResult.Failed(upload.reason)
                    else -> PublishResult.Failed("upload failed")
                }
            }
        }
        val server = serverManager.servers.value.firstOrNull { it.enabled }
            ?: serverManager.servers.value.firstOrNull()
            ?: return PublishResult.Failed("No server — open the HQ invite link first")
        val host = server.host
        val port = TakRestApiClient.SECURE_API_PORT
        val senderUrl = "https://$host:$port/Marti/sync/content?hash=$hash"
        val localFile = PhotoMarkerPackage.cacheFile(cacheDir, uid)
        localFile.writeBytes(jpegBytes)

        val event = CoTEvent(
            uid = uid,
            type = PhotoMarkerPackage.IMAGE_TYPE,
            lat = lat,
            lon = lon,
            callsign = callsign.ifBlank { "Photo" },
            remarks = remarks,
            source = CoTSource.LOCAL,
            fileshareSha256 = hash,
            fileshareUrl = senderUrl,
            fileshareFilename = "photo.jpg",
            localPhotoPath = localFile.absolutePath,
        )
        contactStore.ingest(event)
        val xml = CotBuilders.buildPhotoMarkerEvent(
            uid = uid,
            callsign = event.callsign ?: "Photo",
            lat = lat,
            lon = lon,
            remarks = remarks,
            fileshareSha256 = hash,
            fileshareUrl = senderUrl,
            fileshareFilename = "photo.jpg",
            sizeInBytes = jpegBytes.size.toLong(),
            senderUid = creatorUid,
            senderCallsign = selfCallsign().ifBlank { event.callsign ?: "PSS" },
        )
        runCatching { serverManager.sendCoT(xml, enqueueIfOffline = allowQueue) }
        return PublishResult.Ok(event, hash)
    }

    /** Used by [OutgoingSendQueue.flush] — never re-queues on failure. */
    suspend fun publishFromFileNoQueue(
        file: File,
        lat: Double,
        lon: Double,
        callsign: String = "Photo",
        remarks: String = "",
        eventUid: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext false
        when (
            publishBytes(
                file.readBytes(), lat, lon, callsign, remarks,
                allowQueue = false,
                existingUid = eventUid,
            )
        ) {
            is PublishResult.Ok -> true
            else -> false
        }
    }

    /**
     * If [event] carries fileshare metadata and we lack a local cache,
     * download the Mission Package and extract the image.
     */
    suspend fun ensureCached(event: CoTEvent): CoTEvent? = withContext(Dispatchers.IO) {
        val isPhoto = event.type == PhotoMarkerPackage.IMAGE_TYPE ||
            !event.fileshareSha256.isNullOrBlank()
        if (!isPhoto) return@withContext null

        val existing = event.localPhotoPath?.let { File(it) }
        if (existing != null && existing.isFile) return@withContext event

        val cached = PhotoMarkerPackage.cacheFile(cacheDir, event.uid)
        if (cached.isFile) {
            val updated = event.copy(localPhotoPath = cached.absolutePath)
            contactStore.ingest(updated)
            return@withContext updated
        }

        val hash = event.fileshareSha256 ?: return@withContext null
        val servers = serverManager.servers.value.filter { it.enabled }.ifEmpty {
            serverManager.servers.value
        }
        for (sv in servers) {
            val bytes = runCatching {
                TakRestApiClient(sv, certVault).downloadDataPackage(hash)
            }.getOrNull() ?: continue
            val extracted = PhotoMarkerPackage.extractImage(bytes, preferUid = event.uid)
                ?: continue
            cached.writeBytes(extracted.second)
            val updated = event.copy(
                localPhotoPath = cached.absolutePath,
                fileshareFilename = extracted.first.substringAfterLast('/'),
            )
            contactStore.ingest(updated)
            return@withContext updated
        }
        null
    }

    companion object {
        private const val TAG = "PhotoMarkerManager"
    }
}
