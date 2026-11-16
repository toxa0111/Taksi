package uz.taksi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ilova kutilmaganda yopilsa, xato matni saqlanadi va keyingi ochilishda ko'rsatiladi (tashxis uchun). */
@Composable
fun CrashScreen(trace: String, onContinue: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Text("Ilova oldingi safar kutilmaganda yopildi", fontSize = 18.sp)
            androidx.compose.material3.Text("Quyidagi matn skrinshotini yuboring.", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onContinue) { androidx.compose.material3.Text("Davom etish") }
                OutlinedButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
                }) { androidx.compose.material3.Text("Nusxa olish") }
            }
            SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                androidx.compose.material3.Text(trace, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
