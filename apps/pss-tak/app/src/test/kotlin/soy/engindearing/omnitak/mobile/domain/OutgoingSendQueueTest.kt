package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OutgoingSendQueueTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun enqueueAndFlushCot() = kotlinx.coroutines.runBlocking {
        val q = OutgoingSendQueue(tmp.root)
        q.enqueueCot("<event uid='a'/>")
        q.enqueueCot("<event uid='b'/>")
        assertEquals(2, q.pendingCount.value)

        val sent = mutableListOf<String>()
        val n = q.flush(
            sendCot = { xml -> sent.add(xml); true },
            publishPhoto = { _, _, _, _, _, _ -> false },
        )
        assertEquals(2, n)
        assertEquals(0, q.pendingCount.value)
        assertEquals(listOf("<event uid='a'/>", "<event uid='b'/>"), sent)
    }

    @Test
    fun persistsAcrossInstances() {
        val dir = tmp.root
        OutgoingSendQueue(dir).enqueueCot("<event/>")
        val reloaded = OutgoingSendQueue(dir)
        assertEquals(1, reloaded.pendingCount.value)
    }

    @Test
    fun photoEnqueueWritesFile() {
        val q = OutgoingSendQueue(tmp.root)
        q.enqueuePhoto(byteArrayOf(1, 2, 3), 1.0, 2.0, "P", "r", eventUid = "uid-1")
        assertEquals(1, q.pendingCount.value)
        val rel = q.items.value.first().photoRelPath
        assertTrue(File(tmp.root, "photos/$rel").isFile)
    }
}
