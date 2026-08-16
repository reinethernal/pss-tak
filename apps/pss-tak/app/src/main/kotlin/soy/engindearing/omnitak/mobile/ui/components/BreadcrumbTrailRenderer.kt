package soy.engindearing.omnitak.mobile.ui.components

import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import soy.engindearing.omnitak.mobile.domain.BreadcrumbTrailStore

/**
 * Native MapLibre polylines for [BreadcrumbTrailStore] (same Annotation path as drawings).
 */
object BreadcrumbTrailRenderer {
    private var polylines: List<Polyline> = emptyList()

    fun apply(map: MapLibreMap, trails: Map<String, List<BreadcrumbTrailStore.TrailPoint>>) {
        clear(map)
        val next = ArrayList<Polyline>()
        for ((uid, pts) in trails) {
            if (pts.size < 2) continue
            val color = BreadcrumbTrailStore.colorForUid(uid)
            runCatching {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(pts.map { LatLng(it.lat, it.lon) })
                        .color(color)
                        .width(3.5f)
                        .alpha(0.85f),
                )
            }.getOrNull()?.let { next.add(it) }
        }
        polylines = next
    }

    fun clear(map: MapLibreMap) {
        polylines.forEach { runCatching { map.removePolyline(it) } }
        polylines = emptyList()
    }
}
