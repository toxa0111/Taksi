package uz.taksi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Xaritadagi nuqta. title — bosilganda chiqadigan yozuv. */
data class Pin(val lat: Double, val lng: Double, val title: String, val color: Int)

val GREEN = 0xFF2E7D32.toInt()   // yo'lovchi olinadigan joy
val RED = 0xFFC62828.toInt()     // borish manzili
val BLUE = 0xFF1565C0.toInt()    // men
val ORANGE = 0xFFEF6C00.toInt()  // haydovchi / yo'lovchi (jonli)

private const val ROUTE_COLOR = 0xFF2962FF.toInt()

/** Xaritaning oxirgi holati: o'zgarmagan bo'lsa, qayta chizilmaydi. */
private class MapState(
    val center: Pair<Double, Double>,
    val centerKey: Int,
    val pins: List<Pin>,
    val tappable: Boolean,
    val route: List<Pair<Double, Double>>?,
    val fitDone: Any?,
)

private fun dot(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setSize(52, 52)
    setStroke(7, Color.WHITE)
}

/**
 * OpenStreetMap xaritasi.
 * - center yoki centerKey o'zgargandagina xarita markazi siljiydi (foydalanuvchi surib ko'rayotganda sakramaydi).
 * - route berilsa, ko'k chiziq chiziladi. fitKey o'zgarganda (va marshrut bor bo'lsa) xarita marshrutga moslab
 *   BIR MARTA yaqinlashtiriladi.
 * - onTap berilsa, xaritaga bosilganda koordinata qaytariladi.
 */
@Composable
fun MapBox(
    pins: List<Pin>,
    center: Pair<Double, Double>,
    modifier: Modifier = Modifier,
    onTap: ((Double, Double) -> Unit)? = null,
    route: List<Pair<Double, Double>>? = null,
    fitKey: Any? = null,
    centerKey: Int = 0,
) {
    val tapRef = rememberUpdatedState(onTap)
    val tappable = onTap != null
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { c ->
            Configuration.getInstance().load(c, c.getSharedPreferences("osm", Context.MODE_PRIVATE))
            Configuration.getInstance().userAgentValue = c.packageName
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setTilesScaledToDpi(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(15.0)
            }
        },
        update = { map ->
            // Faqat o'zgargan bo'lsa yangilaymiz: har 3 soniyada xaritani qayta chizish ilovani qotiradi.
            val prev = map.tag as? MapState
            if (prev == null || prev.center != center || prev.centerKey != centerKey) {
                map.controller.setCenter(GeoPoint(center.first, center.second))
            }
            if (prev == null || prev.pins != pins || prev.tappable != tappable || prev.route != route) {
                map.overlays.clear()
                if (route != null && route.size > 1) {
                    val pts = route.map { GeoPoint(it.first, it.second) }
                    val casing = Polyline()
                    casing.setPoints(pts)
                    casing.outlinePaint.color = Color.WHITE
                    casing.outlinePaint.strokeWidth = 20f
                    casing.outlinePaint.strokeCap = Paint.Cap.ROUND
                    map.overlays.add(casing)
                    val line = Polyline()
                    line.setPoints(pts)
                    line.outlinePaint.color = ROUTE_COLOR
                    line.outlinePaint.strokeWidth = 12f
                    line.outlinePaint.strokeCap = Paint.Cap.ROUND
                    map.overlays.add(line)
                }
                pins.forEach { pin ->
                    val m = Marker(map)
                    m.position = GeoPoint(pin.lat, pin.lng)
                    m.title = pin.title
                    m.icon = dot(pin.color)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    map.overlays.add(m)
                }
                if (tappable) {
                    // Eng ustiga qo'yiladi: tanlash rejimida bosilgan joy doim qabul qilinadi.
                    map.overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                if (p != null) tapRef.value?.invoke(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        })
                    )
                }
                map.invalidate()
            }
            var fitDone = prev?.fitDone
            if (fitKey != null && route != null && route.size > 1 && fitDone != fitKey) {
                fitDone = fitKey
                val box = BoundingBox.fromGeoPoints(route.map { GeoPoint(it.first, it.second) })
                map.post { map.zoomToBoundingBox(box, true, 110) }
            }
            map.tag = MapState(center, centerKey, pins, tappable, route, fitDone)
        },
        onRelease = { it.onDetach() },
    )
}

private fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Ruxsat so'raydi va qurilmaning joriy GPS joylashuvini (lat, lng) sifatida beradi. */
@SuppressLint("MissingPermission")
@Composable
fun rememberMyLocation(): State<Pair<Double, Double>?> {
    val ctx = LocalContext.current
    val loc = remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    DisposableEffect(granted) {
        if (!granted) {
            onDispose { }
        } else {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = LocationListener { l -> loc.value = Pair(l.latitude, l.longitude) }
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 3000L, 5f, listener)
                        val last = lm.getLastKnownLocation(p)
                        if (last != null && loc.value == null) loc.value = Pair(last.latitude, last.longitude)
                    }
                } catch (e: Exception) { }
            }
            onDispose { lm.removeUpdates(listener) }
        }
    }
    return loc
}
