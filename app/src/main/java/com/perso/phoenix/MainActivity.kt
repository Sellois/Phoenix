package com.perso.phoenix

import android.content.*
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Xml
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

val PAL = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A, 0xFF00ACC1).map { Color(it) }
const val DEF_FEED = "https://www.lemonde.fr/rss/une.xml"
const val DEF_RADIO = "https://icecast.radiofrance.fr/franceinfo-midfi.mp3"

fun load(sp: SharedPreferences) = sp.getString("apps", "")!!.split(";").filter { it.contains(":") }
    .map { it.substringBefore(":") to it.substringAfter(":").toInt() }
fun save(sp: SharedPreferences, l: List<Pair<String, Int>>) =
    sp.edit().putString("apps", l.joinToString(";") { "${it.first}:${it.second}" }).apply()
suspend fun get(u: String) = withContext(Dispatchers.IO) {
    URL(u).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }.getInputStream().bufferedReader().readText()
}
fun wmo(c: Int) = when (c) {
    0 -> "Dégagé"; in 1..3 -> "Nuageux"; 45, 48 -> "Brouillard"; in 51..67, in 80..82 -> "Pluie"
    in 71..77 -> "Neige"; in 95..99 -> "Orage"; else -> ""
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } } }
}

@Composable fun App() {
    val sp = LocalContext.current.getSharedPreferences("p", 0)
    var apps by remember { mutableStateOf(load(sp)) }
    var settings by remember { mutableStateOf(false) }
    val pager = rememberPagerState(1) { 2 } // page 0 = RSS (balayer vers la droite), page 1 = accueil
    BackHandler { }
    if (settings) Settings(sp, apps, { apps = it; save(sp, it) }) { settings = false }
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            if (page == 0) Rss(sp) else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("⚙", color = Color.Gray, fontSize = 22.sp, modifier = Modifier.align(Alignment.End).clickable { settings = true }.padding(12.dp))
                Weather(sp); Media(sp)
                if (apps.isEmpty()) Text("Touche ⚙ pour choisir tes applis", color = Color.Gray, modifier = Modifier.padding(16.dp))
                apps.forEach { AppRow(it.first, it.second) }
            }
        }
    }
}

@Composable fun AppRow(pkg: String, ci: Int) {
    val ctx = LocalContext.current; val pm = ctx.packageManager
    val label = remember(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    val icon = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    val n = NotifService.counts[pkg] ?: 0; val col = PAL[ci % PAL.size]
    Row(Modifier.fillMaxWidth().height(60.dp).clickable { pm.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) } },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(col))
        Spacer(Modifier.width(12.dp))
        icon?.let { Image(it, null, Modifier.size(36.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (n > 0) Box(Modifier.padding(end = 16.dp).size(28.dp).clip(CircleShape).background(col), Alignment.Center) {
            Text("$n", color = Color.Black, fontSize = 13.sp)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222222)))
}

@Composable fun Weather(sp: SharedPreferences) {
    val lat = sp.getString("lat", "48.85")!!; val lon = sp.getString("lon", "2.35")!!
    val w by produceState("…", lat, lon) {
        while (true) {
            value = runCatching {
                val c = org.json.JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code")).getJSONObject("current")
                "${c.getDouble("temperature_2m").toInt()}°  ${wmo(c.getInt("weather_code"))}"
            }.getOrDefault("Météo indisponible")
            delay(900_000)
        }
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF1B2733)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now), color = Color.LightGray, fontSize = 13.sp)
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 48.sp)
        }
        Text(w, color = Color.White, fontSize = 20.sp)
    }
}

@Composable fun Btn(s: String, f: () -> Unit) = Text(s, fontSize = 22.sp, color = Color.White, modifier = Modifier.clickable(onClick = f).padding(8.dp))

@Composable fun Media(sp: SharedPreferences) {
    var title by remember { mutableStateOf("") }; var play by remember { mutableStateOf(false) }
    var radio by remember { mutableStateOf<MediaPlayer?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val c = NotifService.ctrl
            title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            play = c?.playbackState?.state == PlaybackState.STATE_PLAYING
            delay(1000)
        }
    }
    val t = NotifService.ctrl?.transportControls
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF161616)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (radio != null) "📻 Radio" else title.ifEmpty { "Aucune musique" }, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Btn("⏮") { t?.skipToPrevious() }
        Btn(if (play) "⏸" else "▶") { if (play) t?.pause() else t?.play() }
        Btn("⏭") { t?.skipToNext() }
        Btn(if (radio != null) "⏹" else "📻") {
            if (radio != null) { radio?.release(); radio = null } else radio = runCatching {
                MediaPlayer().apply { setDataSource(sp.getString("radio", DEF_RADIO)); setOnPreparedListener { it.start() }; prepareAsync() }
            }.getOrNull()
        }
    }
}

@Composable fun Rss(sp: SharedPreferences) {
    val url = sp.getString("feed", DEF_FEED)!!; val ctx = LocalContext.current
    val list by produceState(listOf<Pair<String, String>>(), url) {
        while (true) { value = runCatching { parse(get(url)) }.getOrDefault(emptyList()); delay(600_000) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("FLUX RSS", color = PAL[3], fontSize = 13.sp) }
        items(list) { (t, l) ->
            Text(t, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(vertical = 10.dp)
                .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l))) })
        }
    }
}

fun parse(x: String): List<Pair<String, String>> {
    val p = Xml.newPullParser(); p.setInput(x.reader())
    val out = mutableListOf<Pair<String, String>>()
    var inItem = false; var t = ""; var l = ""; var tag = ""
    var e = p.eventType
    while (e != XmlPullParser.END_DOCUMENT) {
        when (e) {
            XmlPullParser.START_TAG -> { tag = p.name; if (tag == "item") { inItem = true; t = ""; l = "" } }
            XmlPullParser.TEXT -> if (inItem) { if (tag == "title") t += p.text else if (tag == "link") l += p.text }
            XmlPullParser.END_TAG -> { if (p.name == "item") { inItem = false; out += t.trim() to l.trim() }; tag = "" }
        }
        e = p.next()
    }
    return out.take(40)
}

@Composable fun Settings(sp: SharedPreferences, apps: List<Pair<String, Int>>, onApps: (List<Pair<String, Int>>) -> Unit, close: () -> Unit) {
    val ctx = LocalContext.current; val pm = ctx.packageManager
    val all = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinct().sortedBy { it.second.lowercase() }
    }
    @Composable fun Field(key: String, label: String, def: String) {
        var v by remember { mutableStateOf(sp.getString(key, def)!!) }
        OutlinedTextField(v, { v = it; sp.edit().putString(key, it).apply() }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(close) { Text("OK") } },
        title = { Text("Réglages") },
        text = {
            LazyColumn(Modifier.height(480.dp)) {
                item {
                    Field("feed", "URL du flux RSS", DEF_FEED); Field("radio", "URL de la radio (flux mp3)", DEF_RADIO)
                    Field("lat", "Latitude météo", "48.85"); Field("lon", "Longitude météo", "2.35")
                    TextButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Activer l'accès aux notifications") }
                    Text("Applis (touche le rond pour changer la couleur)", fontSize = 12.sp)
                }
                items(all) { (pkg, label) ->
                    val cur = apps.find { it.first == pkg }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(cur != null, { on -> onApps(if (on) apps + (pkg to apps.size % PAL.size) else apps.filter { it.first != pkg }) })
                        Text(label, Modifier.weight(1f))
                        if (cur != null) Box(Modifier.size(26.dp).clip(CircleShape).background(PAL[cur.second % PAL.size])
                            .clickable { onApps(apps.map { if (it.first == pkg) pkg to (it.second + 1) % PAL.size else it }) })
                    }
                }
            }
        })
}
