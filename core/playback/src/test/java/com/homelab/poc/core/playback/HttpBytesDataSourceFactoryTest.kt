package com.homelab.poc.core.playback

import androidx.media3.datasource.DataSource
import com.homelab.poc.core.connectivity.HttpBytesResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpBytesDataSourceFactoryTest {

    private val getter = com.homelab.poc.core.connectivity.HttpBytesGetter { url, timeoutMs ->
        HttpBytesResult(200, null, url, ByteArray(0))
    }

    @Test
    fun `factory always creates a data source bound to the getter`() {
        val factory = HttpBytesDataSourceFactory(getter, timeoutMs = 30_000L)
        val source: DataSource = factory.createDataSource()
        assertNotNull(source)
        assertTrue(source is HttpBytesDataSource)
        // Opening the second instance again must work (stateless factory).
        assertTrue(factory.createDataSource() is HttpBytesDataSource)
    }
}
