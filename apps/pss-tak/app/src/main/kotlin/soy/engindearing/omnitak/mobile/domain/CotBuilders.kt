package soy.engindearing.omnitak.mobile.domain

import soy.engindearing.omnitak.mobile.data.CoTEvent
import soy.engindearing.omnitak.mobile.data.CotXml
import java.util.UUID

/**
 * Plain-XML builders for the CoT subset OmniTAK ships. Kept separate
 * from CoTEvent (parse-side) so the send-side message shapes are one
 * obvious place to look. Envelope assembly + escaping live in
 * [CotXml]; this object owns the per-message-type detail blocks.
 */
object CotBuilders {

    /** ISO-8601 UTC timestamp for "now". */
    private fun nowIso(): String = CotXml.isoSeconds()

    /** ISO-8601 UTC timestamp for a given offset from now. */
    private fun isoOffset(seconds: Long): String =
        CotXml.isoSeconds(System.currentTimeMillis() + seconds * 1000L)

    /**
     * `t-x-d-d` "Tasking Delete Data" — the canonical TAK delete
     * primitive. Sent to the server, it propagates to other EUDs which
     * remove the target marker from their map. The deleter's own UID
     * goes in the event's `uid` field; the target UID lives on the
     * `<link>` element with `relation="p-p"`.
     */
    fun buildDeleteEvent(targetUid: String, senderUid: String): String {
        val now = nowIso()
        val detail = buildString {
            append("<detail>")
            append("<link uid=\"").append(CotXml.escape(targetUid)).append("\" relation=\"p-p\"/>")
            append("<__forcedelete/>")
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = senderUid,
            type = "t-x-d-d",
            how = "h-g-i-g-o",
            lat = 0.0, lon = 0.0, hae = 0.0,
            timeIso = now,
            staleIso = isoOffset(60),
            detailXml = detail,
        )
    }

    /**
     * Resurrect a CoTEvent as a fresh CoT XML string. Used by the
     * "Send to Contacts" path so we can re-broadcast the selection to
     * a specific recipient by adding `<dest uid="..."/>` to its detail.
     * Falls back to event.rawXml when present (preserves any extension
     * detail elements like <chat>, <usericon>, mil-std symbology).
     */
    fun rebuildEvent(event: CoTEvent, destUids: List<String>): String {
        // If the original parsed XML is around, re-wrap it with the
        // injected <dest> elements. Otherwise synthesize a minimal CoT.
        val now = event.timeIso ?: nowIso()
        val stale = event.staleIso ?: isoOffset(120)
        val detail = buildString {
            append("<detail>")
            append("<contact")
            event.callsign?.let { append(" callsign=\"").append(CotXml.escape(it)).append('"') }
            append("/>")
            // ATAK / iTAK render FEMA markers (and any custom-glyph marker)
            // off the `iconsetpath` detail — emit it when present so peers
            // with the catalog show the right symbol (#29). Spot Map points
            // (#98) additionally carry their swatch in <color argb>; ATAK reads
            // the dot colour from there, not the CoT type.
            event.iconsetPath?.takeIf { it.isNotBlank() }?.let {
                append("<usericon iconsetpath=\"").append(CotXml.escape(it)).append("\"/>")
            }
            event.colorArgb?.let {
                append("<color argb=\"").append(it).append("\"/>")
            }
            if (event.remarks.isNotBlank()) {
                append("<remarks>").append(CotXml.escape(event.remarks)).append("</remarks>")
            }
            event.courseHeading?.let {
                append("<track course=\"").append(it).append("\" speed=\"0.0\"/>")
            }
            // Photo marker announce (ATAK Quick Pic / Mission Package)
            val sha = event.fileshareSha256
            val url = event.fileshareUrl
            if (!sha.isNullOrBlank() && !url.isNullOrBlank()) {
                append("<fileshare")
                append(" filename=\"").append(CotXml.escape(event.fileshareFilename ?: "photo.jpg")).append('"')
                append(" name=\"").append(CotXml.escape(event.callsign ?: "Photo")).append('"')
                append(" senderCallsign=\"").append(CotXml.escape(event.callsign ?: "PSS")).append('"')
                append(" senderUid=\"").append(CotXml.escape(event.uid)).append('"')
                append(" senderUrl=\"").append(CotXml.escape(url)).append('"')
                append(" sha256=\"").append(CotXml.escape(sha)).append('"')
                append(" sizeInBytes=\"0\"")
                append("/>")
            }
            destUids.forEach { append("<dest uid=\"").append(CotXml.escape(it)).append("\"/>") }
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = event.uid,
            type = event.type,
            how = "h-g-i-g-o",
            lat = event.lat, lon = event.lon,
            hae = event.hae, ce = event.ce, le = event.le,
            timeIso = now,
            staleIso = stale,
            detailXml = detail,
        )
    }

    /**
     * UAS Position Location Information (GAP-XXX UAS support).
     *
     * MIL-STD-2525D friendly-air codes per the official ATAK UAS Tool 13.0
     * spec analysis: rotary-wing UAS is `a-f-A-M-H-Q`, fixed-wing is
     * `a-f-A-M-F-Q`. Vendors / autopilots that don't self-identify a wing
     * shape are emitted as rotary by default since that's what most
     * MAVLink platforms in the field are.
     *
     * Optional `videoUri` adds the `<video><ConnectionEntry>` detail
     * (RTSP URL) that lets other ATAK / iTAK / OmniTAK clients pick up
     * the drone's FMV stream from the same map marker.
     */
    fun buildUasPliEvent(
        uid: String,
        callsign: String,
        latDeg: Double,
        lonDeg: Double,
        haeMeters: Double,
        headingDeg: Double? = null,
        groundSpeedMps: Double? = null,
        videoUri: String? = null,
        isFixedWing: Boolean = false,
        operatorUid: String? = null,
        staleSeconds: Long = 30,
        /** Platform class — drives the MIL-STD-2525 type. Air → friendly
         *  air UAS; ground → friendly ground vehicle (a-f-G-E-V-U);
         *  surface → friendly sea-surface (a-f-S-X). Defaults to AIR for
         *  back-compat with existing UAS callers. */
        vehicleClass: soy.engindearing.omnitak.mobile.data.uas.VehicleClass =
            soy.engindearing.omnitak.mobile.data.uas.VehicleClass.AIR,
    ): String {
        val type = when (vehicleClass) {
            soy.engindearing.omnitak.mobile.data.uas.VehicleClass.GROUND -> "a-f-G-E-V-U"
            soy.engindearing.omnitak.mobile.data.uas.VehicleClass.SURFACE,
            soy.engindearing.omnitak.mobile.data.uas.VehicleClass.SUB -> "a-f-S-X"
            else -> if (isFixedWing) "a-f-A-M-F-Q" else "a-f-A-M-H-Q"
        }
        val detail = buildString {
            append("<detail>")
            append("<contact callsign=\"").append(CotXml.escape(callsign)).append("\"/>")
            append("<__group name=\"Cyan\" role=\"UAV\"/>")
            if (headingDeg != null || groundSpeedMps != null) {
                append("<track course=\"").append(headingDeg ?: 0.0)
                append("\" speed=\"").append(groundSpeedMps ?: 0.0).append("\"/>")
            }
            if (!videoUri.isNullOrBlank()) {
                append("<video><ConnectionEntry uid=\"").append(CotXml.escape(uid))
                append("-VID\" protocol=\"rtsp\" address=\"").append(CotXml.escape(videoUri))
                append("\"/></video>")
            }
            if (!operatorUid.isNullOrBlank()) {
                append("<link uid=\"").append(CotXml.escape(operatorUid))
                append("\" relation=\"p-p\" type=\"a-f-G-U-C\"/>")
            }
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = uid,
            type = type,
            how = "m-g",
            lat = latDeg, lon = lonDeg, hae = haeMeters,
            timeIso = nowIso(),
            staleIso = isoOffset(staleSeconds),
            detailXml = detail,
        )
    }

    /** Fresh random TAK-style UID. */
    fun newUid(): String = UUID.randomUUID().toString()

    /**
     * Photo geo-marker (`b-i-x-i`) with optional `<fileshare>` after Marti upload.
     * Mirrors ATAK Quick Pic wire shape so peers and OTS can resolve the image.
     */
    fun buildPhotoMarkerEvent(
        uid: String,
        callsign: String,
        lat: Double,
        lon: Double,
        remarks: String = "",
        fileshareSha256: String? = null,
        fileshareUrl: String? = null,
        fileshareFilename: String = "photo.jpg",
        sizeInBytes: Long = 0L,
        senderUid: String = uid,
        senderCallsign: String = callsign,
        staleSeconds: Long = 3600 * 24 * 7,
    ): String {
        val now = nowIso()
        val detail = buildString {
            append("<detail>")
            append("<contact callsign=\"").append(CotXml.escape(callsign)).append("\"/>")
            if (remarks.isNotBlank()) {
                append("<remarks>").append(CotXml.escape(remarks)).append("</remarks>")
            }
            if (!fileshareSha256.isNullOrBlank() && !fileshareUrl.isNullOrBlank()) {
                append("<fileshare")
                append(" filename=\"").append(CotXml.escape(fileshareFilename)).append('"')
                append(" name=\"").append(CotXml.escape(callsign)).append('"')
                append(" senderCallsign=\"").append(CotXml.escape(senderCallsign)).append('"')
                append(" senderUid=\"").append(CotXml.escape(senderUid)).append('"')
                append(" senderUrl=\"").append(CotXml.escape(fileshareUrl)).append('"')
                append(" sha256=\"").append(CotXml.escape(fileshareSha256)).append('"')
                append(" sizeInBytes=\"").append(sizeInBytes).append('"')
                append("/>")
            }
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = uid,
            type = PhotoMarkerPackage.IMAGE_TYPE,
            how = "h-g-i-g-o",
            lat = lat,
            lon = lon,
            timeIso = now,
            staleIso = isoOffset(staleSeconds),
            detailXml = detail,
        )
    }

    /**
     * ATAK-style freehand / polygon drawing (`u-d-f`) with vertex list on
     * `<link point="lat,lon,hae …"/>`. Used for search sectors (ПСР).
     */
    fun buildPolygonDrawingEvent(
        uid: String,
        name: String,
        points: List<Pair<Double, Double>>,
        remarks: String = "psr:sector",
        colorArgb: Int = 0x8000BCD4.toInt(),
        strokeWeight: Int = 3,
        staleSeconds: Long = 3600 * 24 * 14,
    ): String {
        require(points.size >= 3) { "polygon needs ≥3 vertices" }
        val lat0 = points.first().first
        val lon0 = points.first().second
        val closed = if (points.first() == points.last()) points else points + points.first()
        val linkPoints = closed.joinToString(" ") { (lat, lon) ->
            String.format(java.util.Locale.US, "%.6f,%.6f,0.0", lat, lon)
        }
        val now = nowIso()
        val detail = buildString {
            append("<detail>")
            append("<contact callsign=\"").append(CotXml.escape(name.ifBlank { "Sector" })).append("\"/>")
            append("<link point=\"").append(CotXml.escape(linkPoints)).append("\"/>")
            append("<strokeColor value=\"").append(colorArgb).append("\"/>")
            append("<strokeWeight value=\"").append(strokeWeight).append("\"/>")
            append("<fillColor value=\"").append(colorArgb).append("\"/>")
            append("<labels_on value=\"true\"/>")
            if (remarks.isNotBlank()) {
                append("<remarks>").append(CotXml.escape(remarks)).append("</remarks>")
            }
            append("</detail>")
        }
        return CotXml.buildEvent(
            uid = uid,
            type = "u-d-f",
            how = "h-e",
            lat = lat0,
            lon = lon0,
            timeIso = now,
            staleIso = isoOffset(staleSeconds),
            detailXml = detail,
        )
    }

    /** XML escape — delegates to the shared [CotXml.escape]. Kept as a
     *  public alias because non-CoT XML emitters (KML/data-package
     *  exporters) call it too. */
    fun xmlEscape(s: String): String = CotXml.escape(s)
}
