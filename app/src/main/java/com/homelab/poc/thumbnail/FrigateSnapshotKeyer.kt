package com.homelab.poc.thumbnail

import coil3.key.Keyer
import coil3.request.Options

/**
 * Stable Coil [Keyer] for [FrigateSnapshot].
 *
 * The key is built deterministically from only stable, semantically relevant
 * fields. It deliberately excludes the [FrigateSnapshot.getter] instance because
 * object identity, hash code and class name are not stable across recompositions
 * or process restarts.
 */
internal class FrigateSnapshotKeyer : Keyer<FrigateSnapshot> {

    override fun key(data: FrigateSnapshot, options: Options): String {
        return buildString {
            append(data.baseUrl)
            append(SEPARATOR)
            append(data.cameraId)
            append(SEPARATOR)
            append(data.refreshKey)
            append(SEPARATOR)
            append(data.transport.name)
            data.resourceUrl?.let {
                append(SEPARATOR)
                append(it)
            }
        }
    }

    private companion object {
        const val SEPARATOR = "\u0000"
    }
}
