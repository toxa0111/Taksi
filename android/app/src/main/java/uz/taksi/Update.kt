package uz.taksi

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** APK ni ilovaning o'zida yuklaydi (foiz bilan). Brauzer kerak emas. */
fun downloadApk(ctx: Context, url: String, onProgress: (Int) -> Unit): File {
    val dir = File(ctx.cacheDir, "updates")
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val out = File(dir, "update.apk")
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 15000
    c.readTimeout = 30000
    c.instanceFollowRedirects = true
    if (c.responseCode !in 200..299) throw Exception("Yuklab bo'lmadi (HTTP ${c.responseCode})")
    val total = c.contentLengthLong
    c.inputStream.use { inp ->
        out.outputStream().use { o ->
            val buf = ByteArray(32768)
            var read = 0L
            var last = -1
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                o.write(buf, 0, n)
                read += n
                if (total > 0) {
                    val p = (read * 100 / total).toInt()
                    if (p != last) { last = p; onProgress(p) }
                }
            }
        }
    }
    if (out.length() < 100_000) throw Exception("Yuklangan fayl buzilgan, qayta urinib ko'ring")
    return out
}

fun canInstallApks(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

/** Tizimning o'rnatish oynasini ochadi (Android bitta tasdiqlash bosishini talab qiladi). */
fun openInstaller(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}

/**
 * Yangilanishni yuklab, o'rnatishni boshlaydi. Birinchi marta Android "shu ilovaga o'rnatishga ruxsat"
 * so'raydi: ruxsat berib orqaga qaytilgach, "Davom etish" bosiladi.
 */
@Composable
fun InstallDialog(url: String, appName: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf("loading") }   // loading | perm | ready | error
    var err by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<File?>(null) }
    var attempt by remember { mutableStateOf(0) }

    fun tryInstall(f: File) {
        if (!canInstallApks(ctx)) { state = "perm"; return }
        try { openInstaller(ctx, f); state = "ready" }
        catch (e: Exception) { err = e.message ?: "O'rnatib bo'lmadi"; state = "error" }
    }

    LaunchedEffect(url, attempt) {
        state = "loading"; progress = 0
        try {
            val f = io { downloadApk(ctx, url) { p -> progress = p } }
            file = f
            tryInstall(f)
        } catch (e: Exception) {
            err = e.message ?: "Yuklab bo'lmadi"
            state = "error"
        }
    }

    AlertDialog(
        onDismissRequest = { if (state != "loading") onClose() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Text(
                when (state) {
                    "loading" -> "Yuklanmoqda…"
                    "perm" -> "Ruxsat kerak"
                    "error" -> "Xato"
                    else -> "O'rnatish oynasi ochildi"
                },
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    "loading" -> {
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Yellow, trackColor = Hairline)
                        Text("$progress %", color = Muted)
                    }
                    "perm" -> Text("Android $appName ga yangilanishni o'rnatishga ruxsat so'raydi. “Sozlamalar” ni bosib, ruxsatni yoqing, so'ng orqaga qaytib “Davom etish” ni bosing.")
                    "error" -> Text(err)
                    else -> Text("Chiqqan oynada “O'rnatish” (Update) ni bosing. O'rnatilgach ilova o'zi qayta ochiladi.")
                }
            }
        },
        confirmButton = {
            when (state) {
                "perm" -> TextButton(onClick = {
                    val f = file
                    if (canInstallApks(ctx) && f != null) tryInstall(f)
                    else try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) { }
                }) { Text("Sozlamalar / Davom etish", fontWeight = FontWeight.Bold) }
                "error" -> TextButton(onClick = { attempt++ }) { Text("Qayta urinish", fontWeight = FontWeight.Bold) }
                "ready" -> TextButton(onClick = { file?.let { tryInstall(it) } }) { Text("Qayta ochish", fontWeight = FontWeight.Bold) }
                else -> {}
            }
        },
        dismissButton = {
            if (state != "loading") TextButton(onClick = onClose) { Text("Yopish", color = Muted) }
        },
    )
}
