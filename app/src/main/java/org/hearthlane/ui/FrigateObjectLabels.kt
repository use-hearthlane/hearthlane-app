package org.hearthlane.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.hearthlane.R

/**
 * Presentation-layer resolver for Frigate detected-object labels.
 *
 * The domain model ([org.hearthlane.core.frigate.Event.label]) always keeps the
 * raw value received from Frigate ("person", "dog", ...); it is never localized.
 * This resolver maps a known object label to the Android string resource for the
 * device locale and falls back to the original label for anything the object
 * model does not cover, so unknown labels keep working untouched.
 */
internal object FrigateObjectLabels {

    /** String resource for a known object label, or null when not covered. */
    @StringRes
    fun resourceFor(label: String): Int? = when (label) {
        "person" -> R.string.frigate_object_person
        "dog" -> R.string.frigate_object_dog
        "cat" -> R.string.frigate_object_cat
        "car" -> R.string.frigate_object_car
        "truck" -> R.string.frigate_object_truck
        "bus" -> R.string.frigate_object_bus
        "motorcycle" -> R.string.frigate_object_motorcycle
        "bicycle" -> R.string.frigate_object_bicycle
        "bird" -> R.string.frigate_object_bird
        else -> null
    }
}

/**
 * Localized display text for a Frigate object label. Android picks the language
 * from the device locale (English default, Portuguese from `values-pt-rBR`).
 *
 * - known label -> translated text for the device locale;
 * - unknown label -> the original value as received;
 * - null label -> the generic "no label" text.
 */
@Composable
internal fun localizedFrigateObjectLabel(label: String?): String {
    if (label == null) return stringResource(R.string.event_no_label)
    val resource = FrigateObjectLabels.resourceFor(label)
    return if (resource != null) stringResource(resource) else label
}
