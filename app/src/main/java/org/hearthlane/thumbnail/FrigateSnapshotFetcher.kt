package org.hearthlane.thumbnail

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import org.hearthlane.core.connectivity.HttpBytesGetter
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.FileSystem

private const val SNAPSHOT_TIMEOUT_MS = 10_000L

/**
 * Coil [Fetcher] that loads Frigate snapshots through the app's transport-scoped
 * [HttpBytesGetter]. This keeps thumbnail traffic on the same path (LOCAL or
 * TAILSCALE) as the rest of the application and avoids leaking snapshot URLs
 * into the system network stack.
 *
 * Failures are surfaced as normal Coil errors so the card can show a
 * best-effort placeholder and remain usable.
 */
internal class FrigateSnapshotFetcher(
    private val snapshot: FrigateSnapshot,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = snapshot.snapshotUrl()
        val result = try {
            snapshot.getter.getBytes(url, SNAPSHOT_TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        }
        if (result.statusCode !in 200..299) {
            throw SnapshotFetchException("snapshot failed: HTTP ${result.statusCode}")
        }
        val body = result.body
        if (body.isEmpty()) {
            throw SnapshotFetchException("snapshot returned empty body")
        }
        val source = ImageSource(
            source = Buffer().write(body),
            fileSystem = FileSystem.SYSTEM,
        )
        return SourceFetchResult(
            source = source,
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK,
        )
    }

    internal class Factory : Fetcher.Factory<FrigateSnapshot> {
        override fun create(
            data: FrigateSnapshot,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = FrigateSnapshotFetcher(data)
    }

    private class SnapshotFetchException(message: String) : Exception(message)
}
