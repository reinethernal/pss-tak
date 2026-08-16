package soy.engindearing.omnitak.mobile.data

import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import soy.engindearing.omnitak.mobile.data.net.TakTls
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * TAK Server Marti REST API client — mission + data-package sync. The Android
 * counterpart to iOS's TAKRestAPIClient, built on plain [HttpsURLConnection]
 * with mutual-TLS (same approach as [CSREnrollmentService]) so we add no new
 * HTTP dependency.
 *
 * One instance is bound to one [TAKServer]; [MissionSyncManager] spins up one
 * per enabled server and aggregates. mTLS uses the operator's imported `.p12`
 * (via [CertVault]); server trust comes from [TakTls] — enrollment CA pin
 * when one exists, system trust otherwise — exactly matching [TAKConnection],
 * so the REST plane can no longer lag the streaming plane's trust policy.
 *
 * Dialect tolerance (verified against the 4-server matrix — TAK Server 5.7,
 * OpenTAKServer 1.7.x, taky 0.10):
 *  - reachability uses `/Marti/api/version/config` (the one endpoint all three
 *    return 200 for — `/Marti/api/version` 404s on OpenTAKServer).
 *  - list envelopes differ: TAK5.7/OTS wrap in `{data:[…]}`, taky's sync
 *    search returns `{resultCount,results:[…]}` — both handled, plus a bare
 *    top-level array.
 *  - taky has no `/Marti/api/missions` route; that fetch is tolerated empty.
 */
class TakRestApiClient(
    private val server: TAKServer,
    private val certVault: CertVault?,
) {
    /** TAK HTTPS API port. Distinct from the CoT streaming port in [server]. */
    private val apiPort = SECURE_API_PORT

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun baseUrl(path: String) = "https://${server.host}:$apiPort$path"

    // ------------------------------------------------------------------
    // Public API (each is a blocking network call — invoke off the main
    // thread; MissionSyncManager wraps these in Dispatchers.IO).
    // ------------------------------------------------------------------

    /**
     * Lightweight reachability + mTLS probe. Throws [ApiException] when the
     * host is unreachable, the cert is rejected, or the server errors —
     * otherwise returns normally (the body is ignored).
     */
    fun checkReachability() {
        val (code, body) = httpGet("/Marti/api/version/config")
        if (code !in 200..299) throw ApiException(httpReason(code, body))
    }

    /** Available missions. Empty on dialects without the endpoint (taky). */
    fun getMissions(): List<TakMissionInfo> {
        val (code, body) = httpGet("/Marti/api/missions")
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseMissions(body)
    }

    /** Available data packages via the sync search index. */
    fun getDataPackages(): List<TakDataPackageInfo> {
        val (code, body) = httpGet("/Marti/api/sync/search")
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseDataPackages(body)
    }

    /**
     * `PUT /Marti/api/missions/{name}?creatorUid&tool&description&group&bbox`
     * Returns the server's view of the freshly-created mission. iOS parity
     * for issue #14 (commit ffcd48d): Marti tolerates an empty body on
     * create, but some CIV builds insist on valid JSON, so we ship `{}`.
     * Body shape on success varies across TAK 5.7 / OTS / taky — when the
     * server returns 2xx with an unparseable / empty body we synthesise
     * the response from the create args. Closes #30 slice 1.
     */
    fun createMission(
        name: String,
        creatorUid: String,
        tool: String = "public",
        description: String? = null,
        groups: List<String> = emptyList(),
        defaultRole: String? = null,
        bbox: MissionBbox? = null,
    ): TakMissionInfo {
        val q = buildList {
            add("creatorUid" to creatorUid)
            add("tool" to tool)
            if (!description.isNullOrBlank()) add("description" to description)
            if (!defaultRole.isNullOrBlank()) add("defaultRole" to defaultRole)
            groups.filter { it.isNotBlank() }.forEach { add("group" to it) }
            bbox?.let { add("bbox" to it.queryValue) }
        }
        val (code, body) = httpRequest(
            method = "PUT",
            path = "/Marti/api/missions/${urlSegment(name)}",
            query = q,
            body = "{}".toByteArray(Charsets.UTF_8),
            contentType = "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        // Best-effort decode; fall back to a synthesized record so the UI
        // gets something back when the server returns 200 with empty body.
        val parsed = runCatching { parseMissions(body).firstOrNull() }.getOrNull()
        return parsed ?: TakMissionInfo(
            name = name,
            description = description,
            creatorUid = creatorUid,
            keywords = emptyList(),
            contentCount = 0,
        )
    }

    /**
     * `POST /Marti/sync/missionupload` — multipart upload of a TAK
     * Mission Package (.zip). Returns the server's SHA-256 hash from
     * the plain-text response (`https://server/Marti/sync/content?hash=…`,
     * or fall back to `hash=…` token form for older Marti builds).
     */
    fun uploadDataPackage(
        zipBytes: ByteArray,
        filename: String,
        creatorUid: String,
    ): String {
        val boundary = "omnitak-${java.util.UUID.randomUUID()}"
        val body = buildMultipartPackage(boundary, filename, zipBytes)
        val (code, response) = httpRequest(
            method = "POST",
            path = "/Marti/sync/missionupload",
            query = listOf(
                "creatorUid" to creatorUid,
                "filename" to filename,
            ),
            body = body,
            contentType = "multipart/form-data; boundary=$boundary",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, response))
        return extractHash(response)
            ?: throw ApiException("Server accepted upload but returned no hash: ${response.take(120)}")
    }

    /**
     * `GET /Marti/sync/content?hash=…` — download a previously uploaded
     * Mission Package / data package as raw bytes.
     */
    fun downloadDataPackage(hash: String): ByteArray {
        val (code, bytes) = httpRequestBytes(
            method = "GET",
            path = "/Marti/sync/content",
            query = listOf("hash" to hash),
        )
        if (code !in 200..299) {
            throw ApiException(httpReason(code, bytes.decodeToString().take(120)))
        }
        return bytes
    }

    /**
     * `PUT /Marti/api/missions/{name}/contents` — attach a previously
     * uploaded data-package hash to the named mission. Mission must
     * already exist; pair with [createMission] + [uploadDataPackage].
     */
    fun attachHashToMission(missionName: String, hash: String) {
        val payload = """{"hashes":["${jsonEscape(hash)}"]}""".toByteArray(Charsets.UTF_8)
        val (code, body) = httpRequest(
            method = "PUT",
            path = "/Marti/api/missions/${urlSegment(missionName)}/contents",
            query = emptyList(),
            body = payload,
            contentType = "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
    }

    /**
     * OTS ПСР: задания операции (`GET /api/missions/{name}/tasks`).
     * Empty on dialects without the route; requires Basic auth + mTLS like other /api.
     */
    fun getMissionTasks(missionName: String): List<TakMissionTask> {
        val (code, body) = httpGet("/api/missions/${urlSegment(missionName)}/tasks")
        if (code == 404) return emptyList()
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseMissionTasks(body)
    }

    /** OTS ПСР: состав выезда (`GET /api/missions/{name}/roster`), read-only in the field client. */
    fun getMissionRoster(missionName: String): List<TakMissionRosterEntry> {
        val (code, body) = httpGet("/api/missions/${urlSegment(missionName)}/roster")
        if (code == 404) return emptyList()
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseMissionRoster(body)
    }

    /** OTS ПСР: ориентировка пропавшего (`GET /api/missions/{name}/subject`). */
    fun getMissionSubject(missionName: String): TakMissionSubject? {
        val (code, body) = httpGet("/api/missions/${urlSegment(missionName)}/subject")
        if (code == 404) return null
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseMissionSubject(body)
    }

    /** PATCH task status (acked / done / …). */
    fun patchMissionTask(missionName: String, taskUid: String, status: String): TakMissionTask {
        val payload = """{"status":${jsonString(status)}}""".toByteArray(Charsets.UTF_8)
        val (code, body) = httpRequest(
            "PATCH",
            "/api/missions/${urlSegment(missionName)}/tasks/${urlSegment(taskUid)}",
            emptyList(),
            payload,
            "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseTaskFromWriteResponse(body)
            ?: TakMissionTask(uid = taskUid, title = "", status = status)
    }

    /** Create a mission task (field HQ). */
    fun createMissionTask(
        missionName: String,
        title: String,
        bodyText: String? = null,
        assignee: String? = null,
        sectorUid: String? = null,
        returnBy: String? = null,
    ): TakMissionTask {
        val payload = buildString {
            append('{')
            append("\"title\":").append(jsonString(title))
            if (!bodyText.isNullOrBlank()) append(",\"body\":").append(jsonString(bodyText))
            if (!assignee.isNullOrBlank()) append(",\"assignee\":").append(jsonString(assignee))
            if (!sectorUid.isNullOrBlank()) append(",\"sector_uid\":").append(jsonString(sectorUid))
            if (!returnBy.isNullOrBlank()) append(",\"return_by\":").append(jsonString(returnBy))
            append('}')
        }.toByteArray(Charsets.UTF_8)
        val (code, body) = httpRequest(
            "POST",
            "/api/missions/${urlSegment(missionName)}/tasks",
            emptyList(),
            payload,
            "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseTaskFromWriteResponse(body)
            ?: throw ApiException("Task create returned no task")
    }

    /** PATCH roster entry (check-in / status). */
    fun patchMissionRoster(missionName: String, entryUid: String, status: String): TakMissionRosterEntry {
        val payload = """{"status":${jsonString(status)}}""".toByteArray(Charsets.UTF_8)
        val (code, body) = httpRequest(
            "PATCH",
            "/api/missions/${urlSegment(missionName)}/roster/${urlSegment(entryUid)}",
            emptyList(),
            payload,
            "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseRosterFromWriteResponse(body)
            ?: TakMissionRosterEntry(uid = entryUid, displayName = "", status = status)
    }

    /** POST search sector polygon (HQ/lead). Coordinates: [[lat,lon],…]. */
    fun createSearchSector(
        name: String,
        coordinates: List<Pair<Double, Double>>,
        missionName: String? = null,
        assignedTo: String? = null,
        status: String = "active",
        uid: String? = null,
    ): TakSearchSector {
        val coordsJson = coordinates.joinToString(prefix = "[", postfix = "]") { (lat, lon) ->
            "[$lat,$lon]"
        }
        val payload = buildString {
            append('{')
            if (!uid.isNullOrBlank()) append("\"uid\":").append(jsonString(uid)).append(',')
            append("\"name\":").append(jsonString(name))
            append(",\"coordinates\":").append(coordsJson)
            append(",\"status\":").append(jsonString(status))
            if (!missionName.isNullOrBlank()) append(",\"mission_name\":").append(jsonString(missionName))
            if (!assignedTo.isNullOrBlank()) append(",\"assigned_to\":").append(jsonString(assignedTo))
            append('}')
        }.toByteArray(Charsets.UTF_8)
        val (code, body) = httpRequest(
            "POST",
            "/api/search_sectors",
            emptyList(),
            payload,
            "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseSectorFromWriteResponse(body)
            ?: TakSearchSector(uid = uid ?: "", name = name, status = status, missionName = missionName)
    }

    fun patchSearchSectorStatus(uid: String, status: String): TakSearchSector {
        val payload = """{"status":${jsonString(status)}}""".toByteArray(Charsets.UTF_8)
        val (code, body) = httpRequest(
            "PATCH",
            "/api/search_sectors/${urlSegment(uid)}",
            emptyList(),
            payload,
            "application/json",
        )
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseSectorFromWriteResponse(body)
            ?: TakSearchSector(uid = uid, name = "", status = status)
    }

    fun listSearchSectors(missionName: String? = null): List<TakSearchSector> {
        val q = if (missionName.isNullOrBlank()) emptyList()
        else listOf("mission" to missionName)
        val (code, body) = httpRequest("GET", "/api/search_sectors", q, null, null)
        if (code == 404) return emptyList()
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseSearchSectors(body)
    }

    /** HQ trail history for peer tracks overlay. */
    fun getTracks(sinceHours: Int = 6, maxPoints: Int = 2000): List<TakTrackTrail> {
        val since = java.time.Instant.now().minusSeconds(sinceHours.toLong() * 3600)
            .toString()
        val (code, body) = httpRequest(
            "GET",
            "/api/tracks",
            listOf(
                "since" to since,
                "min_interval_s" to "8",
                "max_points" to maxPoints.toString(),
            ),
            null,
            null,
        )
        if (code == 404) return emptyList()
        if (code !in 200..299) throw ApiException(httpReason(code, body))
        return parseTracks(body)
    }

    // ------------------------------------------------------------------
    // JSON parsing — tolerant of the three envelope shapes.
    // ------------------------------------------------------------------

    internal fun parseMissions(body: String): List<TakMissionInfo> {
        val arr = listEnvelope(body) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            TakMissionInfo(
                name = name,
                description = o.str("description"),
                creatorUid = o.str("creatorUid"),
                keywords = o.strList("keywords"),
                contentCount = (o["contents"] as? JsonArray)?.size ?: 0,
            )
        }
    }

    internal fun parseDataPackages(body: String): List<TakDataPackageInfo> {
        val arr = listEnvelope(body) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val hash = o.str("hash") ?: o.str("Hash") ?: return@mapNotNull null
            TakDataPackageInfo(
                hash = hash,
                name = o.str("name") ?: o.str("Name") ?: hash,
                mimeType = o.str("mimeType") ?: o.str("MIMEType"),
                size = o.long("size") ?: o.long("Size") ?: 0L,
                submitter = o.str("submitter") ?: o.str("SubmissionUser"),
            )
        }
    }

    internal fun parseMissionTasks(body: String): List<TakMissionTask> {
        val arr = listEnvelope(body) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val uid = o.str("uid") ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            TakMissionTask(
                uid = uid,
                title = title,
                body = o.str("body"),
                assignee = o.str("assignee"),
                sectorUid = o.str("sector_uid"),
                returnBy = o.str("return_by"),
                status = o.str("status") ?: "issued",
            )
        }
    }

    internal fun parseMissionRoster(body: String): List<TakMissionRosterEntry> {
        val arr = listEnvelope(body) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val uid = o.str("uid") ?: return@mapNotNull null
            val displayName = o.str("display_name") ?: return@mapNotNull null
            TakMissionRosterEntry(
                uid = uid,
                displayName = displayName,
                callsign = o.str("callsign"),
                phone = o.str("phone"),
                transport = o.str("transport"),
                skills = o.str("skills"),
                roleOnOp = o.str("role_on_op"),
                status = o.str("status") ?: "expected",
            )
        }
    }

    internal fun parseMissionSubject(body: String): TakMissionSubject? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val active = root["active"] as? JsonObject
        val fromActive = active?.let { subjectFromJson(it) }
        if (fromActive != null) return fromActive
        val arr = listEnvelope(body) ?: return null
        val first = arr.firstOrNull() as? JsonObject ?: return null
        return subjectFromJson(first)
    }

    private fun subjectFromJson(o: JsonObject): TakMissionSubject? {
        val uid = o.str("uid") ?: return null
        val fullName = o.str("full_name") ?: return null
        return TakMissionSubject(
            uid = uid,
            fullName = fullName,
            aliases = o.str("aliases"),
            age = o.str("age"),
            sex = o.str("sex"),
            clothing = o.str("clothing"),
            appearance = o.str("appearance"),
            distinguishing = o.str("distinguishing"),
            medical = o.str("medical"),
            lastSeenAt = o.str("last_seen_at"),
            lastSeenPlace = o.str("last_seen_place"),
            notes = o.str("notes"),
            photoHash = o.str("photo_hash"),
            status = o.str("status") ?: "active",
        )
    }

    private fun parseTaskFromWriteResponse(body: String): TakMissionTask? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val taskObj = (root["task"] as? JsonObject) ?: root
        val uid = taskObj.str("uid") ?: return null
        val title = taskObj.str("title") ?: ""
        return TakMissionTask(
            uid = uid,
            title = title,
            body = taskObj.str("body"),
            assignee = taskObj.str("assignee"),
            sectorUid = taskObj.str("sector_uid"),
            returnBy = taskObj.str("return_by"),
            status = taskObj.str("status") ?: "issued",
        )
    }

    private fun parseRosterFromWriteResponse(body: String): TakMissionRosterEntry? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val o = (root["entry"] as? JsonObject) ?: root
        val uid = o.str("uid") ?: return null
        return TakMissionRosterEntry(
            uid = uid,
            displayName = o.str("display_name") ?: "",
            callsign = o.str("callsign"),
            phone = o.str("phone"),
            transport = o.str("transport"),
            skills = o.str("skills"),
            roleOnOp = o.str("role_on_op"),
            status = o.str("status") ?: "expected",
        )
    }

    private fun parseSectorFromWriteResponse(body: String): TakSearchSector? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val o = (root["sector"] as? JsonObject) ?: root
        return sectorFromJson(o)
    }

    internal fun parseSearchSectors(body: String): List<TakSearchSector> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val arr = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["results"] ?: root["data"] ?: root["sectors"]) as? JsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { el -> sectorFromJson(el as? JsonObject ?: return@mapNotNull null) }
    }

    private fun sectorFromJson(o: JsonObject): TakSearchSector? {
        val uid = o.str("uid") ?: return null
        return TakSearchSector(
            uid = uid,
            name = o.str("name") ?: uid,
            status = o.str("status") ?: "active",
            missionName = o.str("mission_name"),
            assignedTo = o.str("assigned_to"),
        )
    }

    internal fun parseTracks(body: String): List<TakTrackTrail> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val arr = (root["results"] as? JsonArray) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val deviceUid = o.str("device_uid") ?: return@mapNotNull null
            val ptsArr = o["points"] as? JsonArray ?: return@mapNotNull null
            val points = ptsArr.mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                val lat = (po["lat"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val lon = (po["lon"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                    ?: return@mapNotNull null
                lat to lon
            }
            if (points.size < 2) return@mapNotNull null
            TakTrackTrail(
                deviceUid = deviceUid,
                callsign = o.str("callsign") ?: deviceUid,
                points = points,
            )
        }
    }

    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r") + "\""

    /**
     * Pull the list out of whichever envelope the dialect used:
     * `{data:[…]}` (TAK5.7/OTS), `{results:[…]}` (taky), or a bare array.
     */
    private fun listEnvelope(body: String): JsonArray? {
        val root: JsonElement = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        return when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] ?: root["results"]) as? JsonArray
            else -> null
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
            ?: (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    // ------------------------------------------------------------------
    // HTTP + mTLS (HttpsURLConnection, no extra deps).
    // ------------------------------------------------------------------

    /**
     * Generalised mTLS request used by the write endpoints. GET callers
     * (legacy [httpGet]) keep their narrow signature; write callers use
     * this so the body + content-type wiring is in one place.
     */
    internal fun httpRequest(
        method: String,
        path: String,
        query: List<Pair<String, String>>,
        body: ByteArray?,
        contentType: String?,
    ): Pair<Int, String> {
        val url = URL(baseUrl(path) + encodeQuery(query))
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = WRITE_READ_TIMEOUT_MS
            requestMethod = method
            doInput = true
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            val user = server.username
            val pass = server.password
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                val raw = "$user:$pass".toByteArray(Charsets.UTF_8)
                setRequestProperty("Authorization", "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP))
            }
            TakTls.configure(this, server, certVault)
        }
        return try {
            conn.connect()
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            code to text
        } catch (t: Throwable) {
            Log.w(TAG, "$method $path failed: ${t.javaClass.simpleName}: ${t.message}")
            throw ApiException(t.message ?: t.javaClass.simpleName, t)
        } finally {
            conn.disconnect()
        }
    }

    /** Like [httpRequest] but returns raw response bytes (for zip downloads). */
    internal fun httpRequestBytes(
        method: String,
        path: String,
        query: List<Pair<String, String>>,
    ): Pair<Int, ByteArray> {
        val url = URL(baseUrl(path) + encodeQuery(query))
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = WRITE_READ_TIMEOUT_MS
            requestMethod = method
            doInput = true
            setRequestProperty("Accept", "*/*")
            val user = server.username
            val pass = server.password
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                val raw = "$user:$pass".toByteArray(Charsets.UTF_8)
                setRequestProperty("Authorization", "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP))
            }
            TakTls.configure(this, server, certVault)
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.readBytes() ?: ByteArray(0)
            code to bytes
        } catch (t: Throwable) {
            Log.w(TAG, "$method $path (bytes) failed: ${t.javaClass.simpleName}: ${t.message}")
            throw ApiException(t.message ?: t.javaClass.simpleName, t)
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(path: String): Pair<Int, String> {
        val conn = (URL(baseUrl(path)).openConnection() as HttpsURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            // Some dialects (taky / OTS local-auth) accept basic auth in
            // addition to mTLS; send it when the operator provided creds.
            val user = server.username
            val pass = server.password
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                val raw = "$user:$pass".toByteArray(Charsets.UTF_8)
                setRequestProperty("Authorization", "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP))
            }
            TakTls.configure(this, server, certVault)
        }
        return try {
            conn.connect()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            code to text
        } catch (t: Throwable) {
            Log.w(TAG, "GET $path failed: ${t.javaClass.simpleName}: ${t.message}")
            throw ApiException(t.message ?: t.javaClass.simpleName, t)
        } finally {
            conn.disconnect()
        }
    }

    private fun httpReason(code: Int, body: String): String = when (code) {
        401 -> "Authentication required"
        403 -> "Access forbidden"
        404 -> "Not found"
        else -> "Server error ($code)" + body.take(80).let { if (it.isBlank()) "" else ": $it" }
    }

    class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val TAG = "TakRestApiClient"
        const val SECURE_API_PORT = 8443
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
        // Write endpoints (mission upload in particular) can carry
        // multi-MB payloads. Bump the read timeout for those without
        // affecting the snappier list reads.
        private const val WRITE_READ_TIMEOUT_MS = 60_000

        // ---- Exposed `internal` for unit-test instrumentation ----

        /**
         * Marti `missionupload` returns a URL like
         * `https://server/Marti/sync/content?hash=ABC123…` on success.
         * Older builds emit `Hash: ABC123` headers — handle both.
         * Returns null when no hash can be parsed.
         */
        internal fun extractHash(body: String): String? {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return null
            // Try URL query parse first.
            runCatching {
                val url = URL(trimmed)
                val q = url.query.orEmpty()
                q.split('&').forEach { pair ->
                    val (k, v) = pair.split('=', limit = 2).let { it[0] to it.getOrNull(1).orEmpty() }
                    if (k.equals("hash", ignoreCase = true) && v.isNotBlank()) return v
                }
            }
            // Fallback: any `hash=...` token anywhere in the response.
            val idx = trimmed.lowercase().indexOf("hash=")
            if (idx >= 0) {
                val tail = trimmed.substring(idx + "hash=".length)
                val token = tail.takeWhile { it != '&' && !it.isWhitespace() }
                if (token.isNotBlank()) return token
            }
            return null
        }

        /**
         * URL-encode the path segment between slashes — mission names
         * can contain spaces, slashes, and unicode that must round-trip
         * through Marti without rewriting the route.
         */
        internal fun urlSegment(raw: String): String =
            java.net.URLEncoder.encode(raw, Charsets.UTF_8).replace("+", "%20")

        internal fun encodeQuery(pairs: List<Pair<String, String>>): String {
            if (pairs.isEmpty()) return ""
            val sb = StringBuilder("?")
            pairs.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append('&')
                sb.append(java.net.URLEncoder.encode(k, Charsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(v, Charsets.UTF_8))
            }
            return sb.toString()
        }

        internal fun jsonEscape(raw: String): String =
            buildString {
                raw.forEach { c ->
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                    }
                }
            }

        /**
         * Build the `multipart/form-data` body Marti's missionupload
         * endpoint expects: one `assetfile` part containing the .zip
         * with `application/x-zip-compressed` content-type.
         */
        internal fun buildMultipartPackage(
            boundary: String,
            filename: String,
            zipBytes: ByteArray,
        ): ByteArray {
            val crlf = "\r\n"
            val header = StringBuilder()
                .append("--").append(boundary).append(crlf)
                .append("Content-Disposition: form-data; name=\"assetfile\"; filename=\"")
                .append(filename.replace("\"", "")).append('"').append(crlf)
                .append("Content-Type: application/x-zip-compressed").append(crlf)
                .append(crlf)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val footer = (crlf + "--" + boundary + "--" + crlf).toByteArray(Charsets.UTF_8)
            val out = java.io.ByteArrayOutputStream(header.size + zipBytes.size + footer.size)
            out.write(header)
            out.write(zipBytes)
            out.write(footer)
            return out.toByteArray()
        }
    }
}

/**
 * Bounding box for [TakRestApiClient.createMission]. Marti expects
 * `bbox=minLat,minLon,maxLat,maxLon` (no spaces) in WGS-84 degrees.
 */
data class MissionBbox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    val queryValue: String
        get() = "$minLat,$minLon,$maxLat,$maxLon"
}

// MARK: - Marti API models (minimal subset the sync UI needs)

data class TakMissionInfo(
    val name: String,
    val description: String? = null,
    val creatorUid: String? = null,
    val keywords: List<String> = emptyList(),
    val contentCount: Int = 0,
)

data class TakDataPackageInfo(
    val hash: String,
    val name: String,
    val mimeType: String? = null,
    val size: Long = 0L,
    val submitter: String? = null,
)

/** Штабное задание операции (OTS `/api/missions/.../tasks`). */
data class TakMissionTask(
    val uid: String,
    val title: String,
    val body: String? = null,
    val assignee: String? = null,
    val sectorUid: String? = null,
    val returnBy: String? = null,
    val status: String = "issued",
)

/** Участник состава выезда (OTS `/api/missions/.../roster`). */
data class TakMissionRosterEntry(
    val uid: String,
    val displayName: String,
    val callsign: String? = null,
    val phone: String? = null,
    val transport: String? = null,
    val skills: String? = null,
    val roleOnOp: String? = null,
    val status: String = "expected",
)

/** Ориентировка пропавшего (OTS `/api/missions/.../subject`). */
data class TakMissionSubject(
    val uid: String,
    val fullName: String,
    val aliases: String? = null,
    val age: String? = null,
    val sex: String? = null,
    val clothing: String? = null,
    val appearance: String? = null,
    val distinguishing: String? = null,
    val medical: String? = null,
    val lastSeenAt: String? = null,
    val lastSeenPlace: String? = null,
    val notes: String? = null,
    val photoHash: String? = null,
    val status: String = "active",
)

/** Search sector from OTS `/api/search_sectors`. */
data class TakSearchSector(
    val uid: String,
    val name: String,
    val status: String = "active",
    val missionName: String? = null,
    val assignedTo: String? = null,
)

/** One EUD trail from `/api/tracks`. */
data class TakTrackTrail(
    val deviceUid: String,
    val callsign: String,
    val points: List<Pair<Double, Double>>,
)
