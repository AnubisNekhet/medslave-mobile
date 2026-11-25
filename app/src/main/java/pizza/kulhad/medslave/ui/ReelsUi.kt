package pizza.kulhad.medslave.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pizza.kulhad.medslave.Rs
import kotlin.math.roundToInt

private val DARKINK = Color(0xFF1E1E2E)

fun kfmt(n: Int): String = when {
    n >= 1_000_000 -> "%.1f".format(n / 1e6).removeSuffix(".0") + "M"
    n >= 10_000 -> "${(n / 1e3).roundToInt()}K"
    n >= 1_000 -> "%.1f".format(n / 1e3).removeSuffix(".0") + "K"
    else -> "$n"
}

/** text over the card keeps a soft glow in the card colour, like the site's drop-shadow */
@Composable private fun glowStyle() = Shadow(P.card, Offset.Zero, 8f)

@Composable
private fun ActLabel(t: String, color: Color = P.text) =
    androidx.compose.foundation.text.BasicText(t, style = TextStyle(color = color, fontSize = 12.sp, fontFamily = Carlito, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, shadow = glowStyle()))

/* the overlay: bottom left the account and song, the right column like / comments / Repost / share / music */
@Composable
fun ReelOverlay(rs: Rs) {
    val r = rs.reels; val hap = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.BottomCenter).widthIn(max = 714.dp).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 14.dp)
            .graphicsLayer { translationY = rs.oy.value }, verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f).onGloballyPositioned { val b = it.boundsInWindow(); r.whoTop = b.top - rs.stageRect.top - rs.oy.value; r.setExcl(0, b) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(37.dp).clip(CircleShape).background(r.color), contentAlignment = Alignment.Center) { Icon(r.icon, 20.dp, DARKINK) }
                Column(Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * .6f).dp)) {
                    androidx.compose.foundation.text.BasicText(r.name, style = TextStyle(color = P.text, fontSize = 15.sp, fontFamily = Carlito, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, shadow = glowStyle()), maxLines = 1)
                    r.song?.let { s -> androidx.compose.foundation.text.BasicText("♪ ${s.title} · ${s.artist}", style = TextStyle(color = P.text.copy(alpha = .85f), fontSize = 11.5.sp, fontFamily = Carlito, shadow = glowStyle()), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                }
            }
            Column(Modifier.onGloballyPositioned { r.setExcl(1, it.boundsInWindow()) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                /* like: a pop when it turns on */
                val pop = remember { Animatable(1f) }
                LaunchedEffect(r.likePop) { if (r.likePop > 0) pop.animateTo(1f, keyframes { durationMillis = 350; .6f at 0; 1.25f at 210; 1f at 350 }) }
                Act(onClick = { r.like(!r.liked) }) {
                    Icon(Ic.HEART, 34.dp, if (r.liked) HEART else P.text, fill = r.liked, modifier = Modifier.scale(pop.value))
                    ActLabel(kfmt(r.likes + if (r.liked) 1 else 0))
                }
                Act(onClick = { r.open("com") }) { Icon(Ic.COMMENT, 34.dp, P.text); ActLabel(kfmt(r.coms)) }
                /* Repost = missed it: red, the arrows turn half a turn, a cross pops in, then the card is requeued and scrolls up */
                val turn = remember { Animatable(0f) }; val x = remember { Animatable(0f) }
                LaunchedEffect(r.repostAt) {
                    if (r.repostAt == 0) return@LaunchedEffect
                    launch { turn.snapTo(0f); turn.animateTo(180f, tween(420, easing = CubicBezierEasing(.4f, 0f, .2f, 1f))) }
                    x.snapTo(0f); kotlinx.coroutines.delay(140); x.animateTo(1f, tween(260, easing = CubicBezierEasing(.3f, 1.7f, .5f, 1f)))
                }
                LaunchedEffect(rs.cur) { if (!r.reposting) { turn.snapTo(0f); x.snapTo(0f) } }
                val red = r.reposting
                Act(onClick = {
                    if (rs.cur != null && !rs.leaving && !r.reposting) { r.reposting = true; r.repostAt++; rs.repost { r.reposting = false } }
                }) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        Icon(Ic.REPOST, 34.dp, if (red) HEART else P.text, modifier = Modifier.rotate(if (red) turn.value else 0f))
                        if (red) Icon(Ic.CROSS, 34.dp, HEART, 2.6f, modifier = Modifier.scale(x.value))
                    }
                    ActLabel("Repost")
                }
                Act(onClick = { r.open("share") }) { Icon(Ic.SEND, 34.dp, P.text); ActLabel("Share") }
                /* music: a tap mutes, a long press opens the genre switches */
                var art by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(r.song) { art = null; r.song?.let { s -> r.art(s.art) { if (r.song === s) art = it } } }
                Box(Modifier.padding(top = 2.dp).size(26.dp).border(1.5.dp, P.text, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp))
                    .pointerInputTap({ r.tapMusic() }, { hap.performHapticFeedback(HapticFeedbackType.LongPress); r.open("music") }), contentAlignment = Alignment.Center) {
                    art?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = if (r.muted) .25f else .55f }, contentScale = ContentScale.Crop) }
                    Icon(Ic.NOTE + if (r.muted) Ic.SLASH else "", 14.dp, P.text)
                }
            }
        }
    }
}

private fun Modifier.pointerInputTap(tap: () -> Unit, long: () -> Unit) = pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { long() }) }

@Composable
private fun Act(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

/** the double-tap heart: bursts where you tapped, then floats up and fades */
@Composable
fun Bursts(rs: Rs) {
    val d = LocalDensity.current.density
    rs.reels.bursts.toList().forEach { b ->
        key(b.id) {
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) { t.animateTo(1f, tween(850, easing = LinearEasing)); rs.reels.bursts.remove(b) }
            val p = t.value
            val sc = when { p < .15f -> p / .15f * 1.18f; p < .30f -> 1.18f - (p - .15f) / .15f * .24f; p < .42f -> .94f + (p - .30f) / .12f * .06f; p < .68f -> 1f; else -> 1f - (p - .68f) / .32f * .1f }
            val a = when { p < .15f -> p / .15f; p < .68f -> 1f; else -> 1f - (p - .68f) / .32f }
            val up = if (p > .68f) -(p - .68f) / .32f * 48f * d else 0f
            Icon(Ic.HEART, 104.dp, HEART, fill = true, modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((b.x - 52 * d).roundToInt(), (b.y - 52 * d + up).roundToInt()) }
                .graphicsLayer { scaleX = sc; scaleY = sc; alpha = a; rotationZ = b.r })
        }
    }
}

/* ---------------------------------------------------------------- bottom sheets: comments, share, music genres */
@Composable
fun Sheets(rs: Rs) {
    val r = rs.reels; val kind = r.sheet
    val shown by animateFloatAsState(if (kind != null) 1f else 0f, tween(300, easing = CubicBezierEasing(.2f, .8f, .3f, 1f)), label = "sheet")
    var last by remember { mutableStateOf("com") }
    if (kind != null) last = kind
    if (shown == 0f && kind == null) return
    val k = kind ?: last
    val d = LocalDensity.current.density; val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    LaunchedEffect(kind) { if (kind != null) drag.snapTo(0f) }
    var hPx by remember { mutableStateOf(1) }
    fun release(v: Float) { scope.launch { if (drag.value > 110 * d || v > 500 * d) r.close() else drag.animateTo(0f, tween(250)) } }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drag.value > 0 && available.y < 0) { val c = maxOf(available.y, -drag.value); scope.launch { drag.snapTo(drag.value + c) }; return Offset(0f, c) }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) { scope.launch { drag.snapTo(drag.value + available.y) }; return Offset(0f, available.y) }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity { if (drag.value > 0) { release(available.y); return available }; return Velocity.Zero }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f * shown)).then(if (kind != null) Modifier.clickable(remember { MutableInteractionSource() }, null) { r.close() } else Modifier)) {
        val tall = k == "com"
        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 544.dp).fillMaxWidth().then(if (tall) Modifier.fillMaxHeight(.72f) else Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .72f).dp))
            .onSizeChanged { hPx = it.height }
            .graphicsLayer { translationY = (1 - shown) * hPx + drag.value }
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(P.card)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .nestedScroll(nested).windowInsetsPadding(WindowInsets.navigationBars).imePadding()) {
            Column(Modifier.fillMaxWidth().solidLine(P.divider, false)
                .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { release(0f) }, onDragCancel = { release(0f) }) { _, dy -> scope.launch { drag.snapTo(maxOf(0f, drag.value + dy)) } } }
                .padding(top = 9.dp, bottom = 11.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(42.dp, 4.5.dp).clip(RoundedCornerShape(4.dp)).background(P.faint.copy(alpha = .5f)))
                Txt(when (k) { "share" -> "Share"; "music" -> "Music"; else -> "Comments" }, size = 15.sp, bold = true)
            }
            when (k) { "share" -> ShareSheet(rs); "music" -> MusicSheet(rs); else -> CommentSheet(rs, Modifier.weight(1f)) }
        }
    }
    if (r.qr) QrPopup(rs)
}

@Composable
private fun CommentSheet(rs: Rs, modifier: Modifier) {
    val r = rs.reels; val st = rememberLazyListState()
    if (r.loading) Box(modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.TopCenter) { Spinner(32.dp) }
    else LazyColumn(modifier.fillMaxWidth(), st, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        items(r.list) { c ->
            val a = remember { Animatable(0f) }; LaunchedEffect(Unit) { a.animateTo(1f, tween(250)) }
            Row(Modifier.fillMaxWidth().graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 6 * density }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(c.color), contentAlignment = Alignment.Center) { Txt(c.user.take(1).uppercase(), size = 13.6.sp, bold = true, color = DARKINK) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) { Txt(c.user, size = 12.8.sp, bold = true); Txt(c.time, size = 11.5.sp, color = P.faint) }
                    Txt(c.text, size = 13.6.sp, lh = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
                    Txt("Reply", size = 11.5.sp, bold = true, color = P.faint)
                }
                Column(Modifier.clickable(remember { MutableInteractionSource() }, null) { c.liked = !c.liked }.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.HEART, 16.dp, if (c.liked) HEART else P.faint, fill = c.liked)
                    val n = c.likes + if (c.liked) 1 else 0
                    Txt(if (n > 0) kfmt(n) else "", size = 10.9.sp, color = if (c.liked) HEART else P.faint)
                }
            }
        }
    }
    var text by remember { mutableStateOf("") }
    val post = { val x = text.trim(); if (x.isNotEmpty()) { text = ""; r.post(x); rs.scope.launch { st.scrollToItem(0) } } }
    Row(Modifier.fillMaxWidth().solidLine(P.divider, true).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(text, { text = it }, "Add a comment for ${r.name}...", Modifier.weight(1f), ImeAction.Send) { post() }
        Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { post() }.padding(6.dp)) { Txt("Post", bold = true, color = P.link) }
    }
}

/** the round search / comment input */
@Composable
fun Field(v: String, on: (String) -> Unit, hint: String, modifier: Modifier = Modifier, ime: ImeAction = ImeAction.Done, go: () -> Unit = {}) {
    BasicTextField(v, on, modifier.clip(RoundedCornerShape(30.dp)).background(P.text.copy(alpha = .07f)).padding(horizontal = 16.dp, vertical = 10.dp),
        textStyle = TextStyle(color = P.text, fontSize = 14.4.sp, fontFamily = Carlito), singleLine = true, cursorBrush = SolidColor(P.text),
        keyboardOptions = KeyboardOptions(imeAction = ime), keyboardActions = KeyboardActions { go() },
        decorationBox = { inner -> Box { if (v.isEmpty()) Txt(hint, size = 14.4.sp, color = P.faint, lines = 1); inner() } })
}

@Composable
private fun ShareSheet(rs: Rs) {
    val r = rs.reels; var q by remember { mutableStateOf("") }; val sent = remember { mutableStateMapOf<String, Boolean>() }
    val cols = remember { r.share.map { DCOL.random().second } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Field(q, { q = it }, "Search", Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 10.dp))
        r.share.forEachIndexed { i, (n, h, ic) ->
            if (q.isNotEmpty() && !(n + h).lowercase().contains(q.lowercase())) return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(cols[i]), contentAlignment = Alignment.Center) {
                    if (ic.isNotEmpty()) Icon(ic, 21.dp, DARKINK) else Txt(n.take(1).uppercase(), bold = true, color = DARKINK)
                }
                Column(Modifier.weight(1f)) { Txt(n, size = 14.4.sp); if (h.isNotEmpty()) Txt(h, size = 12.sp, color = P.faint) }
                val done = sent[n] == true
                Box(Modifier.height(35.dp).clip(RoundedCornerShape(3.dp)).background(if (done) P.text.copy(alpha = .12f) else Color(0xFF4F5BD5))
                    .clickable(enabled = !done) { if (ic.isNotEmpty()) r.qr = true else sent[n] = true }.padding(horizontal = 19.dp), contentAlignment = Alignment.Center) {
                    Txt(if (done) "Sent" else "Send", size = 13.6.sp, color = if (done) P.faint else Color.White)
                }
            }
        }
        Gap(10.dp)
    }
}

@Composable
private fun MusicSheet(rs: Rs) {
    val r = rs.reels; var msg by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Txt("Songs only play from the genres that are on.", size = 14.sp, color = P.faint, modifier = Modifier.padding(vertical = 6.dp))
        r.genres.forEach { (g, label) ->
            val n = r.songs.count { it.genre == g && !it.dead }; if (n == 0) return@forEach
            Row2(label, "$n songs") {
                Switch(g !in rs.set.goff) { on ->
                    val off = if (on) rs.set.goff - g else rs.set.goff + g
                    if (off.size >= r.genres.count { (k, _) -> r.songs.any { it.genre == k && !it.dead } }) { msg = "Keep at least one genre on"; return@Switch }
                    msg = ""; rs.set.goff = off; rs.set.save(); r.genreChanged()
                }
            }
        }
        if (msg.isNotEmpty()) Txt(msg, size = 13.sp, color = RED, modifier = Modifier.padding(bottom = 6.dp))
        Gap(10.dp)
    }
}

/** the QR pop-up over the share sheet: the app's GitHub page, dark on white so it scans in any theme */
@Composable
private fun QrPopup(rs: Rs) {
    val r = rs.reels
    val path = remember { PathParser().parsePathString(r.qrPath).toPath() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).clickable(remember { MutableInteractionSource() }, null) { r.qr = false }, contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(P.card).clickable(remember { MutableInteractionSource() }, null) {}) {
            Row(Modifier.dashedBottom(P.divider).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Scan to get medslave", bold = true, modifier = Modifier.padding(end = 24.dp))
                Box(Modifier.size(36.dp).clickable { r.qr = false }, contentAlignment = Alignment.Center) { Txt("×", size = 22.sp, color = P.faint) }
            }
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 19.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(216.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
                    Canvas(Modifier.fillMaxSize()) { val k = size.width / r.qrSize; scale(k, k, Offset.Zero) { drawPath(path, Color.Black, style = Stroke(1f, cap = StrokeCap.Butt)) } }
                }
                Txt(r.qrUrl.removePrefix("https://"), size = 12.8.sp, color = P.faint)
            }
        }
    }
}

/** the logo spinning: the loading mark */
@Composable
fun Spinner(size: androidx.compose.ui.unit.Dp) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) { t.snapTo(0f); t.animateTo(360f, tween(1000, easing = LinearEasing)) } }
    Logo(size, Modifier.rotate(t.value))
}
