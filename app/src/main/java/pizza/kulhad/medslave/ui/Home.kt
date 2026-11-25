package pizza.kulhad.medslave.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pizza.kulhad.medslave.Node
import pizza.kulhad.medslave.R
import pizza.kulhad.medslave.Rs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** the logo, painted in the text colour (the site uses it as a mask) */
@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier, color: Color = P.text) =
    Image(painterResource(R.drawable.logo), null, modifier.size(size), colorFilter = ColorFilter.tint(color))

@Composable
fun Home(rs: Rs) {
    Column(Modifier.fillMaxSize().background(P.bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Box(Modifier.weight(1f)) {
            if (!rs.set.reels) Menu(rs, gear = true)
            else when (rs.tab) {
                "menu" -> Menu(rs, gear = false)
                "settings" -> SettingsPage(rs)
                else -> FeedView(rs)
            }
        }
        if (rs.loaded && rs.set.reels && !rs.feed.storyOpen) HomeBar(rs)
    }
}

/** the deck list (Reels Mode: the Menu view) */
@Composable
private fun Menu(rs: Rs, gear: Boolean) {
    val listW = remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val vh = maxHeight; val vw = maxWidth
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().heightIn(min = vh).padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (!rs.loaded) { Spinner(72.dp); return@Column }
                    /* logo + name: tapping opens the credits; they squish in while pressed and pop out while the credits are open */
                    val src = remember { MutableInteractionSource() }; val down by src.collectIsPressedAsState()
                    val sc by animateFloatAsState(if (down) .94f else if (rs.creditsOpen) 1.08f else 1f, tween(if (down) 100 else 350), label = "ttl")
                    Row(Modifier.padding(top = 26.dp, bottom = 19.dp).scale(sc).clickable(src, null) { rs.creditsOpen = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Logo(26.dp)
                        Txt("medslave", size = 32.sp, bold = true, spacing = (-.64).sp)
                    }
                    /* phones: the list spans the screen (up to 30em); wider screens: at least 42% of the width */
                    Column(Modifier.width(IntrinsicSize.Max).widthIn(min = if (vw < 600.dp) minOf(vw - 20.dp, 480.dp) else vw * .42f, max = vw - 20.dp).onSizeChanged { listW.value = it.width }) {
                        if (rs.deck.empty) Empty(rs, vw)
                        rs.deck.subjects.forEach { s ->
                            val n = rs.deck.top.find { it.name == s.name } ?: return@forEach
                            Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                val sh = RoundedCornerShape(6.dp)
                                Box(Modifier.weight(1f).border(1.dp, P.border, sh).press(bg = P.card, shape = sh) { rs.go(n) }.padding(10.dp), contentAlignment = Alignment.Center) {
                                    Txt(s.name, bold = true, align = TextAlign.Center)
                                }
                                if (n.kids.isNotEmpty()) Box(Modifier.size(35.dp).border(1.dp, P.border, sh).press(bg = P.card, shape = sh) { rs.picker = n }, contentAlignment = Alignment.Center) {
                                    Icon(Ic.DOWN, 18.dp, P.faint, 2.4f)
                                }
                            }
                        }
                    }
                    /* the heatmap is hidden for now (Heatmap() is kept to be placed later) */
                }
            }
        }
        /* settings: a round button in the top right (Reels Mode has Settings in the bottom bar) */
        if (gear) Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(42.dp).border(1.dp, P.border, CircleShape).press(bg = P.card, shape = CircleShape) { rs.settingsSec = null; rs.settingsOpen = true },
            contentAlignment = Alignment.Center) { Icon(Ic.GEAR, 19.dp, P.text) }
    }
}

/** Reels Mode's home: the feed (assets/web/feed.html) */
@Composable
private fun FeedView(rs: Rs) {
    if (!rs.loaded) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(72.dp) }; return }
    LaunchedEffect(rs.deck) { rs.feed.show() }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); rs.feed.show() } }   // new stories every 5 minutes
    val bg = P.bg.toArgb()
    AndroidView(factory = { rs.feed.web.detach(); rs.feed.web.view.apply { setBackgroundColor(bg) } }, update = { it.setBackgroundColor(bg) }, modifier = Modifier.fillMaxSize())
}

/* Reels Mode: a bottom bar where the reviewer's is. The views are Feed, Menu, Reels (back to the last card reviewed) and Settings;
   each view's bar holds the other three, always in that order */
@Composable
private fun HomeBar(rs: Rs) {
    val back = rs.recent()
    Row(Modifier.fillMaxWidth().solidLine(P.divider, true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf("feed", "menu", "reels", "settings").filter { it != rs.tab }.forEach { k ->
            when (k) {
                "feed" -> IconBtn(Ic.HOME, Modifier.size(51.dp, 42.dp), size = 24.dp, plain = true) { rs.tab = "feed" }
                "menu" -> IconBtn(Ic.LIST, Modifier.size(51.dp, 42.dp), size = 24.dp, stroke = 2f, plain = true) { rs.tab = "menu" }
                "reels" -> Box(Modifier.size(51.dp, 42.dp).alpha(if (back != null) 1f else .35f)) {
                    IconBtn(Ic.REELS, Modifier.fillMaxSize(), size = 24.dp, stroke = 2f, plain = true) { back?.let { rs.go(it) } }
                }
                "settings" -> IconBtn(Ic.GEAR, Modifier.size(51.dp, 42.dp), size = 24.dp, stroke = 2f, plain = true) { rs.settingsSec = null; rs.tab = "settings" }
            }
        }
    }
}

/** the activity heatmap, opened from the home bar */
@Composable
fun HeatPanel(rs: Rs) {
    Popup("Activity", { rs.heatOpen = false }) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp), contentAlignment = Alignment.Center) {
            Heatmap(rs, maxWidth)
        }
    }
}

/** no deck yet: the import prompt */
@Composable
private fun Empty(rs: Rs, vw: Dp) {
    Column(Modifier.widthIn(max = minOf(384.dp, vw - 32.dp)).padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt("No deck yet", size = 18.4.sp, bold = true)
        Txt("Import an Anki deck (.apkg) to start. It stays on this device.", color = P.faint, align = TextAlign.Center)
        Btn("Import a deck", Modifier.padding(top = 5.dp), p = true, h = 40.dp, enabled = !rs.busy) { rs.deckPick() }
        if (rs.dkMsg.isNotEmpty()) Txt(rs.dkMsg, size = 13.6.sp, color = P.faint, align = TextAlign.Center)
    }
}

/* ---------------------------------------------------------------- heatmap (heatmap.js): answers revealed per day, one block per month,
   Monday-first columns, today at the right end; scrolling left goes back in time. A tap shows the day and its count */
@Composable
fun Heatmap(rs: Rs, w: Dp) {
    val m = remember(rs.heatTick) { rs.store.actMap() }
    val mx = remember(m) { m.values.maxOrNull() ?: 0 }
    val today = remember(rs.heatTick) { LocalDate.now() }
    val hm = P.accent
    val st = rememberLazyListState()
    var tip by remember { mutableStateOf<Triple<String, Int, Offset>?>(null) }
    var root by remember { mutableStateOf(Offset.Zero) }
    val origins = remember { mutableStateMapOf<Int, Offset>() }
    LaunchedEffect(st.isScrollInProgress) { if (st.isScrollInProgress) tip = null }
    val fl by animateFloatAsState(if (st.canScrollForward) 28f else 0f, tween(150), label = "fl")   // reversed: forward = older, on the left
    val fr by animateFloatAsState(if (st.canScrollBackward) 28f else 0f, tween(150), label = "fr")
    val d = LocalDensity.current.density
    Box(Modifier.padding(top = 22.dp).width(w).onGloballyPositioned { root = it.positionInRoot() }) {
        LazyRow(Modifier.fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
            drawContent()
            val a = fl * d / size.width; val b = 1 - fr * d / size.width
            drawRect(Brush.horizontalGradient(0f to Color.Transparent, a.coerceIn(0f, 1f) to Color.Black, b.coerceIn(0f, 1f) to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }, state = st, reverseLayout = true, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(1200) { i ->
                val f = today.withDayOfMonth(1).minusMonths(i.toLong())
                val last = if (i == 0) today.dayOfMonth else f.lengthOfMonth()
                val pad = f.dayOfWeek.value - 1
                val cols = ceil((pad + last) / 7f).toInt()
                Column(Modifier.onGloballyPositioned { origins[i] = it.positionInRoot() }) {
                    Txt(f.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) + if (f.monthValue == 1) " ${f.year}" else "", size = 10.sp, color = P.faint, lh = 14.sp, modifier = Modifier.height(14.dp))
                    Gap(3.dp)
                    Box(Modifier.size((cols * 15 - 3).dp, (7 * 15 - 3).dp).pointerInput(i, m) {
                        detectTapGestures { o ->
                            val c = (o.x / (15 * d)).toInt(); val r = (o.y / (15 * d)).toInt(); val day = c * 7 + r - pad + 1
                            if (day < 1 || day > last || r > 6) { tip = null; return@detectTapGestures }
                            val x = f.withDayOfMonth(day); val k = x.toString()
                            if (tip?.first == k) { tip = null; return@detectTapGestures }
                            val org = origins[i] ?: Offset.Zero
                            tip = Triple(k, m[k] ?: 0, Offset(org.x - root.x + (c * 15 + 6) * d, org.y - root.y + (17 + r * 15) * d))
                        }
                    }) {
                        for (day in 1..last) {
                            val idx = pad + day - 1; val c = idx / 7; val r = idx % 7
                            val k = f.withDayOfMonth(day).toString(); val n = m[k] ?: 0
                            val l = if (n > 0 && mx > 0) maxOf(1, ceil(4.0 * n / mx).toInt()) else 0
                            val a = floatArrayOf(.07f, .22f, .42f, .66f, .92f)[l]
                            Box(Modifier.offset((c * 15).dp, (r * 15).dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(hm.copy(alpha = hm.alpha * a))
                                .then(if (i == 0 && day == last) Modifier.border(1.dp, hm.copy(alpha = .7f), RoundedCornerShape(3.dp)) else Modifier))
                        }
                    }
                }
            }
        }
        tip?.let { (k, n, o) ->
            val day = LocalDate.parse(k).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))
            var tw by remember { mutableStateOf(0) }
            Column(Modifier.offset { IntOffset((o.x - tw / 2f).coerceIn(4 * d, w.value * d - tw - 4 * d).roundToInt(), (o.y - 8 * d).roundToInt() - (36 * d).roundToInt()) }
                .onSizeChanged { tw = it.width }.clip(RoundedCornerShape(6.dp)).background(hm).padding(horizontal = 9.dp, vertical = 5.dp)) {
                Txt(day, size = 11.sp, bold = true, color = P.card, lines = 1)
                Txt("$n card${if (n == 1) "" else "s"} done", size = 11.sp, color = P.card, lines = 1)
            }
        }
    }
}

/* ---------------------------------------------------------------- the subdeck picker (#pop): the subject's tree, each row opens that deck */
@Composable
fun Picker(rs: Rs, n: Node) {
    val open = remember(n) { mutableStateMapOf<Int, Boolean>() }
    Popup(n.name, { rs.picker = null }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
            @Composable fun rows(l: List<Node>, depth: Int) {
                l.forEach { k ->
                    Row(Modifier.fillMaxWidth().padding(start = (depth * 16).dp), verticalAlignment = Alignment.CenterVertically) {
                        val o = open[k.id] == true
                        val rot by animateFloatAsState(if (o) 90f else 0f, tween(150), label = "ch")
                        Box(Modifier.size(35.dp).then(if (k.kids.isNotEmpty()) Modifier.press(shape = RoundedCornerShape(6.dp)) { open[k.id] = !o } else Modifier), contentAlignment = Alignment.Center) {
                            if (k.kids.isNotEmpty()) Icon(Ic.RIGHT, 18.dp, P.faint, 2.4f, modifier = Modifier.rotate(rot))
                        }
                        Row(Modifier.weight(1f).press { rs.go(k) }.padding(horizontal = 8.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Txt(k.name, Modifier.weight(1f))
                            Txt("${k.cnt}", size = 13.6.sp, color = P.faint)
                        }
                    }
                    if (open[k.id] == true) rows(k.kids, depth + 1)
                }
            }
            rows(n.kids, 0)
        }
    }
}

/* ---------------------------------------------------------------- credits (#cr): logo, name, and a pill per top-level deck */
@Composable
fun Credits(rs: Rs) {
    val cols = remember { DCOL.shuffled() }
    val dark = P.dark
    Popup("Credits", { rs.creditsOpen = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(64.dp)
            Gap(10.dp)
            Txt("medslave", size = 24.sp, bold = true)
            Txt("your own Anki decks, on your phone", color = P.faint)
            Gap(6.dp)
            Txt("bring any Anki deck; it stays on your device", size = 14.sp, color = P.faint, align = TextAlign.Center)
            Gap(14.dp)
            val tot = rs.deck.top.sumOf { it.cnt }
            Pill(Ic.ALL, "All decks", "%,d questions".format(tot), null, dark)
            rs.deck.top.forEachIndexed { i, n ->
                val k = cols[i % cols.size]; val ic = DICO.first { it.first.containsMatchIn(n.name.lowercase()) }.second
                Pill(ic, n.name, "%,d questions".format(n.cnt), if (dark) k.second else k.first, dark)
            }
        }
    }
}
@Composable
private fun Pill(icon: String, name: String, sub: String, c: Color?, dark: Boolean) {
    val bg = c ?: P.press; val fg = if (c == null) P.text else if (dark) Color(0xFF1E1E2E) else Color.White
    Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(bg).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, 19.dp, fg, 2f)
        Column { Txt(name, bold = true, color = fg); Txt(sub, size = 12.sp, color = fg.copy(alpha = .8f)) }
    }
}
