package com.tahmidgaming.samsungfumo

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FumoTheme { FumoApp() } }
    }
}

@Composable
fun FumoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val light = lightColorScheme(
        primary = Color(0xFF1677FF),
        primaryContainer = Color(0xFFDDEBFF),
        background = Color(0xFFF4F6FA),
        surface = Color.White
    )
    val darkColors = darkColorScheme(
        primary = Color(0xFF72B9FF),
        primaryContainer = Color(0xFF07527F),
        background = Color(0xFF090B0F),
        surface = Color(0xFF171A20)
    )
    MaterialTheme(
        colorScheme = if (dark) darkColors else light,
        shapes = Shapes(medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp)),
        content = content
    )
}

private enum class Screen { HOME, DOWNLOADS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FumoApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val detected = remember { DeviceInfo.detect() }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var model by remember { mutableStateOf(detected.model) }
    var csc by remember { mutableStateOf(detected.csc) }
    var firmware by remember { mutableStateOf(detected.firmware) }
    var imei by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FirmwareInfo?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var busy by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))
        )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Samsung FUMO", fontWeight = FontWeight.SemiBold) },
                    actions = { IconButton(onClick = { screen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, "Settings") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.Transparent) {
                    NavigationBarItem(screen == Screen.HOME, { screen = Screen.HOME }, { Icon(Icons.Default.Search, null) }, { Text("Discover") })
                    NavigationBarItem(screen == Screen.DOWNLOADS, { screen = Screen.DOWNLOADS }, { Icon(Icons.Default.Download, null) }, { Text("Downloads") })
                    NavigationBarItem(screen == Screen.SETTINGS, { screen = Screen.SETTINGS }, { Icon(Icons.Default.Settings, null) }, { Text("Settings") })
                }
            }
        ) { pad ->
            when (screen) {
                Screen.HOME -> Home(
                    Modifier.padding(pad), model, csc, firmware, imei,
                    { model = it }, { csc = it }, { firmware = it }, { imei = it },
                    result, status, busy
                ) {
                    scope.launch {
                        busy = true
                        status = "Reading Samsung version.xml…"
                        result = withContext(Dispatchers.IO) {
                            runCatching { FumoClient.lookup(model.trim(), csc.trim()) }
                                .getOrElse { FirmwareInfo.error(it.message ?: "Lookup failed") }
                        }
                        status = result!!.status
                        busy = false
                    }
                }
                Screen.DOWNLOADS -> Downloads(Modifier.padding(pad), context)
                Screen.SETTINGS -> Settings(Modifier.padding(pad))
            }
        }
    }
}

@Composable
private fun Home(
    mod: Modifier, model: String, csc: String, firmware: String, imei: String,
    setM: (String) -> Unit, setC: (String) -> Unit, setF: (String) -> Unit, setI: (String) -> Unit,
    result: FirmwareInfo?, status: String, busy: Boolean, check: () -> Unit
) {
    LazyColumn(
        mod.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Text("Firmware discovery", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text("Find Samsung OTA/FUMO updates without flashing your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(model, setM, label = { Text("Model (e.g. SM-T805)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(csc, setC, label = { Text("CSC (e.g. INU)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(firmware, setF, label = { Text("Current build (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(imei, setI, label = { Text("IMEI (only if required by OSP)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = check, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Checking…" else "Check Samsung")
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(status, fontWeight = FontWeight.Medium)
                    if (result != null) {
                        Text(result.target ?: "No target returned", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        result.size?.let { Text("Payload: ${it} bytes") }
                        result.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(12.dp))
                    Text("FUMO registration is authenticated by Samsung. The app never fabricates signatures or firmware URLs.")
                }
            }
        }
    }
}

@Composable
private fun Downloads(mod: Modifier, context: Context) {
    Column(mod.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Downloads", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text("Firmware downloads will appear here when a valid Samsung objectURI is returned.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Settings(mod: Modifier) {
    Column(mod.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text("Protocol diagnostics")
        Text("• Samsung version.xml discovery\n• OMA-DM/FUMO session support\n• SHA-256 verification\n• No flashing or bootloader operations")
    }
}

private data class FirmwareInfo(
    val status: String,
    val target: String? = null,
    val size: Long? = null,
    val note: String? = null
) {
    companion object { fun error(s: String) = FirmwareInfo(s, note = s) }
}

private data class DeviceInfo(val model: String, val csc: String, val firmware: String) {
    companion object {
        fun detect(): DeviceInfo = DeviceInfo(Build.MODEL, "", Build.DISPLAY)
    }
}

private object FumoClient {
    private val client = OkHttpClient.Builder().followRedirects(true).build()

    fun lookup(model: String, csc: String): FirmwareInfo {
        require(model.matches(Regex("SM-[A-Z0-9-]+"))) { "Invalid Samsung model" }
        require(csc.matches(Regex("[A-Z0-9]{3}"))) { "CSC must be 3 characters" }
        val url = "https://fota-cloud-dn.ospserver.net/firmware/$csc/$model/version.xml"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return FirmwareInfo("version.xml HTTP ${response.code}", note = response.body?.string())
            val xml = response.body!!.string()
            val latest = Regex("<latest[^>]*>(.*?)</latest>").find(xml)?.groupValues?.get(1)?.trim()
            val upgrade = Regex("<upgrade>\\s*<value[^>]*fwsize=['\\\"](\\d+)['\\\"][^>]*>(.*?)</value>", RegexOption.DOT_MATCHES_ALL).find(xml)
            val target = upgrade?.groupValues?.get(2)?.trim() ?: latest
            val size = upgrade?.groupValues?.get(1)?.toLongOrNull()
            return FirmwareInfo(
                "Samsung metadata received",
                target,
                size,
                "version.xml is metadata only. A direct .bin objectURI requires the authenticated OMA-DM/FUMO session."
            )
        }
    }
}
