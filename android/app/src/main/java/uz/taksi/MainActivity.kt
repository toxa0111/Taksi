package uz.taksi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yandex.mapkit.MapKitFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { super.onStop(); AppState.foreground = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kutilmagan xato bo'lsa, matnini saqlaymiz va keyingi ochilishda ko'rsatamiz
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            try { crashFile.writeText(android.util.Log.getStackTraceString(e).take(5000)) } catch (_: Throwable) { }
            oldHandler?.uncaughtException(th, e)
        }
        if (crashFile.exists()) {
            val trace = try { crashFile.readText() } catch (_: Throwable) { "?" }
            setContent { CrashScreen(trace) { crashFile.delete(); recreate() } }
            return
        }
        if (BuildConfig.MAPKIT_API_KEY.isNotBlank()) {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        }
        val prefs = getSharedPreferences("taksi", Context.MODE_PRIVATE)
        Api.token = prefs.getString("token", "") ?: ""
        Api.base = prefs.getString("base", Api.base) ?: Api.base
        // Holat paneli sariq, belgilari qora
                window.statusBarColor = 0xFFFFC21A.toInt()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        AppSettings.load(this)
        setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * AppSettings.fontScale),
            ) {
                TaksiTheme {
                    Surface(Modifier.fillMaxSize(), color = PageBg, contentColor = Fg) {
                        if (BuildConfig.IS_ADMIN) AdminApp(prefs) else App(prefs)
                    }
                }
            }
        }
    }
}

@Composable
fun App(prefs: SharedPreferences) {
    var role by remember { mutableStateOf(prefs.getString("role", "") ?: "") }
    val logout = {
        prefs.edit().remove("token").remove("role").remove("name").remove("phone").apply()
        Api.token = ""
        role = ""
    }
    UpdateDialog()
    when (role) {
        "passenger", "driver" -> Shell(role, prefs, logout)
        else -> LoginScreen(prefs) { role = it }
    }
}

// ───────────────────────── Umumiy elementlar ─────────────────────────

/** Buyurtma kartasi: holat, narx, yo'nalish, to'lov usuli. */
@Composable
fun OrderCard(o: JSONObject, extra: @Composable ColumnScope.() -> Unit = {}) {
    val st = o.optString("status")
    val (bg, fg) = statusColors(st)
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(STATUS[st] ?: st, bg, fg)
            Spacer(Modifier.weight(1f))
            Text(som(o.optLong("price")), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Route(o.optString("from"), o.optString("to"))
        Text(
            String.format("%.1f km  ·  %s", o.optDouble("km"), payText(o)),
            fontSize = 13.sp, color = Muted,
        )
        extra()
    }
}

/** Ism va telefon, yoniga "Qo'ng'iroq" tugmasi. */
@Composable
fun PersonRow(role: String, name: String, phone: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) {
            Text("👤", fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { role }, fontWeight = FontWeight.Bold)
            Text(if (phone.isBlank()) role else "$role · $phone", fontSize = 12.sp, color = Muted)
        }
        if (phone.isNotBlank()) {
            DarkButton("📞 Qo'ng'iroq") {
                try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (e: Exception) { }
            }
        }
    }
}

/** Joylashuvni serverga yuborib turadi (boshqa tomon xaritada ko'radi). */
@Composable
fun ShareLocation(me: Pair<Double, Double>?) {
    LaunchedEffect(me) {
        if (me != null) {
            try { io { Api.call("POST", "/location", JSONObject().put("lat", me.first).put("lng", me.second)) } } catch (e: Exception) { }
        }
    }
}

@Composable
fun ErrorText(msg: String) {
    if (msg.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFCE4E4)).padding(12.dp)) {
            Text(msg, color = Color(0xFFA32626), fontSize = 13.sp)
        }
    }
}

// ───────────────────────── Kirish ─────────────────────────

@Composable
fun LoginScreen(prefs: SharedPreferences, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf(Api.base) }
    var showServer by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("+998") }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("passenger") }
    var ref by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var devCode by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(PageBg).verticalScroll(rememberScrollState())) {
        LangBar()
        // Sariq sarlavha
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Yellow)
                .padding(horizontal = 24.dp, vertical = 36.dp),
        ) {
            Column {
                Image(
                    painter = painterResource(R.drawable.toxa_logo),
                    contentDescription = "TOXA",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(16.dp))
                Text("TOXA", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = 4.sp)
                Text("Shahar ichida tez va qulay safar", fontSize = 15.sp, color = Ink.copy(alpha = 0.75f))
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Panel {
                Text(
                    if (sent) "Tasdiqlash kodi" else "Kirish",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Segmented(
                    listOf("passenger" to "🧍 Yo'lovchi", "driver" to "🚖 Haydovchi"),
                    role,
                ) { role = it }
                Field(phone, { phone = it }, "Telefon raqam", keyboard = KeyboardType.Phone)
                Field(name, { name = it }, "Ismingiz (birinchi marta kirganda)")
                if (role == "driver") Field(ref, { ref = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, "Taklif kodi (ixtiyoriy)")

                if (!sent) {
                    BigButton("Kod olish", enabled = !busy) {
                        scope.launch {
                            busy = true; msg = ""
                            try {
                                Api.base = base.trim().trimEnd('/')
                                val r = io { Api.call("POST", "/auth/request", JSONObject().put("phone", phone.trim())) }
                                sent = true
                                // Test rejimida server kodni o'zi qaytaradi. Haqiqiy SMS ulanganda bu o'chadi.
                                devCode = if (r.has("devCode")) r.optString("devCode") else ""
                            } catch (e: Exception) { msg = e.message ?: "Xato" }
                            busy = false
                        }
                    }
                } else {
                    if (devCode.isNotEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Soft).padding(12.dp),
                        ) {
                            Text("Test kodi: $devCode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Field(code, { code = it }, "SMS kod", keyboard = KeyboardType.Number)
                    BigButton("Kirish", enabled = !busy) {
                        scope.launch {
                            busy = true
                            try {
                                val body = JSONObject()
                                    .put("phone", phone.trim()).put("code", code.trim())
                                    .put("name", name.trim()).put("role", role)
                                if (role == "driver" && ref.isNotBlank()) body.put("ref", ref.trim())
                                val r = io { Api.call("POST", "/auth/verify", body) }
                                val u = r.getJSONObject("user")
                                Api.token = r.getString("token")
                                prefs.edit()
                                    .putString("token", Api.token).putString("role", u.getString("role"))
                                    .putString("base", Api.base)
                                    .putString("name", u.optString("name")).putString("phone", u.optString("phone")).apply()
                                onDone(u.getString("role"))
                            } catch (e: Exception) { msg = e.message ?: "Xato" }
                            busy = false
                        }
                    }
                    TextButton(
                        onClick = { sent = false; code = ""; msg = ""; devCode = "" },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Raqamni o'zgartirish", color = Muted) }
                }
                if (busy) Text("Server javob kutilmoqda… (birinchi marta 30–60 soniya ketishi mumkin)", fontSize = 12.sp, color = Muted)
                ErrorText(msg)
            }

            // Server manzili: odatda o'zgartirish shart emas
            TextButton(onClick = { showServer = !showServer }, modifier = Modifier.fillMaxWidth()) {
                Text("⚙ Server sozlamalari", color = Muted, fontSize = 13.sp)
            }
            if (showServer) Field(base, { base = it }, "Server manzili", keyboard = KeyboardType.Uri)
        }
    }
}

// ───────────────────────── Yangilanish ─────────────────────────

@Composable
fun UpdateDialog(tag: String? = null, appName: String = "TOXA") {
    val ctx = LocalContext.current
    var update by remember { mutableStateOf<Pair<Int, String>?>(null) }
    LaunchedEffect(Unit) {
        // Yangilanishni tekshirishda xato (internet yo'q, GitHub javob bermadi) ilovani yopib qo'ymasligi kerak
        val r = try { latestRelease(tag) } catch (e: Exception) { null }
        if (r != null && r.first > currentVersionCode(ctx)) update = r
    }
    var installUrl by remember { mutableStateOf<String?>(null) }
    installUrl?.let { InstallDialog(it, appName) { installUrl = null; update = null } }
    val u = update ?: return
    if (installUrl != null) return
    AlertDialog(
        onDismissRequest = { update = null },
        shape = RoundedCornerShape(24.dp),
        containerColor = Card,
        title = { Text("Yangi versiya bor", fontWeight = FontWeight.Bold) },
        text = { Text("$appName ilovasining yangi versiyasi chiqdi. Ilovaning o'zida yuklab o'rnatiladi.") },
        confirmButton = {
            BigButton("Yangilash", Modifier.width(150.dp)) { installUrl = u.second }
        },
        dismissButton = { TextButton(onClick = { update = null }) { Text("Keyinroq", color = Muted) } },
    )
}
