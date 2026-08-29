package org.hearthlane.test

import android.content.Context
import android.graphics.Color
import coil3.decode.DataSource
import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import org.hearthlane.thumbnail.FrigateSnapshot

/**
 * Coil [ImageLoader] that returns a solid green image for every [FrigateSnapshot]
 * without performing any network request. Used by Home UI tests so they do not
 * depend on a real Frigate server.
 */
fun fakeSnapshotImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(FakeSnapshotFetcher.Factory())
        }
        .build()

private class FakeSnapshotFetcher : Fetcher {

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
        ): Fetcher = FakeSnapshotFetcher()
    }
}
