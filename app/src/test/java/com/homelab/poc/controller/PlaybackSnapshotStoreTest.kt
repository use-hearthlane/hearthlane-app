package com.homelab.poc.controller

import com.homelab.poc.core.playback.PlaybackStatus
import com.homelab.poc.core.playback.PlayerMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSnapshotStoreTest {

    private val store = PlaybackSnapshotStore()

    @Test
    fun `starts with an empty snapshot`() {
        assertEquals(PlaybackSnapshot(), store.snapshot.value)
    }

    @Test
    fun `records a playing session with its metrics`() {
        store.record(
            status = PlaybackStatus.Playing,
            metrics = PlayerMetrics(
                firstFrameElapsedMs = 1200,
                errorCount = 1,
                bytesTransferred = 5000,
            ),
            recoveryCount = 2,
        )

        val snapshot = store.snapshot.value
        assertEquals("playing", snapshot.playbackState)
        assertEquals(1200L, snapshot.firstFrameElapsedMs)
        assertEquals(1, snapshot.errorCount)
        assertEquals(5000L, snapshot.bytesTransferred)
        assertEquals(2, snapshot.recoveryCount)
    }

    @Test
    fun `counters accumulate across sessions while first frame keeps the latest value`() {
        store.record(
            PlaybackStatus.Playing,
            PlayerMetrics(firstFrameElapsedMs = 900, errorCount = 0, bytesTransferred = 1000),
            0,
        )
        store.record(
            PlaybackStatus.Playing,
            PlayerMetrics(firstFrameElapsedMs = 1500, errorCount = 2, bytesTransferred = 8000),
            3,
        )

        val snapshot = store.snapshot.value
        assertEquals("bytes must accumulate across sessions", 9000L, snapshot.bytesTransferred)
        assertEquals("errors must accumulate across sessions", 2, snapshot.errorCount)
        assertEquals("recoveries must accumulate across sessions", 3, snapshot.recoveryCount)
        assertEquals("the latest non-null first-frame value wins", 1500L, snapshot.firstFrameElapsedMs)
    }

    @Test
    fun `error state records its message`() {
        store.record(
            PlaybackStatus.Error("stream failed"),
            PlayerMetrics(errorCount = 3),
            0,
        )

        assertEquals("error", store.snapshot.value.playbackState)
        assertEquals("stream failed", store.snapshot.value.lastError)
    }

    @Test
    fun `last error is retained when a later session ends on a non-error state`() {
        store.record(PlaybackStatus.Error("session expired"), PlayerMetrics(errorCount = 1), 1)
        store.record(PlaybackStatus.Playing, PlayerMetrics(), 0)

        assertEquals("playing", store.snapshot.value.playbackState)
        assertEquals("session expired", store.snapshot.value.lastError)
    }

    @Test
    fun `first frame without a value never overwrites an earlier one`() {
        store.record(PlaybackStatus.Playing, PlayerMetrics(firstFrameElapsedMs = 1100), 0)
        store.record(PlaybackStatus.Playing, PlayerMetrics(), 0)

        assertEquals(1100L, store.snapshot.value.firstFrameElapsedMs)
    }

    @Test
    fun `an ended session maps to the ended label`() {
        store.record(PlaybackStatus.Ended, PlayerMetrics(), 0)

        assertEquals("ended", store.snapshot.value.playbackState)
    }
}
