package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMarkerPackageTest {
    @Test
    fun buildZip_roundTripsImage() {
        val uid = "photo-uid-1"
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val zip = PhotoMarkerPackage.buildZip(uid, jpeg)
        assertTrue(zip.size > jpeg.size)
        val extracted = PhotoMarkerPackage.extractImage(zip, preferUid = uid)
        assertNotNull(extracted)
        assertTrue(extracted!!.first.contains(uid))
        assertEquals(jpeg.toList(), extracted.second.toList())
    }
}
