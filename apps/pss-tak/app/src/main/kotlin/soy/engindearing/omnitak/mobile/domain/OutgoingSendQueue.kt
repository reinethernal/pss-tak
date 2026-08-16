package soy.engindearing.omnitak.mobile.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Disk-backed outbox for markers / CoT and photo publishes while offline.
 * Flushed when a TAK server reconnects.
 */
class OutgoingSendQueue(private val rootDir: File) {
    constructor(context: Context) : this(File(context.filesDir, "outgoing_queue"))

    private val photosDir = File(rootDir, "photos").also { it.mkdirs() }
    private val indexFile = File(rootDir, "index.json")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _items = MutableStateFlow<List<QueuedSend>>(emptyList())
    val items: StateFlow<List<QueuedSend>> = _items.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    init {
        rootDir.mkdirs()
        photosDir.mkdirs()
        runCatching { loadLocked() }
    }

    fun enqueueCot(xml: String): QueuedSend {
        val item = QueuedSend(
            id = UUID.randomUUID().toString(),
            kind = KIND_COT,
            xml = xml,
        )
        persistAdd(item)
        return item
    }

    fun enqueuePhoto(
        jpegBytes: ByteArray,
        lat: Double,
        lon: Double,
        callsign: String,
        remarks: String,
        eventUid: String = "",
    ): QueuedSend {
        val id = UUID.randomUUID().toString()
        val file = File(photosDir, "$id.jpg")
        file.writeBytes(jpegBytes)
        val item = QueuedSend(
            id = id,
            kind = KIND_PHOTO,
            photoRelPath = file.name,
            lat = lat,
            lon = lon,
            callsign = callsign,
            remarks = remarks,
            eventUid = eventUid,
        )
        persistAdd(item)
        return item
    }

    /**
     * Attempt to send every queued item. Stops on first failure so order is preserved.
     * @return number of successfully flushed items
     */
    suspend fun flush(
        sendCot: suspend (String) -> Boolean,
        publishPhoto: suspend (
            file: File,
            lat: Double,
            lon: Double,
            callsign: String,
            remarks: String,
            eventUid: String,
        ) -> Boolean,
    ): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = _items.value.toList()
            if (snapshot.isEmpty()) return@withContext 0
            var flushed = 0
            val remaining = snapshot.toMutableList()
            for (item in snapshot) {
                val ok = when (item.kind) {
                    KIND_COT -> {
                        val xml = item.xml
                        xml.isNotBlank() && runCatching { sendCot(xml) }.getOrDefault(false)
                    }
                    KIND_PHOTO -> {
                        val file = File(photosDir, item.photoRelPath)
                        file.isFile && runCatching {
                            publishPhoto(
                                file,
                                item.lat,
                                item.lon,
                                item.callsign,
                                item.remarks,
                                item.eventUid,
                            )
                        }.getOrDefault(false)
                    }
                    else -> false
                }
                if (!ok) {
                    Log.i(TAG, "flush stopped at ${item.id} kind=${item.kind}")
                    break
                }
                remaining.removeAll { it.id == item.id }
                if (item.kind == KIND_PHOTO) {
                    File(photosDir, item.photoRelPath).delete()
                }
                flushed++
            }
            _items.value = remaining
            _pendingCount.value = remaining.size
            writeIndexLocked(remaining)
            flushed
        }
    }

    private fun persistAdd(item: QueuedSend) {
        synchronized(this) {
            val next = _items.value + item
            _items.value = next
            _pendingCount.value = next.size
            writeIndexLocked(next)
        }
    }

    private fun loadLocked() {
        if (!indexFile.isFile) {
            _items.value = emptyList()
            _pendingCount.value = 0
            return
        }
        val list = runCatching {
            json.decodeFromString<List<QueuedSend>>(indexFile.readText())
        }.getOrElse {
            Log.w(TAG, "index corrupt, resetting", it)
            emptyList()
        }
        _items.value = list
        _pendingCount.value = list.size
    }

    private fun writeIndexLocked(list: List<QueuedSend>) {
        rootDir.mkdirs()
        indexFile.writeText(json.encodeToString(list))
    }

    companion object {
        private const val TAG = "OutgoingSendQueue"
        const val KIND_COT = "cot"
        const val KIND_PHOTO = "photo"
    }
}

@Serializable
data class QueuedSend(
    val id: String,
    val kind: String,
    val xml: String = "",
    val photoRelPath: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val callsign: String = "",
    val remarks: String = "",
    val eventUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
