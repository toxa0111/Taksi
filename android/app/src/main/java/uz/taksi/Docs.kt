package uz.taksi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Haydovchi hujjatlari (foto nazorat). Server bilan bir xil kalitlar. */
val DOC_LABELS = listOf(
    "face" to "🙂 Haydovchi surati",
    "selfie" to "🤳 Foto nazorat (selfi)",
    "license" to "🪪 Haydovchilik guvohnomasi",
    "techpass" to "📄 Mashina tex pasporti",
    "carplate" to "🚗 Mashina raqami (surat)",
)

/** Rasmni o'qiydi, aylantiradi (EXIF), 1280 pikselgacha kichraytiradi va JPEG (base64) qiladi. */
fun jpegBase64(ctx: Context, uri: Uri): String? {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = ctx.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val rotate = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        if (rotate != 0f || scale < 1f) {
            val m = Matrix()
            if (rotate != 0f) m.postRotate(rotate)
            if (scale < 1f) m.postScale(scale, scale)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        return null
    }
}

@Composable
private fun DocStatus(status: String?) {
    when (status) {
        "approved" -> Pill("Tasdiqlangan", Color(0xFFE1F5E9), Color(0xFF14753F))
        "rejected" -> Pill("Rad etildi", Color(0xFFFCE4E4), Color(0xFFA32626))
        "pending" -> Pill("Tekshirilmoqda", Color(0xFFFFF1C2), Color(0xFF8A6100))
        else -> Pill("Yuklanmagan", Chip, Muted)
    }
}

/** Haydovchi Kabinetidagi bo'lim: hujjat rasmlarini yuklash va mashina raqami. */
@Composable
fun DriverDocsPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<JSONObject?>(null) }
    var plate by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var pendingKind by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        try {
            val j = io { Api.call("GET", "/driver/docs") }
            docs = j.optJSONObject("docs")
            if (plate.isBlank()) plate = j.optString("plate")
            verified = j.optBoolean("verified")
        } catch (e: Exception) { }
    }

    fun upload(kind: String, uri: Uri) {
        scope.launch {
            busy = kind; msg = ""
            try {
                val data = io { jpegBase64(ctx, uri) } ?: throw Exception("Rasmni o'qib bo'lmadi")
                io { Api.call("POST", "/driver/docs", JSONObject().put("kind", kind).put("data", data)) }
                tick++
            } catch (e: Exception) { msg = e.message ?: "Xato" }
            busy = ""
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && pendingKind.isNotEmpty()) upload(pendingKind, uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null && pendingKind.isNotEmpty()) upload(pendingKind, u)
    }

    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hujjatlar va tasdiqlash", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (verified) Pill("✅ Tasdiqlangan", Color(0xFFE1F5E9), Color(0xFF14753F))
        }
        Text("Rasmlarni aniq va to'liq oling. Admin tekshirib tasdiqlaydi.", fontSize = 12.sp, color = Muted)
        for ((kind, label) in DOC_LABELS) {
            val d = docs?.optJSONObject(kind)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    DocStatus(d?.optString("status"))
                }
                if (d?.optString("status") == "rejected" && d.optString("note").isNotBlank()) {
                    Text("Sabab: ${d.optString("note")}", fontSize = 12.sp, color = Bad)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("📷 Kamera", Modifier.weight(1f), enabled = busy.isEmpty()) {
                        try {
                            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                            val f = File(dir, "doc_$kind.jpg")
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                            pendingKind = kind; cameraUri = uri
                            camera.launch(uri)
                        } catch (e: Exception) { msg = "Kamerani ochib bo'lmadi" }
                    }
                    GhostButton("🖼 Galereya", Modifier.weight(1f), enabled = busy.isEmpty()) {
                        pendingKind = kind
                        gallery.launch("image/*")
                    }
                }
                if (busy == kind) Text("Yuklanmoqda…", fontSize = 12.sp, color = Muted)
            }
        }
        Field(plate, { plate = it.uppercase() }, "Mashina raqami (masalan 01 A 123 BC)")
        GhostButton("Raqamni saqlash") {
            scope.launch {
                msg = ""
                try {
                    val j = io { Api.call("POST", "/driver/plate", JSONObject().put("plate", plate)) }
                    plate = j.optString("plate")
                    verified = j.optBoolean("verified")
                    msg = "Saqlandi"
                } catch (e: Exception) { msg = e.message ?: "Xato" }
            }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp, color = Muted)
    }
}

// ───────────────────────── Admin: hujjatlarni tekshirish ─────────────────────────

@Composable
fun AdminDocs() {
    var filter by remember { mutableStateOf("pending") }
    var tick by remember { mutableStateOf(0) }
    val data by rememberPoll("/admin/drivers?status=$filter", tick)
    var open by remember { mutableStateOf<JSONObject?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TabTitle("Hujjatlar")
        Segmented(listOf("pending" to "Kutayotgan", "verified" to "Tasdiqlangan", "all" to "Hammasi"), filter) { filter = it }
        val list = data?.optJSONArray("drivers")?.items() ?: emptyList()
        if (data != null && list.isEmpty()) Text("Haydovchi yo'q", fontSize = 13.sp, color = Muted)
        for (d in list) {
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.optString("name"), fontWeight = FontWeight.Bold)
                        Text(d.optString("phone") + if (d.optString("plate").isNotBlank()) " · " + d.optString("plate") else "", fontSize = 12.sp, color = Muted)
                    }
                    if (d.optBoolean("verified")) Pill("✅ Tasdiqlangan", Color(0xFFE1F5E9), Color(0xFF14753F))
                }
                val docs = d.optJSONObject("docs")
                for ((kind, label) in DOC_LABELS) {
                    val x = docs?.optJSONObject(kind)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        DocStatus(x?.optString("status"))
                    }
                }
                GhostButton("Ko'rish va tekshirish") { open = d }
            }
        }
    }
    open?.let { d -> DriverDocsDialog(d) { open = null; tick++ } }
}

@Composable
private fun DriverDocsDialog(d: JSONObject, onClose: () -> Unit) {
    val id = d.optString("id")
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Card,
        title = { Text(d.optString("name") + " · " + d.optString("plate").ifBlank { "raqam yo'q" }, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val docs = d.optJSONObject("docs")
                for ((kind, label) in DOC_LABELS) {
                    val x = docs?.optJSONObject(kind) ?: continue
                    DocReviewRow(id, kind, label, x.optString("status"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Yopish") } },
    )
}

@Composable
private fun DocReviewRow(uid: String, kind: String, label: String, initial: String) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(initial) }
    var reject by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val bmp by produceState<ImageBitmap?>(null, uid, kind) {
        value = try {
            io {
                val b = Api.bytes("/docs/$uid/$kind")
                BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
            }
        } catch (e: Exception) { null }
    }
    fun review(approve: Boolean) {
        scope.launch {
            err = ""
            try {
                io { Api.call("POST", "/admin/docs/$uid/$kind/review", JSONObject().put("approve", approve).put("note", note)) }
                status = if (approve) "approved" else "rejected"
                reject = false
            } catch (e: Exception) { err = e.message ?: "Xato" }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            DocStatus(status)
        }
        val b = bmp
        if (b != null) {
            Image(b, contentDescription = label, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), contentScale = ContentScale.Fit)
        } else {
            Text("Rasm yuklanmoqda…", fontSize = 12.sp, color = Muted)
        }
        if (reject) {
            Field(note, { note = it }, "Rad etish sababi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DangerButton("Rad etish", Modifier.weight(1f)) { review(false) }
                GhostButton("Orqaga", Modifier.weight(1f)) { reject = false }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("✅ Tasdiqlash", Modifier.weight(1f), enabled = status != "approved") { review(true) }
                GhostButton("❌ Rad", Modifier.weight(1f)) { reject = true }
            }
        }
        if (err.isNotEmpty()) Text(err, fontSize = 12.sp, color = Bad)
    }
}
