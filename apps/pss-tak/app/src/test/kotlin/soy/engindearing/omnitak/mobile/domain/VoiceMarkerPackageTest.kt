package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceMarkerPackageTest {
    @Test
    fun zipRoundTrip() {
        val uid = "voice-uid-1"
        val audio = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val zip = VoiceMarkerPackage.buildZip(uid, audio)
        val extracted = VoiceMarkerPackage.extractAudio(zip, preferUid = uid)
        assertNotNull(extracted)
        assertEquals(audio.toList(), extracted!!.second.toList())
    }
}
