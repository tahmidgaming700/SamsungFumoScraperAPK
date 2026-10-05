package com.tahmidgaming.osupdater

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

private data class GalaxyModel(val model: String, val name: String, val csc: String, val lineage: String)
private data class LBuild(val filename: String, val url: String, val sha256: String?, val size: Long?)

private val MODELS = listOf(
    GalaxyModel("SM-T805", "Galaxy Tab S 10.5 LTE", "BNG", "chagalllte"),
    GalaxyModel("SM-T805K", "Galaxy Tab S 10.5 LTE Korea", "KOO", "chagalllte"),
    GalaxyModel("SM-T807", "Galaxy Tab S 10.5 LTE", "TMB", "chagalllte"),
    GalaxyModel("SM-T800", "Galaxy Tab S 10.5 Wi-Fi", "XAR", "chagallwifi"),
    GalaxyModel("SM-T705", "Galaxy Tab S 8.4 LTE", "BNG", "klimtlte")
)

class MainActivity : ComponentActivity() {
    private val http = OkHttpClient()
    private val storage = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        setContent { App() }
    }

    private fun root(command: String): String = runCatching {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out
    }.getOrDefault("")

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun enqueue(c: Context, url: String, name: String, referer: String? = null) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription("OS Updater for Galaxy Tab S")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            .setMimeType("application/octet-stream")
            .addRequestHeader("User-Agent", "OSUpdaterGalaxyTabS/1.0")
        if (referer != null) request.addRequestHeader("Referer", referer)
        val id = (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(c, "Download started (#" + id + ")", Toast.LENGTH_LONG).show()
    }

    private suspend fun archive(codename: String): List<LBuild> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://lineage-archive.timschumi.net/api/builds").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Archive HTTP " + response.code)
            val array = JSONArray(response.body?.string() ?: "[]")
            val rows = (0 until array.length()).map { array.getJSONObject(it) }
                .filter { it.optString("device") == codename }
                .sortedByDescending { it.optString("filename") }
                .take(15)
            rows.mapNotNull { row ->
                val id = row.optLong("id", -1)
                if (id < 0) null else {
                    val detail = Request.Builder()
                        .url("https://lineage-archive.timschumi.net/api/builds/" + id)
                        .build()
                    http.newCall(detail).execute().use { d ->
                        if (!d.isSuccessful) null else {
                            val o = JSONObject(d.body?.string() ?: "{}")
                            LBuild(
                                o.optString("filename"),
                                o.optString("url"),
                                o.optString("sha256").takeIf { it.isNotBlank() },
                                o.optLong("filesize").takeIf { it > 0 }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun prepareRecovery(filename: String): Boolean {
        val safe = filename.substringAfterLast('/').takeIf {
            it.matches(Regex("[A-Za-z0-9._+()\\- ]+"))
        } ?: return false
        val script = "install /sdcard/Download/" + safe + "\n"
        val command = "mkdir -p /cache/recovery /data/cache/recovery /persist/cache/recovery 2>/dev/null; " +
            "for f in /cache/recovery/openrecoveryscript /data/cache/recovery/openrecoveryscript /persist/cache/recovery/openrecoveryscript; do " +
            "d=\$(dirname \"\$f\"); if [ -d \"\$d\" ]; then printf '%s' " + shellQuote(script) +
            " > \"\$f\"; chmod 0644 \"\$f\"; echo OK; break; fi; done"
        return root(command).contains("OK")
    }

    private fun flashDd(image: File, partition: String): String {
        if (!image.exists()) return "Image not found"
        if (!partition.matches(Regex("[A-Za-z0-9_+.-]+"))) return "Invalid partition"
        val destination = "/dev/block/by-name/" + partition
        val output = root("[ -b '" + destination + "' ] || exit 3; dd if=" +
            shellQuote(image.absolutePath) + " of='" + destination + "' bs=4M conv=fsync; sync; echo DD_OK")
        return if (output.contains("DD_OK")) "Flashed " + partition + " successfully"
        else "dd failed: " + output.takeLast(240)
    }

    private fun partitionNames(): List<String> =
        root("ls -1 /dev/block/by-name 2>/dev/null").lines()
            .filter { it.matches(Regex("[A-Za-z0-9_+.-]+")) }.distinct().sorted()

    private fun extractZip(source: File, output: File): Int {
        output.mkdirs()
        var count = 0
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(output, entry.name)
                if (!target.canonicalPath.startsWith(output.canonicalFile.canonicalPath + File.separator)) continue
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zip.copyTo(it) }
                    count++
                }
            }
        }
        return count
    }

    private fun extractTar(source: File, output: File): Int {
        output.mkdirs()
        val p = ProcessBuilder("sh", "-c",
            "tar -xf " + shellQuote(source.absolutePath) + " -C " + shellQuote(output.absolutePath))
            .redirectErrorStream(true).start()
        p.waitFor()
        if (p.exitValue() != 0)
            root("tar -xf " + shellQuote(source.absolutePath) + " -C " + shellQuote(output.absolutePath))
        return output.walkTopDown().count { it.isFile }
    }

    @Composable
    private fun App() {
        var page by remember { mutableStateOf("home") }
        var model by remember { mutableStateOf(MODELS[0]) }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(pageTitle(page)) },
                navigationIcon = {
                    if (page != "home") IconButton({ page = "home" }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                when (page) {
                    "home" -> Home(model) { page = it }
                    "models" -> Models(model) { model = it; page = "home" }
                    "lineage" -> Lineage(model)
                    "stock" -> Browser(
                        "https://samfw.com/firmware/" + model.model + "/" + model.csc,
                        "SamFW • " + model.model
                    )
                    "ota" -> Browser("https://fumo.timschneeberger.me/", "Samsung FUMO • " + model.model)
                    "extract" -> Extract()
                    "flash" -> Flash()
                }
            }
        }
    }

    private fun pageTitle(page: String) = when (page) {
        "models" -> "Galaxy Tab S models"
        "lineage" -> "LineageOS archive"
        "stock" -> "Stock firmware"
        "ota" -> "Stock OTA"
        "extract" -> "Extract firmware"
        "flash" -> "Flashing tools"
        else -> "OS Updater for Galaxy Tab S"
    }

    @Composable
    private fun Home(model: GalaxyModel, go: (String) -> Unit) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Software Update", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Galaxy Tab S firmware, LineageOS and recovery tools")
                        Spacer(Modifier.height(12.dp))
                        Text(model.model, fontWeight = FontWeight.Bold)
                        Text(model.name)
                        Text("CSC " + model.csc + " • " + model.lineage)
                        Spacer(Modifier.height(8.dp))
                        Button({ go("models") }, Modifier.fillMaxWidth()) { Text("Change model") }
                    }
                }
            }
            item { Action("LineageOS", "TimSchumi archive • OTA-ready package", Icons.Default.SystemUpdate) { go("lineage") } }
            item { Action("Stock firmware", "SamFW server workflow", Icons.Default.Download) { go("stock") } }
            item { Action("Stock OTA", "Samsung FUMO / OMA-DM workflow", Icons.Default.Refresh) { go("ota") } }
            item { Action("Extract firmware", "ZIP → TAR.MD5 → images", Icons.Default.FolderZip) { go("extract") } }
            item { Action("Flashing tools", "TWRP / OrangeFox OpenRecoveryScript • dd", Icons.Default.FlashOn) { go("flash") } }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Root flashing can permanently brick the tablet. Verify the model and partition before writing.")
                    }
                }
            }
        }
    }

    @Composable
    private fun Action(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, go: () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
                Button(go) { Text("Open") }
            }
        }
    }

    @Composable
    private fun Models(current: GalaxyModel, select: (GalaxyModel) -> Unit) {
        LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MODELS) { model ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.model, fontWeight = FontWeight.Bold)
                            Text(model.name)
                            Text("CSC " + model.csc + " • " + model.lineage)
                        }
                        FilterChip(current.model == model.model, { select(model) }, label = { Text("Select") })
                    }
                }
            }
        }
    }

    @Composable
    private fun Lineage(model: GalaxyModel) {
        val context = LocalContext.current
        var builds by remember { mutableStateOf<List<LBuild>>(emptyList()) }
        var status by remember { mutableStateOf("Press Check archive") }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("TimSchumi LineageOS archive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(model.model + " • " + model.lineage + ". Archived builds are unofficial.")
            Spacer(Modifier.height(12.dp))
            Button({
                lifecycleScope.launch {
                    status = "Checking…"
                    runCatching { archive(model.lineage) }
                        .onSuccess { builds = it; status = it.size.toString() + " builds found" }
                        .onFailure { status = it.message ?: "Archive error" }
                }
            }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Check archive")
            }
            Spacer(Modifier.height(8.dp))
            Text(status)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(builds) { build ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(build.filename, fontWeight = FontWeight.Bold)
                            build.size?.let { Text("Size: " + (it / 1024 / 1024) + " MB") }
                            build.sha256?.let { Text("SHA-256: " + it, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button({ enqueue(context, build.url, build.filename, "https://lineage-archive.timschumi.net/") }) { Text("Download") }
                                OutlinedButton({ enqueue(context, build.url, build.filename, "https://lineage-archive.timschumi.net/") }) { Text("OTA package") }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Browser(url: String, label: String) {
        val context = LocalContext.current
        Column(Modifier.fillMaxSize()) {
            Text(label, Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
            AndroidView({ makeWebView(context, url) }, Modifier.fillMaxSize())
        }
    }

    private fun makeWebView(context: Context, url: String): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(-1, -1)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Toast.makeText(context, "Page error: " + error.description, Toast.LENGTH_LONG).show()
            }
        }
        setDownloadListener(DownloadListener { url2, userAgent, disposition, mime, _ ->
            if (url2.startsWith("http")) {
                val name = URLUtil.guessFileName(url2, disposition, mime).ifBlank { "firmware.bin" }
                val request = DownloadManager.Request(Uri.parse(url2))
                    .setTitle(name).setDescription("OS Updater firmware")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    .setMimeType(mime ?: "application/octet-stream")
                    .addRequestHeader("User-Agent", userAgent ?: settings.userAgentString)
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(context, "Download started", Toast.LENGTH_LONG).show()
            }
        })
        loadUrl(url)
    }

    @Composable
    private fun Extract() {
        val context = LocalContext.current
        var path by remember { mutableStateOf("") }
        var result by remember { mutableStateOf("Download the firmware first, then enter its local path.") }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Extraction procedure", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("1. Download stock firmware. 2. Extract ZIP. 3. Extract TAR.MD5. 4. Inspect images. 5. Select only the exact image/partition pair for dd.")
            OutlinedTextField(path, { path = it }, Modifier.fillMaxWidth(), label = { Text("Firmware path") }, singleLine = true)
            Button({
                val source = File(path)
                if (!source.exists()) result = "File not found"
                else {
                    val output = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "OSUpdater/" + source.nameWithoutExtension)
                    val count = if (source.extension.equals("zip", true)) extractZip(source, output) else extractTar(source, output)
                    result = "Extracted " + count + " files to " + output.absolutePath
                }
            }, Modifier.fillMaxWidth()) { Text("Extract") }
            Text(result)
        }
    }

    @Composable
    private fun Flash() {
        var image by remember { mutableStateOf("") }
        var partition by remember { mutableStateOf("boot") }
        var result by remember { mutableStateOf("Root: " + root("id").trim().ifBlank { "not granted" }) }
        var partitions by remember { mutableStateOf<List<String>>(emptyList()) }
        val recovery = root("getprop ro.twrp.version; getprop ro.orangefox.version; getprop ro.of.version")
            .lines().filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "not detected" }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Flashing tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Root: " + root("id").trim().ifBlank { "not granted" })
            Text("Recovery: " + recovery)
            Button({ partitions = partitionNames(); result = partitions.size.toString() + " by-name partitions detected" }, Modifier.fillMaxWidth()) { Text("Detect partitions") }
            if (partitions.isNotEmpty()) Text("Detected: " + partitions.joinToString(", "))
            OutlinedTextField(image, { image = it }, Modifier.fillMaxWidth(), label = { Text("Image path") }, singleLine = true)
            OutlinedTextField(partition, { partition = it }, Modifier.fillMaxWidth(), label = { Text("Partition name for dd") }, singleLine = true)
            Button({ result = flashDd(File(image), partition) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FlashOn, null)
                Spacer(Modifier.width(8.dp))
                Text("Flash image with dd")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ result = if (prepareRecovery(File(image).name)) "TWRP OpenRecoveryScript prepared" else "Could not prepare script" }, Modifier.weight(1f)) { Text("TWRP") }
                Button({ result = if (prepareRecovery(File(image).name)) "OrangeFox OpenRecoveryScript prepared" else "Could not prepare script" }, Modifier.weight(1f)) { Text("OrangeFox") }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null)
                    Spacer(Modifier.width(8.dp))
                    Text("dd writes directly to a block device. A wrong target can hard-brick the tablet. Bootloader/security restrictions are not bypassed.")
                }
            }
            Text(result)
        }
    }
}
