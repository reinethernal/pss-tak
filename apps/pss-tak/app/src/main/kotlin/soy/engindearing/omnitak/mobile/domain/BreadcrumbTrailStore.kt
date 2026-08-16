package soy.engindearing.omnitak.mobile.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ring-buffer GPS breadcrumbs for self + peer PLI (SAR «Треки»).
 * Not published as a separate CoT type — HQ uses server `points` history.
 */
class BreadcrumbTrailStore(
    private val maxPointsPerUid: Int = MAX_POINTS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val minDistanceM: Double = MIN_DISTANCE_M,
) {
    data class TrailPoint(val lat: Double, val lon: Double, val tsMs: Long)

    private val _trails = MutableStateFlow<Map<String, List<TrailPoint>>>(emptyMap())
    val trails: StateFlow<Map<String, List<TrailPoint>>> = _trails.asStateFlow()

    fun add(uid: String, lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()) {
        if (uid.isBlank() || lat.isNaN() || lon.isNaN()) return
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return
        _trails.update { current ->
            val prev = current[uid].orEmpty()
            val last = prev.lastOrNull()
            if (last != null) {
                if (nowMs - last.tsMs < minIntervalMs) return@update current
                if (haversineM(last.lat, last.lon, lat, lon) < minDistanceM) return@update current
            }
            val next = (prev + TrailPoint(lat, lon, nowMs)).takeLast(maxPointsPerUid)
            current + (uid to next)
        }
    }

    /** Replace/seed a trail from HQ `/api/tracks` (no throttle). */
    fun seed(uid: String, points: List<Pair<Double, Double>>) {
        if (uid.isBlank() || points.size < 2) return
        val now = System.currentTimeMillis()
        val trail = points.mapIndexed { i, (lat, lon) ->
            TrailPoint(lat, lon, now - (points.size - i) * 1000L)
        }.takeLast(maxPointsPerUid)
        _trails.update { it + (uid to trail) }
    }

    fun clear() {
        _trails.value = emptyMap()
    }

    companion object {
        const val MAX_POINTS = 500
        const val MIN_INTERVAL_MS = 10_000L
        const val MIN_DISTANCE_M = 15.0

        fun colorForUid(uid: String): Int {
            var h = 0
            for (c in uid) h = 31 * h + c.code
            val hue = ((h ushr 1) % 360).toFloat()
            return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.85f))
        }

        private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
