package uz.taksi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/** Xaritadagi nuqta. title — bosilganda chiqadigan yozuv. */
data class Pin(val lat: Double, val lng: Double, val title: String, val color: Int)

val GREEN = 0xFF2E7D32.toInt()   // yo'lovchi olinadigan joy
val RED = 0xFFC62828.toInt()     // borish manzili
val BLUE = 0xFF1565C0.toInt()    // men
val ORANGE = 0xFFEF6C00.toInt()  // haydovchi / yo'lovchi (jonli)

private fun dot(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setSize(44, 44)
    setStroke(6, Color.WHITE)
}

/**
 * OpenStreetMap
