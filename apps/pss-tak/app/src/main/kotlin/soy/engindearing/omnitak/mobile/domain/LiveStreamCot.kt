package soy.engindearing.omnitak.mobile.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import soy.engindearing.omnitak.mobile.OmniTAKApp

/**
 * While the phone camera is live, announce the RTSP URL on the TAK
 * connection as CoT `b-m-p-s-p-loc` + `__video` so HQ / ATAK Video can play it.
 */
class LiveStreamCot(private val app: OmniTAKApp, private val scope: CoroutineScope) {
    private var job: Job? = null

    fun start(host: String, port: Int, path: String) {
        stop()
        job = scope.launch {
            while (isActive) {
                val prefs = app.userPrefsStore.prefs.first()
                val fix = app.locationProvider.effectiveFix()
                val uid = "${prefs.selfUid.ifBlank { "ANDROID-stream" }}-VID"
                val xml = CotBuilders.buildPhoneStreamEvent(
                    uid = uid,
                    callsign = path.ifBlank { prefs.callsign },
                    lat = fix?.lat ?: 0.0,
                    lon = fix?.lon ?: 0.0,
                    hae = fix?.altitudeM ?: 0.0,
                    host = host,
                    port = port,
                    path = path,
                )
                runCatching { app.serverManager.sendCoT(xml, enqueueIfOffline = false) }
                delay(5_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
