package com.homelab.poc.core.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class PlaybackStatusTest {

    private fun playbackException(cause: Throwable? = null): PlaybackException =
        PlaybackException("source error", cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)

    @Test
    fun `httpStatusFrom returns the status from the cause chain`() {
        val wrapped = IOException(
            "media3 wrapper",
            HttpStatusIOException(503, "HttpBytesDataSource: GET http://x -> HTTP 503"),
        )
        assertEquals(503, httpStatusFrom(playbackException(wrapped)))
    }

    @Test
    fun `httpStatusFrom returns null for connection failures`() {
        assertNull(httpStatusFrom(playbackException(IOException("Unable to resolve host"))))
        assertNull(httpStatusFrom(playbackException()))
    }
}
