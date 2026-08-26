package com.homelab.poc.thumbnail

import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.test.FakeTsnetGateway
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraThumbnailModelFactoryTest {

    @Test
    fun `create uses LOCAL getter when connection is null`() {
        val factory = CameraThumbnailModelFactory(
            connection = MutableStateFlow(null),
            gateway = FakeTsnetGateway(),
        )

        val model = factory.create("backyard", "http://frigate", 0)

        assertTrue(model is FrigateSnapshot)
        val snapshot = model as FrigateSnapshot
        assertEquals("backyard", snapshot.cameraId)
        assertEquals(TransportKind.LOCAL, snapshot.transport)
        assertEquals("http://frigate/api/backyard/latest.jpg?h=0", snapshot.snapshotUrl())
    }

    @Test
    fun `create uses the transport from the current connection`() {
        val factory = CameraThumbnailModelFactory(
            connection = MutableStateFlow(
                FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
            ),
            gateway = FakeTsnetGateway(),
        )

        val model = factory.create("hall", "http://frigate", 3)

        assertTrue(model is FrigateSnapshot)
        val snapshot = model as FrigateSnapshot
        assertEquals("http://frigate/api/hall/latest.jpg?h=3", snapshot.snapshotUrl())
        assertEquals(TransportKind.TAILSCALE, snapshot.transport)
        // The getter is selected based on the transport but remains an internal
        // implementation detail; the UI only receives the opaque Coil model.
        assertTrue(snapshot.getter is com.homelab.poc.core.frigate.TsnetHttpBytesGetter)
    }

    @Test
    fun `eventThumbnail builds the event thumbnail resource URL`() {
        val factory = CameraThumbnailModelFactory(
            connection = MutableStateFlow(
                FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            ),
            gateway = FakeTsnetGateway(),
        )

        val model = factory.eventThumbnail("1787072293.499881-q04v5h", "http://frigate")

        assertTrue(model is FrigateSnapshot)
        val snapshot = model as FrigateSnapshot
        assertEquals(
            "http://frigate/api/events/1787072293.499881-q04v5h/thumbnail.jpg",
            snapshot.snapshotUrl(),
        )
        // The event id is the stable cache identity for the Coil key.
        assertEquals("1787072293.499881-q04v5h", snapshot.cameraId)
    }

    @Test
    fun `eventThumbnail respects the current transport getter`() {
        val factory = CameraThumbnailModelFactory(
            connection = MutableStateFlow(
                FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
            ),
            gateway = FakeTsnetGateway(),
        )

        val model = factory.eventThumbnail("evt-1", "http://frigate")

        assertTrue((model as FrigateSnapshot).getter is com.homelab.poc.core.frigate.TsnetHttpBytesGetter)
        assertEquals(TransportKind.TAILSCALE, model.transport)
    }

    @Test
    fun `eventSnapshot builds the snapshot resource URL`() {
        val factory = CameraThumbnailModelFactory(
            connection = MutableStateFlow(
                FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            ),
            gateway = FakeTsnetGateway(),
        )

        val model = factory.eventSnapshot("1787072293.499881-q04v5h", "http://frigate")

        assertTrue(model is FrigateSnapshot)
        val snapshot = model as FrigateSnapshot
        assertEquals(
            "http://frigate/api/events/1787072293.499881-q04v5h/snapshot.jpg",
            snapshot.snapshotUrl(),
        )
        // The event id is the stable cache identity for the Coil key.
        assertEquals("1787072293.499881-q04v5h", snapshot.cameraId)
    }

    @Test
    fun `eventSnapshot respects the current transport getter`() {
        val factory = CameraThumbnailModelFactory(
            connection = MutableStateFlow(
                FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
            ),
            gateway = FakeTsnetGateway(),
        )

        val model = factory.eventSnapshot("evt-1", "http://frigate")

        assertTrue((model as FrigateSnapshot).getter is com.homelab.poc.core.frigate.TsnetHttpBytesGetter)
        assertEquals(TransportKind.TAILSCALE, model.transport)
    }
}
