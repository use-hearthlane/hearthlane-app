package org.hearthlane.ui.locations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import org.hearthlane.location.DeviceMarker
import org.hearthlane.location.FocusRequest
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.max

/**
 * Family-location map backed by OSMDroid.
 *
 * One marker per device with a published location, colored deterministically
 * by [DeviceColorResolver]. Markers stay in sync with [devices]; a stale set
 * never blanks the map because the caller already keeps the last valid result.
 * The first non-empty data set centers the view once (auto-fit); later polls
 * only move markers, so the user's pan/zoom is preserved.
 *
 * Selection is shared with the device selector: [selectedDeviceId] highlights
 * the matching marker, and [focusRequests] centers the camera on each emitted
 * device (only devices with coordinates are emitted). Tapping a marker routes
 * back through [onDeviceSelected].
 */
@Composable
fun LocationsMap(
    devices: List<DeviceMarker>,
    selectedDeviceId: String?,
    focusRequest: FocusRequest?,
    onDeviceSelected: (String) -> Unit,
    onMapEmptyTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = remember { createMapState(context) }
    val currentOnMapEmptyTap by rememberUpdatedState(onMapEmptyTap)

    // OSMDroid's lifecycle methods pair with the host lifecycle.
    DisposableEffect(state.map) {
        state.map.onResume()
        onDispose { state.map.onPause() }
    }

    // A tap on empty map (no marker) closes the details panel. The overlay sits
    // below the markers, so a marker tap is consumed by the marker first and
    // never reaches this handler.
    LaunchedEffect(state.map) {
        state.map.overlays.add(
            0,
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                    currentOnMapEmptyTap()
                    return false
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
            }),
        )
    }

    // Camera centering is driven by the controller's focus requests (the epoch
    // advances on each selection, so re-selecting a device re-centers).
    LaunchedEffect(focusRequest) {
        if (focusRequest != null) state.overlay.focusOn(focusRequest.deviceId)
    }

    // Keep the markers in sync with the latest query result.
    LaunchedEffect(devices) {
        state.overlay.update(devices, onDeviceSelected)
    }

    LaunchedEffect(selectedDeviceId) {
        state.overlay.setSelected(selectedDeviceId)
    }

    // The map is a classic View embedded in Compose: clip it to its layout
    // bounds so its drawing (tiles/controls during zoom/pan) can never spill
    // over the device selector rendered above it.
    AndroidView(
        factory = { state.map },
        modifier = modifier.clipToBounds(),
    )
}

/** Creates and configures the map together with its marker overlay. */
private fun createMapState(context: Context): MapState {
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE),
    )
    Configuration.getInstance().userAgentValue = "${context.packageName}.locations"
    val map = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        controller.setZoom(12.0)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
    }
    return MapState(map, DeviceMarkersOverlay(map))
}

private class MapState(
    val map: MapView,
    val overlay: DeviceMarkersOverlay,
)

/**
 * Renders one colored [Marker] per device, adding/removing/moving markers as
 * the data changes, and owns the selection highlight and camera focus. Auto-fits
 * the view on the first non-empty set only.
 */
private class DeviceMarkersOverlay(private val map: MapView) {

    private val markers = mutableMapOf<String, Marker>()
    private val accuracyPolygons = mutableMapOf<String, Polygon>()
    private var centered = false
    private var selected: String? = null
    private var onSelect: (String) -> Unit = {}

    fun update(devices: List<DeviceMarker>, onSelect: (String) -> Unit) {
        this.onSelect = onSelect
        val nextIds = devices.map { it.deviceId }.toSet()
        markers.keys.filterNot { it in nextIds }.forEach { id ->
            map.overlays.remove(markers.remove(id))
        }
        for (device in devices) {
            val marker = markers[device.deviceId]
            if (marker == null) {
                Marker(map).apply {
                    position = GeoPoint(device.latitude, device.longitude)
                    title = device.label
                    infoWindow = null
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = markerIcon(device.deviceId, selected = false)
                    setOnMarkerClickListener { _, _ ->
                        this@DeviceMarkersOverlay.onSelect(device.deviceId)
                        true
                    }
                }.also { added ->
                    markers[device.deviceId] = added
                    map.overlays.add(added)
                }
            } else {
                marker.position = GeoPoint(device.latitude, device.longitude)
                marker.title = device.label
            }
        }
        reconcileAccuracyCircles(devices)
        applyCircleSelection()
        map.invalidate()
        if (!centered && devices.isNotEmpty()) {
            centered = true
            zoomToFit(devices)
        }
    }

    /** Highlights the selected marker and emphasizes its accuracy circle; all
     *  other devices keep their base icon and discreet circle. Selection only
     *  changes presentation — it never removes a circle or a marker. */
    fun setSelected(deviceId: String?) {
        if (deviceId == selected) return
        selected = deviceId
        for (id in markers.keys) {
            markers[id]?.icon = markerIcon(id, selected = id == deviceId)
        }
        applyCircleSelection()
        map.invalidate()
    }

    /**
     * One accuracy circle PER device with a valid location and accuracy,
     * drawn permanently and independent of selection/details. Reconciles the
     * deltas between polls (create/update in place/remove), so overlays never
     * accumulate and the MapView is never rebuilt. Each circle is inserted
     * below every marker, so the pin always stays above its circle; the
     * circles are [DecorativeAccuracyPolygon]s, which never open an InfoWindow
     * and never swallow a tap meant for the map or a marker.
     */
    private fun reconcileAccuracyCircles(devices: List<DeviceMarker>) {
        val plan = planAccuracyCircles(accuracyPolygons.keys.toSet(), devices)
        plan.toRemove.forEach { id ->
            accuracyPolygons.remove(id)?.let { map.overlays.remove(it) }
        }
        for (device in plan.toCreate) {
            val polygon = DecorativeAccuracyPolygon(map).apply {
                setPoints(
                    Polygon.pointsAsCircle(
                        GeoPoint(device.latitude, device.longitude),
                        device.accuracyMeters.toDouble(),
                    ),
                )
            }
            // Insert after the already-existing circles and before any marker:
            // markers are always appended later, so this keeps circles lowest.
            map.overlays.add(accuracyPolygons.size, polygon)
            accuracyPolygons[device.deviceId] = polygon
        }
        for (device in plan.toUpdate) {
            accuracyPolygons[device.deviceId]?.setPoints(
                Polygon.pointsAsCircle(
                    GeoPoint(device.latitude, device.longitude),
                    device.accuracyMeters.toDouble(),
                ),
            )
        }
    }

    /** Selection emphasis only: the selected device's circle gets a stronger
     *  fill and outline; the others stay discreet. All circles remain visible. */
    private fun applyCircleSelection() {
        for ((id, polygon) in accuracyPolygons) {
            val isSelected = id == selected
            val argb = DeviceColorResolver.colorFor(id).toArgb()
            polygon.setFillColor(
                translucentFill(argb, if (isSelected) SELECTED_FILL_ALPHA else UNSELECTED_FILL_ALPHA),
            )
            polygon.setStrokeColor(argb)
            polygon.setStrokeWidth(if (isSelected) SELECTED_STROKE_WIDTH else UNSELECTED_STROKE_WIDTH)
        }
    }

    private fun translucentFill(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Centers the camera on [deviceId] when a marker exists; no-op otherwise,
     *  so a device without coordinates never moves the map to a fake position. */
    fun focusOn(deviceId: String) {
        val marker = markers[deviceId] ?: return
        val point = marker.position
        val zoom = max(map.zoomLevelDouble, MIN_FOCUS_ZOOM)
        map.controller.animateTo(point, zoom, FOCUS_ANIMATION_MS)
    }

    private fun markerIcon(deviceId: String, selected: Boolean): BitmapDrawable {
        val color = DeviceColorResolver.colorFor(deviceId).toArgb()
        val size = if (selected) SELECTED_SIZE else BASE_SIZE
        val radius = if (selected) SELECTED_RADIUS else BASE_RADIUS
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        canvas.drawCircle(center, center, radius, ringPaint)
        canvas.drawCircle(center, center, radius - RING_WIDTH, fillPaint(color))
        return bitmap.toDrawable(map.context.resources)
    }

    /** Bounds the view around the devices with a small padding for the edge marker. */
    private fun zoomToFit(devices: List<DeviceMarker>) {
        val minLat = devices.minOf { it.latitude } - PAD_DEGREES
        val maxLat = devices.maxOf { it.latitude } + PAD_DEGREES
        val minLon = devices.minOf { it.longitude } - PAD_DEGREES
        val maxLon = devices.maxOf { it.longitude } + PAD_DEGREES
        map.zoomToBoundingBox(
            BoundingBox(maxLat, maxLon, minLat, minLon),
            true,
            64,
        )
    }

    private companion object {
        /** Padding around the auto-fit box (~1 km) so edge markers are not clipped. */
        const val PAD_DEGREES = 0.01
        const val MIN_FOCUS_ZOOM = 13.0
        const val FOCUS_ANIMATION_MS = 500L
        const val BASE_SIZE = 56
        const val SELECTED_SIZE = 78
        const val BASE_RADIUS = 21f
        const val SELECTED_RADIUS = 31f
        const val RING_WIDTH = 5f

        /** Accuracy circle emphasis by selection. Unselected devices stay
         *  discreet (~9% fill); the selected device gets a stronger fill
         *  (~14%) and a more evident outline. Markers always dominate. */
        const val UNSELECTED_FILL_ALPHA = 0x18
        const val SELECTED_FILL_ALPHA = 0x24
        const val UNSELECTED_STROKE_WIDTH = 2f
        const val SELECTED_STROKE_WIDTH = 3f

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }

        fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
    }
}

/**
 * Accuracy circle polygon that is strictly decorative: it never opens an
 * InfoWindow and never consumes a tap.
 *
 * A plain OSMDroid [Polygon] does both: [PolyOverlayWithIW.onSingleTapConfirmed]
 * hit-tests the tap against the geometry and, on a hit, calls [Polygon.onClickDefault]
 * (through `Polygon.click`), which opens the default `BasicInfoWindow` — even
 * with no click listener — and returns true, so the tap is also swallowed and
 * never reaches the [MapEventsOverlay]. Overriding [onSingleTapConfirmed] to
 * return false keeps the circle visible but fully pass-through: a tap inside
 * the circle reaches the map exactly like a tap on any empty area (and closes
 * the details panel), while a marker on top still receives its own tap first.
 */
internal class DecorativeAccuracyPolygon(map: MapView) : Polygon(map) {

    init {
        // Defense in depth: even if some code path calls showInfoWindow, a null
        // InfoWindow makes it a no-op (see OverlayWithIW.showInfoWindow).
        setInfoWindow(null)
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean = false
}