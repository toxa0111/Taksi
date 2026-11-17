package uz.taksi

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Faol safar paytida (ilova fonda bo'lsa ham, masalan Yandex Navigator ochiq) joylashuvni
 * serverga yuborib turadi. Doimiy bildirishnoma ko'rsatadi; ustiga bosilsa TOXA ochiladi.
 */
class LocationService : Service() {
    private var lm: LocationManager? = null
    private var listener: LocationListener? = null
    private var lastSent = 0L
    @Volatile private var watching = false
    private var role = ""

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Jarayon qayta tiklangan bo'lsa token yo'q — ishlashning ma'nosi yo'q.
        if (Api.token.isEmpty() || !hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra("text") ?: "Safar davom etmoqda"
        role = intent?.getStringExtra("role") ?: ""
        startAsForeground(text)
        if (role.isNotEmpty() && !watching) startWatching()

        if (listener == null) {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val l = LocationListener { loc ->
                val now = System.currentTimeMillis()
                if (now - lastSent >= 3000) {
                    lastSent = now
                    val lat = loc.latitude
                    val lng = loc.longitude
                    Thread {
                        try { Api.call("POST", "/location", JSONObject().put("lat", lat).put("lng", lng)) } catch (e: Exception) { }
                    }.start()
                }
            }
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (manager.isProviderEnabled(p)) {
                        manager.requestLocationUpdates(p, 4000L, 10f, l, Looper.getMainLooper())
                    }
                } catch (e: Exception) { }
            }
            lm = manager
            listener = l
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(text: String) {
        val channelId = "trip"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Faol safar", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_toxa)
            .setContentTitle("TOXA")
            .setContentText(translate(text, AppSettings.lang))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, n)
        }
    }

    /**
     * Ilova yopiq/fonda bo'lsa ham yangilik bildirish: haydovchiga yangi buyurtma, yo'lovchiga safar holati.
     * (Firebase push o'rniga: xizmat ishlab turganda serverdan so'rab turadi.)
     */
    private fun startWatching() {
        watching = true
        Thread {
            var lastCount = -1
            val seen = HashMap<Int, String>()
            var first = true
            while (watching) {
                try {
                    if (role == "driver") {
                        val n = Api.call("GET", "/orders/available").getJSONArray("orders").length()
                        if (lastCount in 0 until n && !AppState.foreground) notifyAlert("Yangi buyurtma!", "Yaqin atrofda yangi buyurtma bor")
                        lastCount = n
                    } else if (role == "passenger") {
                        val arr = Api.call("GET", "/orders/mine").getJSONArray("orders")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.optInt("id"); val st = o.optString("status")
                            val prev = seen[id]
                            if (!first && prev != null && prev != st && !AppState.foreground) {
                                val msg = when (st) {
                                    "accepted" -> "Haydovchi buyurtmangizni qabul qildi"
                                    "arrived" -> "Haydovchi yetib keldi"
                                    "started" -> "Safar boshlandi"
                                    "done" -> "Safar tugadi"
                                    "cancelled" -> "Buyurtma bekor qilindi"
                                    else -> ""
                                }
                                if (msg.isNotEmpty()) notifyAlert("TOXA", msg)
                            }
                            seen[id] = st
                        }
                        first = false
                    }
                } catch (e: Exception) { }
                try { Thread.sleep(5000) } catch (e: InterruptedException) { break }
            }
        }.start()
    }

    private fun notifyAlert(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("alerts", "Yangiliklar", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(R.drawable.ic_stat_toxa)
            .setContentTitle(translate(title, AppSettings.lang))
            .setContentText(translate(text, AppSettings.lang))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (AppSettings.sound == "off") b.setSilent(true)
        try { nm.notify(100 + (System.currentTimeMillis() % 1000).toInt(), b.build()) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        watching = false
        listener?.let { lm?.removeUpdates(it) }
        listener = null
        super.onDestroy()
    }
}

private fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Faol safar bor ekan, fon xizmati ishlaydi; safar tugasa yoki ekrandan chiqilsa to'xtaydi. */
@Composable
fun TripLocationService(running: Boolean, text: String, role: String = "") {
    val ctx = LocalContext.current
    // Android 13+: doimiy bildirishnoma uchun ruxsat (rad etilsa ham xizmat ishlaydi)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(running) {
        if (running && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (e: Exception) { }
        }
    }
    DisposableEffect(running) {
        if (running) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LocationService::class.java).putExtra("text", text).putExtra("role", role))
            } catch (e: Exception) { }   // ilova fonda bo'lsa Android 12+ ruxsat bermasligi mumkin
        }
        onDispose { if (running) try { ctx.stopService(Intent(ctx, LocationService::class.java)) } catch (e: Exception) { } }
    }
}
