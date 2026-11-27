package pizza.kulhad.medslave.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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

/* the overlay: bottom left the account and song, the right column like / comments / Repost / share / save / music (only the counts have labels) */
@Composable
fun ReelOverlay(rs: Rs) {
    val r = rs.reels; val hap = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.BottomCenter).widthIn(max = 714.dp).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 14.dp)
            .graphicsLayer { translationY = rs.oy.value }, verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f).onGloballyPositioned { val b = it.boundsInWindow(); r.whoTop = b.top - rs.stageRect.top - rs.oy.value; r.setExcl(0, b) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val openAcc = { rs.cur?.let { c -> rs.feed.openFromReels(c.n, c.o) }; Unit }   // the account's profile in the feed
                Box(Modifier.size(37.dp).clip(CircleShape).background(r.color).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { openAcc() }, contentAlignment = Alignment.Center) { if (rs.set.avatar != "solid") Waves(r.color); Icon(r.icon, 20.dp, DARKINK) }
                Column(Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * .6f).dp)) {
                    androidx.compose.foundation.text.BasicText(r.name, style = TextStyle(color = P.text, fontSize = 15.sp, fontFamily = Carlito, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, shadow = glowStyle()), maxLines = 1,
                        modifier = Modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { openAcc() })
                    r.song?.let { s -> androidx.compose.foundation.text.BasicText("♪ ${s.title} · ${s.artist}", style = TextStyle(color = P.text.copy(alpha = .85f), fontSize = 11.5.sp, fontFamily = Carlito, shadow = glowStyle()), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { r.open("music") }) }   // the song opens the music sheet
                }
            }
            Column(Modifier.onGloballyPositioned { r.setExcl(1, it.boundsInWindow()) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(15.dp)) {
                /* the feed's button animations (feed.css). like: the heart pops and a ring bursts out of it; unlike: a small squash */
                val scope = rememberCoroutineScope()
                val pop = remember { Animatable(1f) }; val ring = remember { Animatable(1f) }; var unlikes by remember { mutableIntStateOf(0) }
                LaunchedEffect(r.likePop) { if (r.likePop > 0) {
                    launch { ring.snapTo(0f); ring.animateTo(1f, tween(450, easing = CubicBezierEasing(0f, 0f, .58f, 1f))) }
                    pop.animateTo(1f, keyframes { durationMillis = 350; .6f at 0; 1.25f at 210; 1f at 350 }) } }
                LaunchedEffect(unlikes) { if (unlikes > 0) pop.animateTo(1f, keyframes { durationMillis = 250; 1f at 0; .8f at 100; 1f at 250 }) }
                Act(onClick = { val v = !r.liked; r.like(v); if (!v) unlikes++ }) {
                    Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                        if (ring.value < 1f) { val t = ring.value; Box(Modifier.size(25.dp).graphicsLayer { scaleX = .4f + 1.3f * t; scaleY = scaleX; alpha = 1f - t }.border((2f * (1f - t)).dp, likeC(rs), CircleShape)) }
                        Icon(Ic.HEART, 25.dp, if (r.liked) likeC(rs) else P.text, fill = r.liked, modifier = Modifier.scale(pop.value))
                    }
                    ActLabel(kfmt(r.likes + if (r.liked) 1 else 0))
                }
                /* comments: a wiggle, then the sheet */
                val wig = remember { Animatable(0f) }
                if (!rs.set.nocom) Act(onClick = { scope.launch { wig.snapTo(0f); wig.animateTo(1f, tween(450, easing = CubicBezierEasing(.42f, 0f, .58f, 1f))) }
                    scope.launch { if (!rs.rm()) kotlinx.coroutines.delay(160); r.open("com") } }) {
                    val t = wig.value
                    val rot = when { t <= 0f || t >= 1f -> 0f; t < .2f -> -14f * t / .2f; t < .45f -> -14f + 25f * (t - .2f) / .25f; t < .7f -> 11f - 17f * (t - .45f) / .25f; else -> -6f + 6f * (t - .7f) / .3f }
                    val sc = when { t <= 0f || t >= 1f -> 1f; t < .2f -> 1f + .1f * t / .2f; t < .45f -> 1.1f; t < .7f -> 1.1f - .1f * (t - .45f) / .25f; else -> 1f }
                    Icon(Ic.COMMENT, 25.dp, P.text, modifier = Modifier.graphicsLayer { rotationZ = rot; scaleX = sc; scaleY = sc }); ActLabel(kfmt(r.coms)) }
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
                    Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) {
                        Icon(Ic.REPOST, 25.dp, if (red) HEART else P.text, modifier = Modifier.rotate(if (red) turn.value else 0f))
                        if (red) Icon(Ic.CROSS, 25.dp, HEART, 2.6f, modifier = Modifier.scale(x.value))
                    }
                }
                /* share: the plane flies off top right and comes back in from bottom left, then the sheet */
                val fly = remember { Animatable(1f) }
                Act(onClick = { scope.launch { fly.snapTo(0f); fly.animateTo(1f, tween(600, easing = CubicBezierEasing(.4f, 0f, .2f, 1f))) }
                    scope.launch { if (!rs.rm()) kotlinx.coroutines.delay(260); r.open("share") } }) {
                    val t = fly.value
                    val (dx, a, sc) = if (t < .4f) Triple(16f * t / .4f, 1f - t / .4f, 1f - .3f * t / .4f) else if (t < .41f) Triple(-12f, 0f, .7f) else { val u = (t - .41f) / .59f; Triple(-12f * (1 - u), u, .7f + .3f * u) }
                    val dpx = with(androidx.compose.ui.platform.LocalDensity.current) { dx.dp.toPx() }
                    Icon(Ic.SEND, 25.dp, P.text, modifier = Modifier.graphicsLayer { translationX = dpx; translationY = -dpx; alpha = a; scaleX = sc; scaleY = sc })
                }
                /* Save: the card goes into saved.cards (the feed's archive); the bookmark drops in on every tap */
                val drop = remember { Animatable(1f) }
                LaunchedEffect(r.savePop) { if (r.savePop > 0) { drop.snapTo(0f); drop.animateTo(1f, tween(400, easing = CubicBezierEasing(.3f, 1.6f, .5f, 1f))) } }
                Act(onClick = {
                    rs.cur?.let { c -> r.saved = !r.saved; r.savePop++; rs.feed.saveFromReels(c.n.id, c.o, r.saved) }
                }) {
                    val t = drop.value; val dy = with(androidx.compose.ui.platform.LocalDensity.current) { (-8f * (1 - t)).dp.toPx() }
                    Icon(Ic.SAVE, 25.dp, P.text, fill = r.saved, modifier = Modifier.graphicsLayer { translationY = dy; scaleX = .85f + .15f * t; scaleY = scaleX })
                }
                /* ⋯: a small menu with Hide (like a feed post's), which keeps the card out of Reels, posts and stories, and Block, which
                   hides every subject the card's account posts */
                var menu by remember { mutableStateOf(false) }
                var shutAt by remember { mutableStateOf(0L) }   // when a touch outside closed it: that touch landing on the dots mustn't reopen it
                LaunchedEffect(rs.cur) { menu = false }
                Box {
                    Act(onClick = { if (menu) menu = false else if (System.currentTimeMillis() - shutAt > 400) menu = true }) { Icon(Ic.DOTS, 25.dp, P.text, 2.6f) }
                    if (menu) androidx.compose.ui.window.Popup(alignment = Alignment.CenterEnd, offset = androidx.compose.ui.unit.IntOffset(with(androidx.compose.ui.platform.LocalDensity.current) { -40.dp.roundToPx() }, 0),
                        onDismissRequest = { menu = false; shutAt = System.currentTimeMillis() }) {
                        val sh = RoundedCornerShape(8.dp)
                        Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).clip(sh).background(P.card).border(1.dp, P.border, sh)) {
                            listOf(Triple(Ic.STOP, "Hide") { rs.hideCur() }, Triple(Ic.BAN, "Block") { rs.blockCur() }).forEach { (ic, t, go) ->
                                Row(Modifier.fillMaxWidth().clickable { menu = false; go() }.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(ic, 20.dp, HEART); Txt(t, size = 14.4.sp, bold = true)
                                }
                            }
                        }
                    }
                }
                /* music: a tap mutes, a long press opens the genre switches */
                var art by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(r.song) { art = null; r.song?.let { s -> r.art(s.art) { if (r.song === s) art = it } } }
                Box(Modifier.padding(top = 2.dp).size(27.dp).border(1.5.dp, P.text, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp))
                    .pointerInputTap({ r.tapMusic() }, { r.buzz(); r.open("music") }), contentAlignment = Alignment.Center) {
                    art?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = if (r.muted) .25f else .55f }, contentScale = ContentScale.Crop) }
                    Icon(Ic.NOTE + if (r.muted) Ic.SLASH else "", 15.dp, P.text)
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
            Icon(Ic.HEART, 104.dp, likeC(rs), fill = true, modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset((b.x - 52 * d).roundToInt(), (b.y - 52 * d + up).roundToInt()) }
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
                    Icon(Ic.HEART, 11.dp, if (c.liked) likeC(rs) else P.faint, fill = c.liked)
                    val n = c.likes + if (c.liked) 1 else 0
                    Txt(if (n > 0) kfmt(n) else "", size = 10.9.sp, color = if (c.liked) likeC(rs) else P.faint)
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
                Box(Modifier.height(35.dp).clip(RoundedCornerShape(8.dp)).background(if (done) P.text.copy(alpha = .12f) else Color(0xFF4F5BD5))
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
    val r = rs.reels; var msg by remember { mutableStateOf("") }; var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<pizza.kulhad.medslave.Reels.Playlist?>(null) }
    val dim = androidx.compose.ui.graphics.lerp(P.card, P.text.copy(alpha = 1f), .45f)   // a switched-off row's icon: a solid mid-tone
    /* every group that has songs (the genres and the user's playlists); at least one stays on */
    val keys = r.genres.map { it.first }.filter { g -> r.songs.any { it.genre == g && !it.dead } } + r.lists.filter { it.songs.isNotEmpty() }.map { it.key }
    fun toggle(k: String, on: Boolean) {
        r.markLists()   // a playlist switched off now was on until now
        val off = if (on) rs.set.goff - k else rs.set.goff + k
        if (keys.all { it in off }) { msg = "Keep at least one genre or playlist on"; return }
        msg = ""; rs.set.goff = off; rs.set.save(); r.genreChanged()
    }
    @Composable fun row(icon: String, label: String, small: String, k: String, enabled: Boolean, long: (() -> Unit)? = null, tap: (() -> Unit)? = null, lead: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
        val on = k !in rs.set.goff
        Row(Modifier.fillMaxWidth().then(if (long != null || tap != null) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap?.invoke() }, onLongPress = { long?.invoke() }) } else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lead != null) lead() else if (k == r.preview) PlayMark(30.dp) else Icon(icon, 22.dp, if (on && enabled) P.text else dim, 2f)
            Row2(label, small, Modifier.weight(1f)) { trailing?.invoke(); if (enabled) Switch(on) { toggle(k, it) } }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Txt("Songs only play from the genres and playlists that are on.", size = 14.sp, color = P.faint, modifier = Modifier.padding(vertical = 6.dp))
        /* your playlists first (a long press edits one), then the genres */
        /* your playlists all at once, like the genres below: only while a genre is on, so something can still play */
        val pk = r.lists.filter { it.songs.isNotEmpty() }.map { it.key }
        val genreOn = r.genres.any { (g, _) -> g !in rs.set.goff && r.songs.any { it.genre == g && !it.dead } }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Your playlists", bold = true, modifier = Modifier.weight(1f))
            if (genreOn && pk.isNotEmpty()) Switch(pk.any { it !in rs.set.goff }) { on ->
                msg = ""; r.markLists(); rs.set.goff = if (on) rs.set.goff - pk.toSet() else rs.set.goff + pk; rs.set.save(); r.genreChanged()
            }
        }
        /* the list stays open with every playlist off (the switch above turns them all on or off) */
        run {
            Column {
                if (r.lists.isNotEmpty()) Txt("Press and hold a playlist to edit it", size = 12.8.sp, color = P.faint)
                r.lists.toList().forEach { p ->
                    val n = p.songs.size
                    /* a tap plays one of its songs (another tap, or closing the sheet, stops it); a long press edits it */
                    row(Ic.NOTE, p.name, p.status.ifEmpty { "$n song${if (n == 1) "" else "s"}" }, p.key, n > 0, long = { r.stopPreview(); r.buzz(); editing = p }, tap = { r.previewList(p) },
                        lead = if (p.covers.isEmpty()) null else ({ Box { PlCover(r, p.covers.map { it.second }, p.key !in rs.set.goff && n > 0); if (r.preview == p.key) PlayMark(30.dp) } })) {   // a long press edits it, with a small buzz
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { r.removePlaylist(p) }, contentAlignment = Alignment.Center) { Txt("×", size = 20.sp, color = P.faint) }
                    }
                }
            }
        }
        Btn("Add a playlist", Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), p = true, h = 38.dp, size = 14.4.sp) { adding = true }
        /* the genres all at once: only while one of your playlists is on, so something can still play */
        val gk = r.genres.map { it.first }.filter { g -> r.songs.any { it.genre == g && !it.dead } }
        val listOn = r.lists.any { it.songs.isNotEmpty() && it.key !in rs.set.goff }
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("Genres", bold = true, modifier = Modifier.weight(1f))
            if (listOn && gk.isNotEmpty()) Switch(gk.any { it !in rs.set.goff }) { on ->
                msg = ""; r.markLists(); rs.set.goff = if (on) rs.set.goff - gk.toSet() else rs.set.goff + gk; rs.set.save(); r.genreChanged()
            }
        }
        /* with every genre off (the switch above), the list folds away; while any genre is on it stays open (Your playlists never folds) */
        androidx.compose.animation.AnimatedVisibility(!(listOn && gk.isNotEmpty()) || gk.any { it !in rs.set.goff },
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) {
            Column {
                r.genres.forEach { (g, label) ->
                    val n = r.songs.count { it.genre == g && !it.dead }; if (n == 0) return@forEach
                    row(GENRE_IC[g] ?: Ic.NOTE, label, "$n songs", g, true)
                }
            }
        }
        if (msg.isNotEmpty()) Txt(msg, size = 13.sp, color = RED, modifier = Modifier.padding(vertical = 6.dp))
        Gap(10.dp)
    }
    if (adding) AddPlaylist(rs, null) { adding = false }
    editing?.let { p -> AddPlaylist(rs, p) { editing = null } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { r.stopPreview() } }   // the sheet closed: the preview stops
}

/** a preview playing: the cover dimmed, a white play triangle on it */
@Composable
private fun PlayMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) {
        Icon(Ic.PLAY, size * .5f, Color.White, 1.5f, fill = true)
    }
}

/** a new playlist: a name, and the songs pasted as lines or a playlist link. editing one: its found songs as pills (× takes one out),
    and the box adds more */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddPlaylist(rs: Rs, edit: pizza.kulhad.medslave.Reels.Playlist?, close: () -> Unit) {
    var name by remember { mutableStateOf(edit?.name ?: "") }; var text by remember { mutableStateOf("") }
    val kept = remember { androidx.compose.runtime.mutableStateListOf<pizza.kulhad.medslave.Song>().apply { edit?.let { addAll(it.songs) } } }
    val acc = pizza.kulhad.medslave.ui.accents(rs.set.theme).map { if (P.dark) it.second else it.first }   // the pills' colours
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { rs.reels.stopPreview() } }   // closing it stops a song playing
    /* wider than a platform dialog: nearly the screen's width, 16dp from each side */
    androidx.compose.ui.window.Dialog(close, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(P.card).border(1.dp, P.border, RoundedCornerShape(8.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt(if (edit != null) "Edit playlist" else "Add a playlist", bold = true)
            Field(name, { name = it }, "Name", Modifier.fillMaxWidth())
            if (kept.isNotEmpty()) Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    kept.toList().forEach { s ->
                        /* the album cover at its true size (a square as tall as the pill) at the left end, under the song's accent (picked by the
                           song, so it stays put) fading in from 5% to full across the cover; the rest of the pill is the full colour. the title and
                           × in the opposite of the text colour (black in dark mode, white in light) */
                        var cov by remember(s.art) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(s.art) { rs.reels.art(s.art) { cov = it } }
                        /* the cover's dominant hue, made pastel for the theme (coverTint); a grey cover, or none yet, keeps an accent picked by the song */
                        val dark = P.dark
                        val fgc = P.text; val bg = remember(cov, dark, fgc) { cov?.let { coverTint(it, dark, fgc) } } ?: if (acc.isEmpty()) P.hi else acc[((s.title + s.artist).hashCode().toUInt() % acc.size.toUInt()).toInt()]
                        /* a tap plays it (from the cache if it's there, otherwise streamed and not saved); another tap stops it */
                        val pk = "song:" + s.title + "|" + s.artist
                        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(30.dp)).background(bg).clickable { rs.reels.previewSong(s, pk) }) {   // full width: one song per row
                            Box(Modifier.size(28.dp).align(Alignment.CenterStart)) {
                                cov?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(bg.copy(alpha = .05f), bg))))
                                if (rs.reels.preview == pk) Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = .5f)), contentAlignment = Alignment.Center) { Icon(Ic.PLAY, 14.dp, Color.White, 1.5f, fill = true) }
                            }
                            Row(Modifier.fillMaxSize().padding(start = 34.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Txt(s.title, size = 13.6.sp, bold = true, color = P.text, lines = 1, modifier = Modifier.weight(1f))
                                Box(Modifier.size(28.dp).clip(CircleShape).clickable { if (rs.reels.preview == pk) rs.reels.stopPreview(); kept.remove(s) }, contentAlignment = Alignment.Center) { Txt("×", size = 17.sp, bold = true, color = P.text) }
                            }
                        }
                    }
                }
            }
            BasicTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 82.dp, max = 340.dp).clip(RoundedCornerShape(8.dp)).background(P.text.copy(alpha = .07f)).padding(12.dp),
                textStyle = TextStyle(color = P.text, fontSize = 14.sp, fontFamily = Carlito), cursorBrush = SolidColor(P.text),
                decorationBox = { inner -> Box { if (text.isEmpty()) Txt(if (edit != null) "Add more songs, one per line (Song – Artist),\nor a playlist link" else "Paste songs, one per line (Song – Artist),\nor a Spotify, Apple Music or YouTube playlist link", size = 14.sp, color = P.faint); inner() } })
            Txt("Each song plays its 30-second preview. Finding them takes a couple of seconds a song.", size = 12.8.sp, color = P.faint)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Cancel", h = 36.dp, size = 14.4.sp, pad = 16.dp, onClick = close)
                Btn(if (edit != null) "Save" else "Add", p = true, h = 36.dp, size = 14.4.sp, pad = 16.dp, enabled = text.isNotBlank() || (edit != null && kept.isNotEmpty())) {
                    if (edit != null) rs.reels.editPlaylist(edit, name, kept.toList(), text) else rs.reels.addPlaylist(name, text); close() }
            }
        }
    }
}

/** the QR pop-up over the share sheet: the latest release's APK (a straight download), dark on white so it scans in any theme */
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
                Txt("Downloads the latest medslave APK", size = 12.8.sp, color = P.faint)
            }
        }
    }
}

/** a like's colour: the accent, or Instagram red (Settings › Reviewing › Red likes) */
@Composable fun likeC(rs: Rs) = if (rs.set.redlike) HEART else P.hi

/** the logo spinning, the loading mark: two quick turns (eased in and out), then a moment upright, and again */
@Composable
fun Spinner(size: androidx.compose.ui.unit.Dp) {
    var ms by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { val t0 = withFrameMillis { it }; while (true) withFrameMillis { ms = it - t0 } }
    val hold = 380f; val spin = 760f; val turn = 720f
    /* it starts with the turn (most loads are short; a pause first would never show it moving), then rests upright, and so on */
    fun angle(t: Float): Float { val c = (t + hold) % (hold + spin); return if (c < hold) 0f else turn * FastOutSlowInEasing.transform((c - hold) / spin) }
    Logo(size, Modifier.rotate(angle(ms.toFloat())))
}

/* the music sheet's genre icons */
private val GENRE_IC = mapOf(
    "trend" to "<polyline points=\"22 7 13.5 15.5 8.5 10.5 2 17\"/><polyline points=\"16 7 22 7 22 13\"/>",
    "pop" to "<path d=\"M12 3l1.9 5.8L20 11l-6.1 2.2L12 19l-1.9-5.8L4 11l6.1-2.2Z\"/>",
    "rap" to "<rect x=\"9\" y=\"2\" width=\"6\" height=\"12\" rx=\"3\"/><path d=\"M19 10v1a7 7 0 0 1-14 0v-1M12 18v4M8 22h8\"/>",
    "edm" to "<path d=\"M13 2 3 14h9l-1 8 10-12h-9l1-8z\"/>",
    "bolly" to "<rect x=\"2\" y=\"3\" width=\"20\" height=\"18\" rx=\"2\"/><path d=\"M7 3v18M17 3v18M2 9h5M2 15h5M17 9h5M17 15h5\"/>",
    "indie" to "<path d=\"M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3\"/>",
    "punjabi" to "<path d=\"m2 2 8 8M22 2l-8 8\"/><ellipse cx=\"12\" cy=\"9\" rx=\"10\" ry=\"5\"/><path d=\"M7 13.4v7.9M12 14v8M17 13.4v7.9M2 9v8a10 5 0 0 0 20 0V9\"/>",
    "lofi" to "<path d=\"M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z\"/>",
    "rock" to "<path d=\"M4.9 19.1C1 15.2 1 8.8 4.9 4.9M7.8 16.2c-2.3-2.3-2.3-6.1 0-8.5M16.2 7.8c2.3 2.3 2.3 6.1 0 8.5M19.1 4.9C23 8.8 23 15.1 19.1 19\"/><circle cx=\"12\" cy=\"12\" r=\"2\"/>")

/** a playlist's cover from its albums' covers: 1 whole; 2 side by side, half of each; 3 and 4 in a 2×2 grid, clockwise from the top
    left (3: 1st, 2nd, 1st, 3rd; 4: each in turn) */
@Composable
private fun PlCover(r: pizza.kulhad.medslave.Reels, urls: List<String>, on: Boolean) {
    val bm = remember(urls) { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(urls) { urls.forEach { u -> r.art(u) { bm[u] = it } } }
    @Composable fun pic(u: String, m: Modifier) = Box(m.background(P.text.copy(alpha = .08f))) { bm[u]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
    val sh = RoundedCornerShape(5.dp)
    Box(Modifier.size(30.dp).clip(sh).border(1.dp, P.border, sh).graphicsLayer { alpha = if (on) 1f else .45f }) {
        when (urls.size) {
            1 -> pic(urls[0], Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize()) { pic(urls[0], Modifier.weight(1f).fillMaxHeight()); pic(urls[1], Modifier.weight(1f).fillMaxHeight()) }
            else -> { val g = if (urls.size == 3) listOf(0, 1, 2, 0) else listOf(0, 1, 3, 2)   // top left, top right, bottom left, bottom right
                Column(Modifier.fillMaxSize()) { for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) { for (c in 0..1) pic(urls[g[row * 2 + c]], Modifier.weight(1f).fillMaxHeight()) } } }
        }
    }
}

/** an account avatar's waves (as the feed's, feed.js avatar): two diagonal waves over its colour, each a further hue step round the wheel */
private fun hueStep(c: Color, dh: Float): Color { val hsl = FloatArray(3); androidx.core.graphics.ColorUtils.colorToHSL(c.toArgb(), hsl); hsl[0] = ((hsl[0] + dh) % 360f + 360f) % 360f; return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl)) }
@Composable
fun androidx.compose.foundation.layout.BoxScope.Waves(c: Color) {
    val c1 = remember(c) { hueStep(c, 22f) }; val c2 = remember(c) { hueStep(c, 44f) }
    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
        val u = size.width / 100f
        fun wave(y: Float) = androidx.compose.ui.graphics.Path().apply {
            moveTo(-40 * u, y * u); cubicTo(-15 * u, (y - 12) * u, 5 * u, (y + 12) * u, 30 * u, y * u)
            cubicTo(55 * u, (y - 12) * u, 75 * u, (y - 12) * u, 100 * u, y * u); cubicTo(125 * u, (y + 12) * u, 145 * u, (y + 12) * u, 170 * u, y * u)
            lineTo(170 * u, 200 * u); lineTo(-40 * u, 200 * u); close() }
        withTransform({ rotate(-28f) }) { drawPath(wave(52f), c1); drawPath(wave(76f), c2) }
    }
}

/** a cover's dominant colour as a pastel: the hue that most of its colourful pixels share (36 hue buckets, each pixel weighted by how vivid
    it is, the best bucket taken with its neighbours), then a fixed saturation and lightness, like the theme's accents: light and soft in
    dark mode (black text on it), a mid tone in light mode (white text on it), then lightened / darkened until the text on it (fg) reads
    at a 4.5:1 contrast at least. null for a cover with too little colour */
fun coverTint(img: ImageBitmap, dark: Boolean, fg: Color): Color? {
    val b = android.graphics.Bitmap.createScaledBitmap(img.asAndroidBitmap(), 24, 24, true)
    val w = FloatArray(36); val cx = FloatArray(36); val cy = FloatArray(36); val hsv = FloatArray(3); var tot = 0f
    for (y in 0 until 24) for (x in 0 until 24) {
        android.graphics.Color.colorToHSV(b.getPixel(x, y), hsv)
        if (hsv[2] < .18f || hsv[1] < .22f) continue
        val wt = hsv[1] * hsv[2]; val i = (hsv[0] / 10f).toInt().coerceIn(0, 35); val rad = Math.toRadians(hsv[0].toDouble())
        w[i] += wt; cx[i] += (kotlin.math.cos(rad) * wt).toFloat(); cy[i] += (kotlin.math.sin(rad) * wt).toFloat(); tot += wt
    }
    if (tot < 24 * 24 * .04f) return null
    val best = (0 until 36).maxBy { w[(it + 35) % 36] + w[it] + w[(it + 1) % 36] }
    val ks = listOf((best + 35) % 36, best, (best + 1) % 36)
    val h = ((Math.toDegrees(kotlin.math.atan2(ks.sumOf { cy[it].toDouble() }, ks.sumOf { cx[it].toDouble() })) + 360) % 360).toFloat()
    val f = fg.copy(alpha = 1f).toArgb(); var l = if (dark) .80f else .55f
    fun col() = androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, if (dark) .72f else .58f, l))
    while (androidx.core.graphics.ColorUtils.calculateContrast(f, col()) < 4.5 && l in .1f..(.95f)) l += if (dark) .02f else -.02f
    return Color(col())
}
