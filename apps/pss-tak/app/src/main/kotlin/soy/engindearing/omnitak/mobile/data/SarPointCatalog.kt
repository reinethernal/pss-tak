package soy.engindearing.omnitak.mobile.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Civil SAR / ПСР point presets shared by PSS TAK and the web HQ map.
 *
 * Wire shape (ATAK-compatible without a custom iconset):
 * - CoT type: friendly ground installation `a-f-G-I-U-T`
 * - Callsign: stable short code (LKP, PLS, …)
 * - Remarks: always starts with `psr:<code>` so HQ filters and parsers
 *   can classify without relying on FEMA icon packs.
 * - Optional Spot Map colour via [argbHex] for map readability.
 */
object SarPointCatalog {

    enum class Kind(val code: String, val callsign: String) {
        LKP("lkp", "LKP"),
        PLS("pls", "PLS"),
        IPP("ipp", "IPP"),
        CHECKED("checked", "CHECKED"),
        DANGER("danger", "DANGER"),
        RALLY("rally", "RALLY"),
    }

    data class SarPoint(
        val kind: Kind,
        val labelRu: String,
        val labelEn: String,
        val image: ImageVector,
        val accent: Color,
        /** Opaque ARGB hex for Spot-style colouring when peers support it. */
        val argbHex: String,
        val cotType: String = "a-f-G-I-U-T",
    ) {
        fun remarksPrefix(): String = "psr:${kind.code}"

        fun buildRemarks(extra: String = ""): String {
            val base = remarksPrefix()
            val trimmed = extra.trim()
            return if (trimmed.isEmpty()) base else "$base $trimmed"
        }
    }

    private val entries: Map<Kind, SarPoint> = mapOf(
        Kind.LKP to SarPoint(
            kind = Kind.LKP,
            labelRu = "Последнее известное",
            labelEn = "Last Known Point",
            image = Icons.Filled.Place,
            accent = Color(0xFFE91E63),
            argbHex = "FFE91E63",
        ),
        Kind.PLS to SarPoint(
            kind = Kind.PLS,
            labelRu = "Где видели",
            labelEn = "Place Last Seen",
            image = Icons.Filled.Visibility,
            accent = Color(0xFF9C27B0),
            argbHex = "FF9C27B0",
        ),
        Kind.IPP to SarPoint(
            kind = Kind.IPP,
            labelRu = "Старт планирования",
            labelEn = "Initial Planning Point",
            image = Icons.Filled.Flag,
            accent = Color(0xFF2196F3),
            argbHex = "FF2196F3",
        ),
        Kind.CHECKED to SarPoint(
            kind = Kind.CHECKED,
            labelRu = "Проверено",
            labelEn = "Checked / Cleared",
            image = Icons.Filled.CheckCircle,
            accent = Color(0xFF4CAF50),
            argbHex = "FF4CAF50",
        ),
        Kind.DANGER to SarPoint(
            kind = Kind.DANGER,
            labelRu = "Опасность",
            labelEn = "Hazard / Danger",
            image = Icons.Filled.Warning,
            accent = Color(0xFFF44336),
            argbHex = "FFF44336",
        ),
        Kind.RALLY to SarPoint(
            kind = Kind.RALLY,
            labelRu = "Сборка",
            labelEn = "Rally / Assembly",
            image = Icons.Filled.Groups,
            accent = Color(0xFFFF9800),
            argbHex = "FFFF9800",
        ),
    )

    val all: List<SarPoint> get() = Kind.entries.mapNotNull(entries::get)

    fun pointFor(kind: Kind): SarPoint? = entries[kind]

    fun fromRemarksOrCallsign(remarks: String?, callsign: String?): SarPoint? {
        val r = remarks.orEmpty().lowercase()
        val match = Regex("""psr:([a-z]+)""").find(r)
        if (match != null) {
            val code = match.groupValues[1]
            return Kind.entries.firstOrNull { it.code == code }?.let { entries[it] }
        }
        val cs = callsign.orEmpty().uppercase()
        return Kind.entries.firstOrNull { it.callsign == cs }?.let { entries[it] }
    }
}
