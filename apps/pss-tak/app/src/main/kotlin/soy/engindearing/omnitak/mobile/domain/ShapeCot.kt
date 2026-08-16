package soy.engindearing.omnitak.mobile.domain

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import soy.engindearing.omnitak.mobile.data.Drawing
import soy.engindearing.omnitak.mobile.data.DrawingKind
import java.io.StringReader

/**
 * Parse ATAK freehand / polygon drawings (`u-d-f`, `u-d-r`, …) into [Drawing].
 */
object ShapeCot {
    fun isShapeType(type: String): Boolean =
        type == "u-d-f" || type.startsWith("u-d-")

    fun parseToDrawing(xml: String): Drawing? = runCatching {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var uid: String? = null
        var type: String? = null
        var callsign: String? = null
        var remarks: String? = null
        var linkPoints: String? = null
        var colorArgb: Int? = null

        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "event" -> {
                        uid = parser.getAttributeValue(null, "uid")
                        type = parser.getAttributeValue(null, "type")
                    }
                    "contact" -> callsign = parser.getAttributeValue(null, "callsign") ?: callsign
                    "remarks" -> remarks = parser.nextText() ?: remarks
                    "link" -> {
                        val pts = parser.getAttributeValue(null, "point")
                        if (!pts.isNullOrBlank()) linkPoints = pts
                    }
                    "strokeColor", "fillColor", "color" -> {
                        val v = parser.getAttributeValue(null, "value")
                            ?: parser.getAttributeValue(null, "argb")
                        colorArgb = v?.toIntOrNull() ?: colorArgb
                    }
                }
            }
            ev = parser.next()
        }

        if (uid == null || type == null || !isShapeType(type)) return@runCatching null
        val rawPts = linkPoints ?: return@runCatching null
        val points = rawPts.trim().split(Regex("\\s+")).mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size < 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            lat to lon
        }
        // Drop closing duplicate if present
        val ring = if (points.size >= 2 && points.first() == points.last()) {
            points.dropLast(1)
        } else {
            points
        }
        if (ring.size < 3) return@runCatching null

        val colorHex = colorArgb?.let {
            String.format("#%06X", it and 0xFFFFFF)
        } ?: "#00BCD4"

        val name = callsign?.takeIf { it.isNotBlank() }
            ?: remarks?.removePrefix("psr:sector")?.trim()?.takeIf { it.isNotBlank() }
            ?: "Sector"

        Drawing(
            id = uid,
            kind = DrawingKind.POLYGON,
            name = name,
            points = ring,
            colorHex = colorHex,
        )
    }.getOrNull()
}
