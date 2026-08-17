package soy.engindearing.omnitak.mobile.domain

import android.content.Context
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
 * Publish and receive ATAK-compatible voice markers (`b-i-x-a` + fileshare).
 */
class VoiceMarkerManager(
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
        get() = File(context.filesDir, "voice_markers").also { if (!it.exists()) it.mkdirs() }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    sealed class PublishResult {
        data class Ok(val event: CoTEvent, val hash: String) : PublishResult()
        data class Queued(val event: CoTEvent) : PublishResult()
        data class Failed(val reason: String) : PublishResult()
    }

    suspend fun publishFromFile(
        file: File,
        lat: Double,
        lon: Double,
        callsign: String = "Voice",
        remarks: String = "",
    ): PublishResult = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            if (!file.isFile) return@withContext PublishResult.Failed("Audio file missing")
            publishBytes(file.readBytes(), lat, lon, callsign, remarks)
        } catch (e: Exception) {
            Log.e(TAG, "publishFromFile failed", e)
            PublishResult.Failed(e.message ?: "upload failed")
        } finally {
            _busy.value = false
        }
    }

    private suspend fun publishBytes(
        audioBytes: ByteArray,
        lat: Double,
        lon: Double,
        callsign: String,
        remarks: String,
        allowQueue: Boolean = true,
        existingUid: String? = null,
    ): PublishResult {
        val uid = existingUid?.takeIf { it.isNotBlank() } ?: CotBuilders.newUid()
        val zip = VoiceMarkerPackage.buildZip(uid, audioBytes)
        val creatorUid = selfUid().ifBlank { uid }
        val upload = missionSyncManager.uploadDataPackage(
            zipBytes = zip,
            filename = "voice-$uid.zip",
            creatorUid = creatorUid,
        )
        val hash = when (upload) {
            is UploadOutcome.Hash -> upload.hash
            is UploadOutcome.NoServer, is UploadOutcome.Failed -> {
                if (allowQueue && outgoingQueue != null) {
                    outgoingQueue.enqueueVoice(
                        audioBytes, lat, lon, callsign, remarks, eventUid = uid,
                    )
                    val localFile = VoiceMarkerPackage.cacheFile(cacheDir, uid)
                    localFile.writeBytes(audioBytes)
                    val event = CoTEvent(
                        uid = uid,
                        type = VoiceMarkerPackage.AUDIO_TYPE,
                        lat = lat,
                        lon = lon,
                        callsign = callsign.ifBlank { "Voice" },
                        remarks = remarks,
                        source = CoTSource.LOCAL,
                        localAudioPath = localFile.absolutePath,
                    )
                    contactStore.ingest(event)
                    return PublishResult.Queued(event)
                }
                return when (upload) {
                    is UploadOutcome.NoServer ->
                        PublishResult.Failed("No connected server for voice upload")
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
        val localFile = VoiceMarkerPackage.cacheFile(cacheDir, uid)
        localFile.writeBytes(audioBytes)

        val event = CoTEvent(
            uid = uid,
            type = VoiceMarkerPackage.AUDIO_TYPE,
            lat = lat,
            lon = lon,
            callsign = callsign.ifBlank { "Voice" },
            remarks = remarks,
            source = CoTSource.LOCAL,
            fileshareSha256 = hash,
            fileshareUrl = senderUrl,
            fileshareFilename = VoiceMarkerPackage.DEFAULT_FILENAME,
            localAudioPath = localFile.absolutePath,
        )
        contactStore.ingest(event)
        val xml = CotBuilders.buildVoiceMarkerEvent(
            uid = uid,
            callsign = event.callsign ?: "Voice",
            lat = lat,
            lon = lon,
            remarks = remarks,
            fileshareSha256 = hash,
            fileshareUrl = senderUrl,
            fileshareFilename = VoiceMarkerPackage.DEFAULT_FILENAME,
            sizeInBytes = audioBytes.size.toLong(),
            senderUid = creatorUid,
            senderCallsign = selfCallsign().ifBlank { event.callsign ?: "PSS" },
        )
        runCatching { serverManager.sendCoT(xml, enqueueIfOffline = allowQueue) }
        return PublishResult.Ok(event, hash)
    }

    suspend fun publishFromFileNoQueue(
        file: File,
        lat: Double,
        lon: Double,
        callsign: String = "Voice",
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

    suspend fun ensureCached(event: CoTEvent): CoTEvent? = withContext(Dispatchers.IO) {
        val isVoice = event.type == VoiceMarkerPackage.AUDIO_TYPE ||
            VoiceMarkerPackage.isAudioName(event.fileshareFilename ?: "")
        if (!isVoice) return@withContext null

        val existing = event.localAudioPath?.let { File(it) }
        if (existing != null && existing.isFile) return@withContext event

        val cached = VoiceMarkerPackage.cacheFile(cacheDir, event.uid)
        if (cached.isFile) {
            val updated = event.copy(localAudioPath = cached.absolutePath)
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
            val extracted = VoiceMarkerPackage.extractAudio(bytes, preferUid = event.uid)
                ?: continue
            cached.writeBytes(extracted.second)
            val updated = event.copy(
                localAudioPath = cached.absolutePath,
                fileshareFilename = extracted.first.substringAfterLast('/'),
            )
            contactStore.ingest(updated)
            return@withContext updated
        }
        null
    }

    companion object {
        private const val TAG = "VoiceMarkerManager"
    }
}
