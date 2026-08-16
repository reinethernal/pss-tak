package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import soy.engindearing.omnitak.mobile.data.DrawingKind
import soy.engindearing.omnitak.mobile.data.SarPointCatalog

class SarAndShapeCotTest {
    @Test
    fun sarCatalog_remarksPrefix() {
        val lkp = SarPointCatalog.pointFor(SarPointCatalog.Kind.LKP)!!
        assertEquals("psr:lkp", lkp.remarksPrefix())
        assertEquals(lkp, SarPointCatalog.fromRemarksOrCallsign("psr:lkp trail", null))
        assertEquals(lkp, SarPointCatalog.fromRemarksOrCallsign(null, "LKP"))
    }

    @Test
    fun polygonCot_roundTrip() {
        val pts = listOf(
            55.0 to 37.0,
            55.1 to 37.0,
            55.1 to 37.1,
            55.0 to 37.1,
        )
        val xml = CotBuilders.buildPolygonDrawingEvent(
            uid = "sector-1",
            name = "Alpha",
            points = pts,
            remarks = "psr:sector assigned:Team2",
        )
        assertTrue(xml.contains("u-d-f"))
        val drawing = ShapeCot.parseToDrawing(xml)
        assertNotNull(drawing)
        assertEquals(DrawingKind.POLYGON, drawing!!.kind)
        assertEquals("sector-1", drawing.id)
        assertTrue(drawing.points.size >= 3)
    }
}
