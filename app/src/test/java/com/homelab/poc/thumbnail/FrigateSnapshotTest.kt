package com.homelab.poc.thumbnail

import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.test.FakeHttpBytesGetter
import org.junit.Assert.assertEquals
import org.junit.Test

class FrigateSnapshotTest {

    @Test
    fun `snapshotUrl uses camera id and refresh key`() {
        val snapshot = FrigateSnapshot(
            cameraId = "backyard",
            baseUrl = "http://site.omni.corp",
            refreshKey = 7,
            transport = TransportKind.LOCAL,
            getter = FakeHttpBytesGetter(),
        )

        assertEquals(
            "http://site.omni.corp/api/backyard/latest.jpg?h=7",
            snapshot.snapshotUrl(),
        )
    }

    @Test
    fun `snapshotUrl trims trailing slash from baseUrl`() {
        val snapshot = FrigateSnapshot(
            cameraId = "hall",
            baseUrl = "http://site.omni.corp/",
            refreshKey = 1,
            transport = TransportKind.LOCAL,
            getter = FakeHttpBytesGetter(),
        )

        assertEquals(
            "http://site.omni.corp/api/hall/latest.jpg?h=1",
            snapshot.snapshotUrl(),
        )
    }

    @Test
    fun `snapshotUrl uses the explicit resource URL when set`() {
        val snapshot = FrigateSnapshot(
            cameraId = "1787072293.499881-q04v5h",
            baseUrl = "http://site.omni.corp",
            refreshKey = 0,
            transport = TransportKind.LOCAL,
            getter = FakeHttpBytesGetter(),
            resourceUrl = "http://site.omni.corp/api/events/1787072293.499881-q04v5h/thumbnail.jpg",
        )

        assertEquals(
            "http://site.omni.corp/api/events/1787072293.499881-q04v5h/thumbnail.jpg",
            snapshot.snapshotUrl(),
        )
    }
}
