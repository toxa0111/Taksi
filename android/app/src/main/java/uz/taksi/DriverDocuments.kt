package uz.taksi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private val DRIVER_DOCUMENT_FIELDS = listOf(
    "licenseFront" to "Haydovchilik guvohnomasi — old tomoni",
    "licenseBack" to "Haydovchilik guvohnomasi — orqa tomoni",
    "vehicleDoc" to "Mashina hujjati (texnik pasport)",
    "platePhoto" to "Mashina davlat raqami",
)

@Composable
fun DriverDocumentsScreen(initial: JSONObject, onApproved: (JSONObject) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateMapOf<String, String>() }
    var plate by remember { mutableStateOf(initial.optString("vehiclePlate")) }
    var selectedKey by remember { mutableStateOf("") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val docs = initial.optJSONObject("documents") ?: JSONObject()

    LaunchedEffect(initial) {
        DRIVER_DOCUMENT_FIELDS.forEach { (key, _) ->
            val value = docs.optString(key)
            if (value.startsWith("data:image/jpeg;base64,")) photos[key] = value
        }
    }

    fun storeBitmap(bitmap: Bitmap) {
        val encoded = compressDriverPhoto(bitmap)
        if (encoded == null) message = "Surat hajmini 600 KB dan kichik qila olmadim. Qayta suratga oling."
        else { photos[selectedKey] = "data:image/jpeg;base64," + Base64.encodeToString(encoded, Base64.NO_WRAP); message = "" }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && selectedKey.isNotBlank()) {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Suratni o‘qib bo‘lmadi")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: throw IllegalStateException("Surat formati ochilmadi")
                storeBitmap(bitmap)
            } catch (e: Exception) { message = e.message ?: "Suratni yuklashda xato" }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null && selectedKey.isNotBlank()) {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Kamera suratini o‘qib bo‘lmadi")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: throw IllegalStateException("Kamera surati ochilmadi")
                storeBitmap(bitmap)
            } catch (e: Exception) { message = e.message ?: "Kamera suratini yuklashda xato" }
        }
        cameraFile?.delete()
        cameraFile = null
        cameraUri = null
    }

    Column(
        Modifier.fillMaxSize().background(PageBg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HAYDOVCHI HUJJATLARI", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Fg)
        val status = initial.optString("status", "pending")
        when (status) {
            "approved" -> Text("Hujjatlar tasdiqlangan. Ishga chiqishingiz mumkin.", color = Fg)
            "rejected" -> Panel {
                Text("Hujjatlar qayta yuborilishi kerak", fontWeight = FontWeight.Bold)
                Text("Rad etish sababi: " + initial.optString("reason"), color = Muted, fontSize = 13.sp)
            }
            else -> Panel(color = Ink) {
                Text("Tekshiruv kutilmoqda", fontWeight = FontWeight.Bold, color = Yellow)
                Text("Admin hujjatlarni tasdiqlamaguncha ishga chiqish yopiq bo‘ladi.", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
            }
        }
        Panel {
            Text("Avtomobil davlat raqami", fontWeight = FontWeight.Bold)
            Field(plate, { plate = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, "Masalan: 01A123BC")
        }
        DRIVER_DOCUMENT_FIELDS.forEach { (key, label) ->
            Panel {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val value = photos[key]
                if (value != null) {
                    val bitmap = remember(value) {
                        try { BitmapFactory.decodeByteArray(Base64.decode(value.substringAfter(','), Base64.DEFAULT), 0,
                            Base64.decode(value.substringAfter(','), Base64.DEFAULT).size)?.asImageBitmap() } catch (_: Exception) { null }
                    }
                    if (bitmap != null) Image(bitmap, contentDescription = label, modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                    Text("✓ Surat tanlandi", color = Fg, fontSize = 12.sp)
                } else Text("Surat hali tanlanmagan", color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        try {
                            selectedKey = key
                            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                            val file = File.createTempFile("driver_doc_", ".jpg", dir)
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            cameraFile = file; cameraUri = uri
                            camera.launch(uri)
                        } catch (e: Exception) { message = e.message ?: "Kamerani ochib bo‘lmadi" }
                    }) { Text("📷 Kamera") }
                    TextButton(onClick = { selectedKey = key; gallery.launch("image/*") }) { Text("Galereya") }
                }
            }
        }
        Text("Suratlar JPEG formatida yuboriladi. Har bir surat 600 KB gacha siqiladi.", fontSize = 12.sp, color = Muted)
        ErrorText(message)
        BigButton("Hujjatlarni tekshiruvga yuborish", enabled = !busy && plate.length == 8 && DRIVER_DOCUMENT_FIELDS.all { photos.containsKey(it.first) }) {
            scope.launch {
                busy = true; message = ""
                try {
                    val body = JSONObject().put("vehiclePlate", plate)
                    DRIVER_DOCUMENT_FIELDS.forEach { (key, _) -> body.put(key, photos[key]) }
                    io { Api.call("POST", "/driver/documents", body) }
                    val refreshed = io { Api.call("GET", "/driver/documents") }
                    onApproved(refreshed)
                    message = "Hujjatlar yuborildi."
                } catch (e: Exception) { message = e.message ?: "Yuborishda xato" }
                busy = false
            }
        }
        if (busy) Text("Yuborilmoqda…", color = Muted)
    }
}

private fun compressDriverPhoto(source: Bitmap): ByteArray? {
    var bitmap = source
    val maxSide = maxOf(source.width, source.height)
    if (maxSide > 1600) {
        val scale = 1600f / maxSide
        bitmap = Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }
    var quality = 88
    while (quality >= 45) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        if (bytes.size <= 600 * 1024) return bytes
        quality -= 8
    }
    return null
}
