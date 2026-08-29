package org.hearthlane.core.playback

import androidx.media3.datasource.DataSource
import org.hearthlane.core.connectivity.HttpBytesResult
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpBytesDataSourceFactoryTest {

    private val getter = org.hearthlane.core.connectivity.HttpBytesGetter { url, timeoutMs ->
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
