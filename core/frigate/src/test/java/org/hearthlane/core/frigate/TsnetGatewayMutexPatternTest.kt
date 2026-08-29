package org.hearthlane.core.frigate

import org.hearthlane.core.connectivity.TsnetGateway
import org.hearthlane.core.connectivity.HttpBytesResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Validates the Mutex-based serialization pattern used by TsnetGatewayImpl.
 *
 * Since TsnetGatewayImpl depends on the gomobile binding (TailscaleBridge),
 * it cannot be unit-tested without the Android runtime. This test validates
 * the Mutex pattern itself to prove concurrent callers are serialized.
 */
class TsnetGatewayMutexPatternTest {

    @Test
    fun `two concurrent ensureRunning calls are serialized by Mutex`() = runBlocking {
        val startCount = AtomicInteger(0)

        val gateway = object : TsnetGateway {
            private val mutex = Mutex()

            override suspend fun ensureRunning() {
                mutex.withLock {
                    startCount.incrementAndGet()
                    yield()
                }
            }

            override suspend fun stopIfRunning() = Unit
            override suspend fun reset() = Unit
            override suspend fun httpGet(url: String, timeoutMs: Long): String = ""
            override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
                HttpBytesResult(200, "", "", byteArrayOf())
        }

        coroutineScope {
            val job1 = async { gateway.ensureRunning() }
            val job2 = async { gateway.ensureRunning() }
            awaitAll(job1, job2)
        }

        assertEquals("both calls must complete", 2, startCount.get())
    }

    @Test
    fun `Mutex guarantees at most one concurrent execution under heavy contention`() = runBlocking {
        val concurrentMax = AtomicInteger(0)
        val currentCount = AtomicInteger(0)
        val mutex = Mutex()

        val gateway = object : TsnetGateway {
            override suspend fun ensureRunning() {
                mutex.withLock {
                    val c = currentCount.incrementAndGet()
                    concurrentMax.updateAndGet { maxOf(it, c) }
                    yield()
                    currentCount.decrementAndGet()
                }
            }

            override suspend fun stopIfRunning() = Unit
            override suspend fun reset() = Unit
            override suspend fun httpGet(url: String, timeoutMs: Long): String = ""
            override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
                HttpBytesResult(200, "", "", byteArrayOf())
        }

        coroutineScope {
            (1..10).map {
                async { gateway.ensureRunning() }
            }.awaitAll()
        }

        assertEquals(
            "Mutex must guarantee at most 1 concurrent execution",
            1,
            concurrentMax.get(),
        )
    }

    @Test
    fun `Mutex blocks second caller until first completes`() = runBlocking {
        val entryGate = CompletableDeferred<Unit>()
        val releaseGate = CompletableDeferred<Unit>()
        val entered = AtomicInteger(0)

        val gateway = object : TsnetGateway {
            private val mutex = Mutex()

            override suspend fun ensureRunning() {
                mutex.withLock {
                    entered.incrementAndGet()
                    entryGate.await()
                    releaseGate.await()
                }
            }

            override suspend fun stopIfRunning() = Unit
            override suspend fun reset() = Unit
            override suspend fun httpGet(url: String, timeoutMs: Long): String = ""
            override suspend fun httpGetBytes(url: String, timeoutMs: Long): HttpBytesResult =
                HttpBytesResult(200, "", "", byteArrayOf())
        }

        coroutineScope {
            val job1 = async { gateway.ensureRunning() }
            val job2 = async { gateway.ensureRunning() }

            yield()
            yield()

            assertEquals("only the first caller must have entered", 1, entered.get())

            entryGate.complete(Unit)
            releaseGate.complete(Unit)
            job1.await()

            yield()
            assertEquals("the second caller must now have entered", 2, entered.get())

            releaseGate.complete(Unit)
            job2.await()
        }
    }
}
