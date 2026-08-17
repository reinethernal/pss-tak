package soy.engindearing.omnitak.mobile.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app phone livestream (MediaMTX RTSP), not a separate ICU APK. */
object LiveStreamState {
    enum class Status { Idle, Starting, Live, Error }

    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _bitrateKbps = MutableStateFlow(0L)
    val bitrateKbps: StateFlow<Long> = _bitrateKbps.asStateFlow()

    private val _publishUrl = MutableStateFlow<String?>(null)
    val publishUrl: StateFlow<String?> = _publishUrl.asStateFlow()

    fun reset() {
        _status.value = Status.Idle
        _error.value = null
        _bitrateKbps.value = 0
        _publishUrl.value = null
    }

    fun setStarting(url: String) {
        _publishUrl.value = url
        _error.value = null
        _status.value = Status.Starting
    }

    fun setLive() {
        _status.value = Status.Live
        _error.value = null
    }

    fun setError(message: String) {
        _status.value = Status.Error
        _error.value = message
    }

    fun setIdle() {
        _status.value = Status.Idle
        _bitrateKbps.value = 0
    }

    fun setBitrate(bitsPerSecond: Long) {
        _bitrateKbps.value = bitsPerSecond / 1000
    }
}
