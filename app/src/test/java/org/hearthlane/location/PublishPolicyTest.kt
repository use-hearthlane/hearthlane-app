package org.hearthlane.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure [shouldPublish] adaptive decision: time + distance based
 * publishing with a jitter guard, never tracking and never a history.
 */
class PublishPolicyTest {

    private val minInterval = 30_000L
    private val threshold = 100.0
    private val maxInterval = 5 * 60_000L

    private fun decide(
        nowMs: Long = 0L,
        neverPublished: Boolean = false,
        lastPublishAtMs: Long? = 0L,
        lastPublishAttemptAtMs: Long? = null,
        distance: Double? = 0.0,
        accuracy: Float? = 10f,
    ) = shouldPublish(
        nowMs = nowMs,
        neverPublished = neverPublished,
        lastPublishAtMs = lastPublishAtMs,
        lastPublishAttemptAtMs = lastPublishAttemptAtMs,
        distanceFromLastPublishedMeters = distance,
        pendingAccuracyMeters = accuracy,
        minPublishIntervalMs = minInterval,
        distanceThresholdMeters = threshold,
        maxPublishIntervalMs = maxInterval,
    )

    @Test
    fun `never published publishes`() {
        assertTrue(decide(neverPublished = true, distance = null))
    }

    @Test
    fun `thirty seconds with small movement does not publish`() {
        assertFalse(decide(nowMs = 30_000L, distance = 20.0))
    }

    @Test
    fun `sixty seconds with large movement publishes`() {
        assertTrue(decide(nowMs = 60_000L, distance = 150.0))
    }

    @Test
    fun `four minutes without movement does not publish`() {
        assertFalse(decide(nowMs = 4 * 60_000L, distance = 0.0))
    }

    @Test
    fun `five minutes without movement publishes via the max interval`() {
        assertTrue(decide(nowMs = 5 * 60_000L, distance = 0.0))
    }

    @Test
    fun `jitter within the accuracy does not publish`() {
        // 80 m of apparent movement with 100 m accuracy is GPS noise.
        assertFalse(decide(nowMs = 60_000L, distance = 80.0, accuracy = 100f))
    }

    @Test
    fun `movement beyond both threshold and accuracy publishes`() {
        assertTrue(decide(nowMs = 60_000L, distance = 120.0, accuracy = 100f))
    }

    @Test
    fun `minimum publish interval is respected even with movement`() {
        assertFalse(decide(nowMs = 29_999L, distance = 500.0, lastPublishAttemptAtMs = 0L))
    }

    @Test
    fun `a location without accuracy uses the plain threshold`() {
        assertFalse(decide(nowMs = 60_000L, distance = 80.0, accuracy = null))
        assertTrue(decide(nowMs = 60_000L, distance = 120.0, accuracy = null))
    }
}