package uz.taksi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import java.lang.ref.WeakReference

/** Xaritadagi nuqta. title — bosilganda chiqadigan yozuv. */
data class Pin(val lat: Double, val lng: Double, val title: String, val color: Int)

val GREEN = 0xFF2E7D32.toInt()
val RED = 0xFFC62828.toInt()
val BLUE = 0xFF1565C0.toInt()
val ORANGE = 0xFFEF6C00.toInt()

private class MapState(
    val center: Pair<Double, Double>,
    val centerKey: Int,
    val pins: List<Pin>,
    val route: List<Pair<Double, Double>>?,
    val fitKey: Any?,
)

private fun pinImage(context: Context, color: Int): ImageProvider {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawCircle(32f, 32f, 23f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 6f
    canvas.drawCircle(32f, 32f, 23f, paint)
    return ImageProvider.fromBitmap(bitmap)
}

/** Yandex MapKit xaritasi: markaz, nuqtalar, marshrut chizig'i va xaritaga bosish. */
@Composable
fun MapBox(
    pins: List<Pin>,
    center: Pair<Double, Double>,
    modifier: Modifier = Modifier,
    onTap: ((Double, Double) -> Unit)? = null,
    route: List<Pair<Double, Double>>? = null,
    fitKey: Any? = null,
    centerKey: Int = 0,
    onMapTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tapRef = rememberUpdatedState(onTap)
    val mapTapRef = rememberUpdatedState(onMapTap)
    val tappable = onTap != null
    val inputListener = remember {
        object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                mapTapRef.value?.invoke()
                if (tappable) tapRef.value?.invoke(point.latitude, point.longitude)
            }
            override fun onMapLongTap(map: Map, point: Point) = Unit
        }
    }

    if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
        Text("Yandex MapKit kaliti ulanmagan. GitHub Actions Secret sozlamasini tekshiring.")
        return
    }

    val mapView = remember {
        MapView(context).apply {
            mapWindow.map.move(CameraPosition(Point(center.first, center.second), 15.0f, 0.0f, 0.0f))
            mapWindow.map.addInputListener(WeakReference(inputListener))
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { mapView.onStop() } catch (_: Exception) { }
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { mapView },
        update = { view ->
            val map = view.mapWindow.map
            val previous = view.tag as? MapState
            if (previous == null || previous.center != center || previous.centerKey != centerKey) {
                map.move(CameraPosition(Point(center.first, center.second), 15.0f, 0.0f, 0.0f))
            }
            if (previous == null || previous.pins != pins || previous.route != route) {
                val objects: MapObjectCollection = map.mapObjects
                objects.clear()
                route?.takeIf { it.size > 1 }?.let { points ->
                    objects.addPolyline(Polyline(points.map { Point(it.first, it.second) }))
                        .apply {
                            setStrokeColor(0xFF2962FF.toInt())
                            style = style.apply { strokeWidth = 5f }
                        }
                }
                pins.forEach { pin ->
                    objects.addPlacemark().apply {
                        geometry = Point(pin.lat, pin.lng)
                        setIcon(pinImage(context, pin.color))
                        userData = pin.title
                    }
                }
            }
            if (fitKey != null && route != null && route.size > 1 && previous?.fitKey != fitKey) {
                val points = route.map { Point(it.first, it.second) }
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val target = Point((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
                map.move(CameraPosition(target, 13.0f, 0.0f, 0.0f))
            }
            view.tag = MapState(center, centerKey, pins, route, fitKey)
        },
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
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val listener = android.location.LocationListener { l -> loc.value = Pair(l.latitude, l.longitude) }
            for (provider in listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        lm.requestLocationUpdates(provider, 3000L, 5f, listener)
                        val last = lm.getLastKnownLocation(provider)
                        if (last != null && loc.value == null) loc.value = Pair(last.latitude, last.longitude)
                    }
                } catch (_: Exception) { }
            }
            onDispose { lm.removeUpdates(listener) }
        }
    }
    return loc
}
