package org.hearthlane.ui.locations

import android.content.Context
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the accuracy-circle tap bug: a plain OSMDroid [Polygon]
 * hit-tests single taps against its geometry and, on a hit, opens the default
 * `BasicInfoWindow` (even with no click listener) AND returns true, swallowing
 * the tap. The accuracy circle must be strictly decorative: no InfoWindow, no
 * click action, and pass-through so a tap inside the circle is treated as an
 * empty-map tap (closing the details panel).
 *
 * Limitation: the full OverlayManager dispatch (markers above circles above
 * MapEventsOverlay, reverse-order stop-on-true) is not driven here; the test
 * pins the overlay's own policy (InfoWindow null + tap never consumed), which
 * is what guarantees pass-through given the existing z-ordering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DecorativeAccuracyPolygonTest {

    private lateinit var map: MapView

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid_test", Context.MODE_PRIVATE),
        )
        Configuration.getInstance().userAgentValue = "org.hearthlane.test"
        map = MapView(context)
    }

    @Test
    fun `accuracy polygon has no info window`() {
        val polygon = DecorativeAccuracyPolygon(map)

        assertNull("a decorative circle must never have an InfoWindow", polygon.infoWindow)
    }

    @Test
    fun `accuracy polygon never consumes a tap even inside its geometry`() {
        val polygon = DecorativeAccuracyPolygon(map)
        polygon.setPoints(Polygon.pointsAsCircle(GeoPoint(0.0, 0.0), 100.0))

        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 10f, 10f, 0)
        val consumed = try {
            polygon.onSingleTapConfirmed(event, map)
        } finally {
            event.recycle()
        }

        assertFalse(
            "a tap inside the circle must pass through to the map, never be consumed",
            consumed,
        )
    }

    @Test
    fun `accuracy polygon triggers no click action`() {
        val polygon = DecorativeAccuracyPolygon(map)

        // The default Polygon would call Polygon.click -> onClickDefault and open
        // the InfoWindow; the decorative override short-circuits before any of
        // that, so nothing is opened and nothing is consumed.
        assertNull(polygon.infoWindow)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 5f, 5f, 0)
        try {
            assertFalse(polygon.onSingleTapConfirmed(event, map))
        } finally {
            event.recycle()
        }
    }

    @Test
    fun `circle still renders as a polygon with the radius points`() {
        val polygon = DecorativeAccuracyPolygon(map)
        val points = Polygon.pointsAsCircle(GeoPoint(-23.55, -46.63), 100.0)

        polygon.setPoints(points)

        assertTrue("a decorative circle is still a renderable Polygon", polygon is Polygon)
        assertEquals(points.size, polygon.getPoints().size)
    }
}