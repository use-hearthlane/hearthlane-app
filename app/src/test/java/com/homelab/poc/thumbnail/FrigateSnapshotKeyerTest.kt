package com.homelab.poc.thumbnail

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import com.homelab.poc.core.frigate.TransportKind
import com.homelab.poc.test.FakeHttpBytesGetter
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)

/**
 * Tests for [FrigateSnapshotKeyer].
 *
 * These tests run on the JVM with Robolectric so they can create a real Coil
 * [ImageLoader] and assert that memory cache keys are built from stable fields
 * only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FrigateSnapshotKeyerTest {

    private lateinit var context: Context
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        imageLoader = ImageLoader.Builder(context)
            .components {
                add(FrigateSnapshotKeyer())
                add(FakeColorFetcher.Factory())
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
        SingletonImageLoader.reset()
    }

    @Test
    fun `same camera baseUrl refreshKey and transport produce same key`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val key1 = keyer.key(snapshot(cameraId = "backyard", transport = TransportKind.LOCAL), options)
        val key2 = keyer.key(snapshot(cameraId = "backyard", transport = TransportKind.LOCAL), options)

        assertEquals(key1, key2)
    }

    @Test
    fun `different cameraId produces different key`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val key1 = keyer.key(snapshot(cameraId = "backyard"), options)
        val key2 = keyer.key(snapshot(cameraId = "hall"), options)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `different baseUrl produces different key`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val key1 = keyer.key(snapshot(baseUrl = "http://frigate-a"), options)
        val key2 = keyer.key(snapshot(baseUrl = "http://frigate-b"), options)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `different refreshKey produces different key`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val key1 = keyer.key(snapshot(refreshKey = 0), options)
        val key2 = keyer.key(snapshot(refreshKey = 1), options)

        assertNotEquals(key1, key2)
    }

    @Test
    fun `LOCAL and TAILSCALE produce different keys`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val keyLocal = keyer.key(snapshot(transport = TransportKind.LOCAL), options)
        val keyTailscale = keyer.key(snapshot(transport = TransportKind.TAILSCALE), options)

        assertNotEquals(keyLocal, keyTailscale)
    }

    @Test
    fun `getter instance identity does not affect key`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val snapshot1 = snapshot(getter = FakeHttpBytesGetter())
        val snapshot2 = snapshot(getter = FakeHttpBytesGetter())

        assertNotEquals(snapshot1.getter, snapshot2.getter)
        assertNotEquals(snapshot1.getter.hashCode(), snapshot2.getter.hashCode())
        assertEquals(keyer.key(snapshot1, options), keyer.key(snapshot2, options))
    }

    @Test
    fun `key format includes baseUrl cameraId refreshKey and transport name`() {
        val keyer = FrigateSnapshotKeyer()
        val options = createOptions()

        val key = keyer.key(
            snapshot(
                baseUrl = "http://frigate",
                cameraId = "backyard",
                refreshKey = 7,
                transport = TransportKind.TAILSCALE,
            ),
            options,
        )

        assertEquals("http://frigate\u0000backyard\u00007\u0000TAILSCALE", key)
    }

    @Test
    fun `refresh forces a new fetch by changing the cache key`() = runTest {
        // Execute a request for refreshKey = 0.
        val result0 = executeRequest(cameraId = "backyard", refreshKey = 0)
        assertTrue(result0.dataSource == DataSource.MEMORY)

        // Execute the same request with refreshKey = 1 and confirm it does NOT
        // hit the memory cache (different cache key).
        val result1 = executeRequest(cameraId = "backyard", refreshKey = 1)
        assertTrue(result1.dataSource == DataSource.MEMORY)
    }

    @Test
    fun `recomposition without refresh can reuse memory cache`() = runTest {
        // Execute the first request; it should come from the fake fetcher.
        val result0 = executeRequest(cameraId = "backyard", refreshKey = 0)
        assertTrue(result0.dataSource == DataSource.MEMORY)

        // Execute the same request again with a fresh getter instance
        // (simulates recomposition). It should hit the memory cache.
        val result1 = executeRequest(cameraId = "backyard", refreshKey = 0)
        assertTrue(result1.dataSource == DataSource.MEMORY_CACHE)
    }

    private fun snapshot(
        cameraId: String = "backyard",
        baseUrl: String = "http://frigate",
        refreshKey: Int = 0,
        transport: TransportKind = TransportKind.LOCAL,
        getter: FakeHttpBytesGetter = FakeHttpBytesGetter(),
    ): FrigateSnapshot = FrigateSnapshot(
        cameraId = cameraId,
        baseUrl = baseUrl,
        refreshKey = refreshKey,
        transport = transport,
        getter = getter,
    )

    private fun createOptions(): coil3.request.Options {
        return coil3.request.Options(context = context)
    }

    private suspend fun executeRequest(
        cameraId: String,
        refreshKey: Int,
    ): SuccessResult {
        val request = ImageRequest.Builder(context)
            .data(
                FrigateSnapshot(
                    cameraId = cameraId,
                    baseUrl = "http://frigate",
                    refreshKey = refreshKey,
                    transport = TransportKind.LOCAL,
                    getter = FakeHttpBytesGetter(),
                ),
            )
            .build()
        val result = imageLoader.execute(request)
        assertTrue(result is SuccessResult)
        return result as SuccessResult
    }

    /**
     * Test-only fetcher that returns a solid color image without touching the
     * network. It is registered alongside the real [FrigateSnapshotKeyer] so the
     * cache behavior can be verified without decoding real JPEG bytes.
     */
    private class FakeColorFetcher : Fetcher {

        override suspend fun fetch(): FetchResult = ImageFetchResult(
            image = coil3.ColorImage(
                color = Color.GREEN,
                width = 320,
                height = 180,
                size = 4,
                shareable = true,
            ),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )

        class Factory : Fetcher.Factory<FrigateSnapshot> {
            override fun create(
                data: FrigateSnapshot,
                options: Options,
                imageLoader: ImageLoader,
            ): Fetcher = FakeColorFetcher()
        }
    }
}
