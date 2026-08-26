package com.homelab.poc.ui

import com.homelab.poc.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure mapping contract for [FrigateObjectLabels.resourceFor]: known object
 * labels resolve to their stable string resource and unknown labels resolve to
 * null (so the UI falls back to the original value). No Android/locale context
 * is required here; locale resolution is covered by the Robolectric UI tests.
 */
class FrigateObjectLabelsTest {

    @Test
    fun `known labels resolve to their string resource`() {
        assertEquals(R.string.frigate_object_person, FrigateObjectLabels.resourceFor("person"))
        assertEquals(R.string.frigate_object_dog, FrigateObjectLabels.resourceFor("dog"))
        assertEquals(R.string.frigate_object_cat, FrigateObjectLabels.resourceFor("cat"))
        assertEquals(R.string.frigate_object_car, FrigateObjectLabels.resourceFor("car"))
        assertEquals(R.string.frigate_object_truck, FrigateObjectLabels.resourceFor("truck"))
        assertEquals(R.string.frigate_object_bus, FrigateObjectLabels.resourceFor("bus"))
        assertEquals(R.string.frigate_object_motorcycle, FrigateObjectLabels.resourceFor("motorcycle"))
        assertEquals(R.string.frigate_object_bicycle, FrigateObjectLabels.resourceFor("bicycle"))
        assertEquals(R.string.frigate_object_bird, FrigateObjectLabels.resourceFor("bird"))
    }

    @Test
    fun `unknown labels resolve to null`() {
        assertNull(FrigateObjectLabels.resourceFor("horse"))
        assertNull(FrigateObjectLabels.resourceFor("package"))
        assertNull(FrigateObjectLabels.resourceFor(""))
    }
}
