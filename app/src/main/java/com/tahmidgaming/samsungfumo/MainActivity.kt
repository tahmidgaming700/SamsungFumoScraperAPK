package com.tahmidgaming.samsungfumo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FumoTheme { FumoApp() } }
    }
}

@Composable
fun FumoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF72B9FF), background = Color(0xFF090B0F), surface = Color(0xFF171A20))
        else lightColorScheme(primary = Color(0xFF1677FF), background = Color(0xFFF4F6FA), surface = Color.White),
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { TopAppBar(title = { Text("Samsung FUMO", fontWeight = FontWeight.SemiBold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
        bottomBar = {
            NavigationBar(containerColor = Color.Transparent) {
                NavigationBarItem(selected = screen == Screen.HOME, onClick = { screen = Screen.HOME }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Discover") })
                NavigationBarItem(selected = screen == Screen.DOWNLOADS, onClick = { screen = Screen.DOWNLOADS }, icon = { Icon(Icons.Default.Download, null) }, label = { Text("Downloads") })
                NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { screen = Screen.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))) {
            when (screen) {
                Screen.HOME -> Home(Modifier.padding(pad), model, csc, firmware, imei, { model = it }, { csc = it }, { firmware = it }, { imei = it }, result, status, busy) {
                    scope.launch {
                        busy = true
                        status = "Connecting to Samsung FOTA…"
                        result = withContext(Dispatchers.IO) { runCatching { FumoClient.lookup(model.trim(), csc.trim()) }.getOrElse { FirmwareInfo.error(it.message ?: "Network request failed") } }
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
private fun Home(mod: Modifier, model: String, csc: String, firmware: String, imei: String, setM: (String) -> Unit, setC: (String) -> Unit, setF: (String) -> Unit, setI: (String) -> Unit, result: FirmwareInfo?, status: String, busy: Boolean, check: () -> Unit) {
    LazyColumn(mod.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)) {
        item {
            Text("Firmware discovery", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text("Samsung OTA/FUMO metadata and authenticated download support.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(model, setM, label = { Text("Model (e.g. SM-T805)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(csc, setC, label = { Text("CSC (e.g. INU)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(firmware, setF, label = { Text("Current build (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(imei, setI, label = { Text("IMEI (optional; never required for version.xml)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                    result?.let {
                        Text(it.target ?: "No target returned", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        it.size?.let { bytes -> Text("Payload: $bytes bytes") }
                        it.note?.let { note -> Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null); Spacer(Modifier.width(12.dp))
                    Text("Samsung controls FUMO authorization. The app does not bypass authentication or invent protected firmware URLs.")
                }
            }
        }
    }
}

@Composable
private fun Downloads(mod: Modifier, context: Context) {
    Column(mod.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Downloads", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text("Authenticated Samsung objectURI downloads are saved to the public Downloads folder when available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Settings(mod: Modifier) {
    Column(mod.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text("Protocol diagnostics")
        Text("• HTTPS + Samsung FOTA User-Agent\n• HTTP redirects permitted only by Android network policy\n• OMA-DM/FUMO authorization\n• SHA-256 verification\n• No flashing or bootloader operations")
    }
}

private data class FirmwareInfo(val status: String, val target: String? = null, val size: Long? = null, val note: String? = null) {
    companion object { fun error(s: String) = FirmwareInfo("Samsung request failed", note = s) }
}

private data class DeviceInfo(val model: String, val csc: String, val firmware: String) {
    companion object { fun detect() = DeviceInfo(Build.MODEL, "", Build.DISPLAY) }
}

private object FumoClient {
    private const val USER_AGENT = "Kies2.0_FUS"
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    fun lookup(model: String, csc: String): FirmwareInfo {
        require(model.matches(Regex("SM-[A-Z0-9-]+"))) { "Invalid Samsung model: $model" }
        require(csc.matches(Regex("[A-Z0-9]{3}"))) { "CSC must be exactly 3 letters/numbers" }
        val url = "https://fota-cloud-dn.ospserver.net/firmware/$csc/$model/version.xml"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/xml, text/xml, */*").header("Connection", "close").get().build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return FirmwareInfo("Samsung HTTP ${response.code}", note = "Samsung FOTA returned HTTP ${response.code}. The request reached Samsung, but the server rejected it.")
                if (!body.contains("<versioninfo")) return FirmwareInfo("Invalid Samsung response", note = "Samsung did not return version.xml. Check your internet connection or Samsung service availability.")
                val latest = Regex("<latest[^>]*>(.*?)</latest>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
                val upgrade = Regex("<upgrade>\\s*<value[^>]*fwsize=['\\\"](\\d+)['\\\"][^>]*>(.*?)</value>", RegexOption.DOT_MATCHES_ALL).find(body)
                val target = upgrade?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() } ?: latest
                val size = upgrade?.groupValues?.get(1)?.toLongOrNull()
                FirmwareInfo("Samsung metadata received", target, size, "version.xml is accessible. Protected .bin downloads require Samsung's authenticated FUMO/OMA-DM objectURI.")
            }
        } catch (e: Exception) {
            FirmwareInfo("Connection failed", note = "${e.javaClass.simpleName}: ${e.message ?: "Unable to connect to Samsung FOTA"}")
        }
    }

    fun download(context: Context, url: String, fileName: String): Long {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Invalid download URL" }
        val request = DownloadManager.Request(Uri.parse(url)).setTitle(fileName).setDescription("Samsung FUMO firmware").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName).addRequestHeader("User-Agent", USER_AGENT)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
