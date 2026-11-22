@file:OptIn(ExperimentalFoundationApi::class)

package com.vapor.player

import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.basicMarquee
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

object V {
    val Bg = Color(0xFF0B0221)
    val Pink = Color(0xFFFF71CE)
    val Cyan = Color(0xFF01CDFE)
    val Green = Color(0xFF05FFA1)
    val Purple = Color(0xFFB967FF)
    val Yellow = Color(0xFFFFFB96)
    val Panel = Color(0xCC14072E)
}

private val Mono = FontFamily.Monospace

@Composable
fun VaporTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = V.Pink, secondary = V.Cyan, background = V.Bg, surface = V.Bg),
        content = content
    )
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        VaporBackground()
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ＡＣＣＥＳＳ　ＤＥＮＩＥＤ", color = V.Pink, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text("Нужен доступ к музыке на устройстве", color = V.Cyan, fontFamily = Mono, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            RetroButton("ＡＬＬＯＷ", active = true, onClick = onGrant)
        }
    }
}

// ============================ ГЛАВНЫЙ ЭКРАН ============================

@Composable
fun VaporScreen(vm: PlayerViewModel) {
    var query by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    val list = remember(vm.tracks, query, favOnly, vm.favorites) {
        vm.tracks.filter {
            (!favOnly || it.id in vm.favorites) &&
                (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true))
        }
    }

    Box(Modifier.fillMaxSize()) {
        VaporBackground()
        LazyColumn(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ＶＡＰＯＲ　ＰＬＡＹＥＲ", color = V.Pink, fontFamily = Mono, fontWeight = FontWeight.Black, fontSize = 24.sp)
                    Text("ヴェイパー　プレイヤー　'９５", color = V.Cyan, fontFamily = Mono, fontSize = 13.sp)
                }
            }
            item { PlayerWindow(vm) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchBox(query, { query = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    RetroButton("♥", active = favOnly, onClick = { favOnly = !favOnly })
                }
            }
            item {
                Text("${list.size} ＴＲＡＣＫＳ".fw(), color = V.Purple, fontFamily = Mono, fontSize = 12.sp)
            }
            items(list, key = { it.id }) { t ->
                TrackRow(
                    t, current = t.id == vm.currentId, playing = vm.isPlaying,
                    fav = t.id in vm.favorites,
                    onClick = { vm.play(t) }, onFav = { vm.toggleFav(t.id) }
                )
            }
            if (vm.tracks.isEmpty()) item {
                Text("ＮＯ　ＭＵＳＩＣ　ＦＯＵＮＤ", color = V.Pink, fontFamily = Mono, modifier = Modifier.padding(24.dp))
            }
        }
        Scanlines()
    }
}

@Composable
fun PlayerWindow(vm: PlayerViewModel) {
    val track = vm.tracks.firstOrNull { it.id == vm.currentId }
    RetroWindow("ＮＯＷ　ＰＬＡＹＩＮＧ.ｅｘｅ") {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AlbumArt(track, Modifier.size(210.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                track?.title ?: "ＮＯ　ＴＲＡＣＫ", color = V.Cyan, fontFamily = Mono,
                fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Text(track?.artist ?: "― ― ―", color = V.Pink, fontFamily = Mono, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Visualizer(vm.isPlaying)

            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = max(vm.duration, 1L)
            Slider(
                value = drag ?: (vm.position.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { vm.seek((it * dur).toLong()) }; drag = null },
                colors = SliderDefaults.colors(
                    thumbColor = V.Cyan, activeTrackColor = V.Pink,
                    inactiveTrackColor = V.Purple.copy(alpha = 0.4f)
                )
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(vm.position.fmt(), color = V.Green, fontFamily = Mono, fontSize = 12.sp)
                Text(vm.duration.fmt(), color = V.Green, fontFamily = Mono, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RetroButton("⤮", active = vm.shuffle, onClick = vm::toggleShuffle, modifier = Modifier.weight(1f))
                RetroButton("◀◀", onClick = vm::prev, modifier = Modifier.weight(1f))
                RetroButton(if (vm.isPlaying) "❚❚" else "▶", active = true, onClick = vm::togglePlay, modifier = Modifier.weight(1.3f))
                RetroButton("▶▶", onClick = vm::next, modifier = Modifier.weight(1f))
                RetroButton(
                    when (vm.repeatMode) { Player.REPEAT_MODE_ALL -> "↻A"; Player.REPEAT_MODE_ONE -> "↻1"; else -> "↻" },
                    active = vm.repeatMode != Player.REPEAT_MODE_OFF, onClick = vm::cycleRepeat,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RetroButton("ＳＬＯＷ　０.８ｘ", active = vm.vapor, onClick = vm::toggleVapor, modifier = Modifier.weight(2f))
                RetroButton(
                    if (vm.sleepMinutes == 0) "ＺＺＺ" else "ＺＺＺ ${vm.sleepMinutes}ｍ",
                    active = vm.sleepMinutes > 0, onClick = vm::cycleSleep, modifier = Modifier.weight(1.4f)
                )
                RetroButton(
                    if (track != null && track.id in vm.favorites) "♥" else "♡",
                    active = track != null && track.id in vm.favorites,
                    onClick = { track?.let { vm.toggleFav(it.id) } }, modifier = Modifier.weight(0.7f)
                )
            }
        }
    }
}

// ============================ КОМПОНЕНТЫ ============================

@Composable
fun RetroWindow(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(2.dp, V.Cyan).background(V.Panel)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(V.Purple, V.Pink)))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("_ ▢ ✕", color = Color.White, fontFamily = Mono, fontSize = 12.sp)
        }
        content()
    }
}

@Composable
fun RetroButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false
) {
    val c = if (active) V.Green else V.Pink
    Box(
        modifier
            .border(2.dp, c)
            .background(c.copy(alpha = if (active) 0.2f else 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = c, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.border(2.dp, V.Cyan).background(Color(0x6614072E)).padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) Text("ＳＥＡＲＣＨ_", color = V.Cyan.copy(alpha = 0.5f), fontFamily = Mono, fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = V.Cyan, fontFamily = Mono, fontSize = 14.sp),
            cursorBrush = SolidColor(V.Pink), modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TrackRow(
    t: Track, current: Boolean, playing: Boolean, fav: Boolean,
    onClick: () -> Unit, onFav: () -> Unit
) {
    val c = if (current) V.Green else V.Cyan
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, c.copy(alpha = if (current) 1f else 0.35f))
            .background(if (current) V.Green.copy(alpha = 0.12f) else V.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (current && playing) "▶" else if (current) "❚❚" else "♪", color = c, fontFamily = Mono, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = c, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, color = V.Pink, fontFamily = Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(t.durationMs.fmt(), color = V.Purple, fontFamily = Mono, fontSize = 12.sp)
        Text(
            if (fav) "♥" else "♡", color = V.Pink, fontSize = 20.sp,
            modifier = Modifier.clickable(onClick = onFav).padding(start = 10.dp)
        )
    }
}

@Composable
fun AlbumArt(track: Track?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, track?.id) {
        value = if (track == null || Build.VERSION.SDK_INT < 29) null
        else withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(track.uri, Size(512, 512), null) }.getOrNull()
        }
    }
    Box(modifier.border(2.dp, V.Pink), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(V.Pink.copy(0.3f), V.Cyan.copy(0.3f)))))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF2B0A57), V.Pink)))
                drawCircle(
                    Brush.verticalGradient(listOf(V.Yellow, V.Pink), startY = size.height * 0.2f, endY = size.height * 0.7f),
                    radius = size.width * 0.3f, center = Offset(size.width / 2, size.height * 0.45f)
                )
                drawRect(Color(0xFF12032B), Offset(0f, size.height * 0.65f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.35f))
                for (i in 0..5) {
                    val y = size.height * 0.65f + size.height * 0.35f * (i / 6f) * (i / 6f) + 4f
                    drawLine(V.Cyan.copy(0.6f), Offset(0f, y), Offset(size.width, y), 2f)
                }
            }
            Text("🗿", fontSize = 64.sp)
        }
    }
}

@Composable
fun Visualizer(playing: Boolean) {
    val t by rememberInfiniteTransition(label = "viz").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val n = 28
        val w = size.width / n
        for (i in 0 until n) {
            val phase = t * 2f * PI.toFloat() * (1 + i % 3) + i * 0.7f
            val h = if (playing) 0.15f + 0.85f * abs(sin(phase)) else 0.06f
            val bh = size.height * h
            drawRect(
                lerp(V.Cyan, V.Pink, i / n.toFloat()),
                Offset(i * w + 2f, size.height - bh),
                androidx.compose.ui.geometry.Size(w - 4f, bh)
            )
        }
    }
}

// ============================ ФОН И ЭФФЕКТЫ ============================

@Composable
fun VaporBackground() {
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart), label = "grid"
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.6f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0221), Color(0xFF2B0A57), Color(0xFFB0307A)), 0f, horizon))
        val r = w * 0.3f
        drawCircle(
            Brush.verticalGradient(listOf(V.Yellow, V.Pink), startY = horizon - r * 2, endY = horizon),
            r, Offset(w / 2, horizon - r * 0.3f)
        )
        drawRect(Color(0xFF12032B), Offset(0f, horizon), androidx.compose.ui.geometry.Size(w, h - horizon))
        // горизонтальные линии, бегущие к зрителю
        for (k in 0..9) {
            val p = (k + t) / 10f
            val y = horizon + (h - horizon) * p * p
            drawLine(V.Purple.copy(alpha = 0.6f), Offset(0f, y), Offset(w, y), 2f)
        }
        // перспективные вертикали
        for (i in -12..12) {
            drawLine(
                V.Cyan.copy(alpha = 0.35f),
                Offset(w / 2 + i * w * 0.02f, horizon), Offset(w / 2 + i * w * 0.22f, h), 2f
            )
        }
    }
}

@Composable
fun Scanlines() {
    Canvas(Modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 1f)
            y += 4f
        }
    }
}
