package uz.taksi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("taksi", Context.MODE_PRIVATE)
        Api.token = prefs.getString("token", "") ?: ""
        Api.base = prefs.getString("base", Api.base) ?: Api.base
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App(prefs) } } }
    }
}

@Composable
fun App(prefs: SharedPreferences) {
    var role by remember { mutableStateOf(prefs.getString("role", "") ?: "") }
    val logout = {
        prefs.edit().remove("token").remove("role").apply()
        Api.token = ""
        role = ""
    }
    when (role) {
        "passenger" -> PassengerScreen(logout)
        "driver" -> DriverScreen(logout)
        else -> LoginScreen(prefs) { role = it }
    }
}

// ───────────────────────── Umumiy elementlar ─────────────────────────

@Composable
fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
fun Header(title: String, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onLogout) { Text("Chiqish") }
    }
}

@Composable
fun OrderCard(o: JSONObject, extra: @Composable ColumnScope.() -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(STATUS[o.optString("status")] ?: o.optString("status"), style = MaterialTheme.typography.titleMedium)
            Text("${o.optString("from")} → ${o.optString("to")}")
            Text(String.format("%.1f km · %s", o.optDouble("km"), som(o.optLong("price"))))
            Text(payText(o))
            extra()
        }
    }
}

/** Telefondagi navigator ilovasida (Google Maps, Yandex, 2GIS…) nuqtani ochadi. */
fun openNavigator(ctx: Context, lat: Double, lng: Double, label: String) {
    try {
        val uri = Uri.parse("geo:0,0?q=$lat,$lng(${Uri.encode(label)})")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Except
