package soy.engindearing.adsb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import org.maplibre.android.maps.MapLibreMap
import soy.engindearing.omnitak.plugin.LocalMapEngineHandle

/**
 * Host-provided gate for the Layers → «Воздух (ADS-B)» toggle.
 * Defaults to visible so plugins/tests without a provider still render.
 */
val LocalAdsbLayerVisible = compositionLocalOf { true }

/**
 * The ADS-B plugin's registered map overlay. Renders no Compose UI of its own —
 * it bridges the plugin's aircraft state into the MapLibre GL layers via
 * [AircraftLayer].
 */
@Composable
fun AdsbMapOverlay(service: AdsbService) {
    val mapHandle = LocalMapEngineHandle.current
    val map = mapHandle as? MapLibreMap
    val aircraft by service.aircraft.collectAsState()
    val active by service.active.collectAsState()
    val layerVisible = LocalAdsbLayerVisible.current

    LaunchedEffect(map, aircraft, active, layerVisible) {
        val m = map ?: return@LaunchedEffect
        AircraftLayer.update(m, if (active && layerVisible) aircraft else emptyList())
    }
}
