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
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.ColumnScope
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
    /* the feed runs behind the status bar (its page pads itself), so a story can fill the whole screen without the feed jumping;
       everything else keeps clear of the system bars */
    val story = rs.feed.storyOpen
    Column(Modifier.fillMaxSize().background(P.bg).then(if (story) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))) {
        Box(Modifier.weight(1f)) {
            if (!rs.set.reels || rs.tab == "menu") Box(Modifier.windowInsetsPadding(WindowInsets.statusBars)) { Menu(rs) } else FeedView(rs)
        }
        if (rs.loaded && rs.set.reels && !story) NavBar(rs, if (rs.tab == "menu") "menu" else "feed")
    }
}

/** the deck list (Reels Mode: the Menu view) */
@Composable
private fun Menu(rs: Rs) {
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val vw = maxWidth
            /* the logo, the deck's dots and name, and All decks stay put; only the subjects scroll (inside the pages). short lists sit in the
               middle as before */
            Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (!rs.loaded) { Spinner(72.dp); return@Column }
                /* logo + name: tapping opens the credits; they squish in while pressed and pop out while the credits are open */
                val src = remember { MutableInteractionSource() }; val down by src.collectIsPressedAsState()
                val sc by animateFloatAsState(if (down) .94f else if (rs.creditsOpen) 1.08f else 1f, tween(if (down) 100 else 350), label = "ttl")
                /* the app tour's Menu part: once, the first time the Menu shows with a deck and nothing else open */
                LaunchedEffect(rs.loaded, rs.deck) {
                    if (!rs.loaded || rs.deck.empty || rs.tourSeen("menu")) return@LaunchedEffect
                    kotlinx.coroutines.delay(700)
                    androidx.compose.runtime.snapshotFlow { rs.tourClear() && rs.tour == null }.first { it }
                    kotlinx.coroutines.delay(300); if (rs.tourClear() && rs.tour == null && !rs.tourSeen("menu")) rs.tour = "menu"
                }
                Row(Modifier.padding(top = 26.dp, bottom = 19.dp).tourTarget(rs, "menu.logo").scale(sc).clickable(src, null) { rs.creditsOpen = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Logo(26.dp)
                    Txt("medslave", size = 32.sp, bold = true, spacing = (-.64).sp)
                }
                /* phones: the list spans the screen (up to 30em); wider screens: 42% of the width (a fixed width: the pager can't be measured intrinsically) */
                val w = if (vw < 600.dp) minOf(vw - 20.dp, 480.dp) else vw * .42f
                if (rs.deck.empty) Column(Modifier.width(w).verticalScroll(rememberScrollState())) { Empty(rs, vw) } else DeckPages(rs, w)
            }
        }
        /* settings: a round button in the top right */
        Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(42.dp).tourTarget(rs, "menu.gear").border(1.dp, P.border, CircleShape).press(bg = P.card, shape = CircleShape) { rs.settingsSec = null; rs.settingsOpen = true },
            contentAlignment = Alignment.Center) { Icon(Ic.GEAR, 19.dp, P.text) }
    }
}

/** the Menu's decks: a page per imported deck, swiped sideways. on top (fixed): the dots and the deck's name (its parent deck) when there are
    several, and All decks (that deck's every card). each page: its subjects, scrolling inside the page; a subject with subdecks has the
    picker button. a page's rows study only that deck's cards */
@Composable
private fun ColumnScope.DeckPages(rs: Rs, w: Dp) {
    val parts = rs.deck.parts
    val st = androidx.compose.foundation.pager.rememberPagerState { parts.size }
    Column(Modifier.width(w)) {
        if (parts.size > 1) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                parts.indices.forEach { i ->
                    val on = i == st.currentPage
                    Box(Modifier.padding(horizontal = 3.dp).size(if (on) 7.dp else 6.dp).clip(CircleShape).background(if (on) P.text else P.border))
                }
            }
            Txt(parts.getOrNull(st.currentPage)?.name ?: "Deck", size = 14.4.sp, color = P.faint, bold = true, lines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        }
        /* All subjects: every card in the deck on show; All decks: every card in every deck (one deck: just All decks, they're the same) */
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp).tourTarget(rs, "menu.all"), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            val sh = RoundedCornerShape(6.dp)
            @Composable fun btn(label: String, go: () -> Unit) =
                Box(Modifier.weight(1f).border(1.dp, P.border, sh).press(bg = P.card, shape = sh, onClick = go).padding(10.dp), contentAlignment = Alignment.Center) {
                    Txt(label, bold = true, align = TextAlign.Center, lines = 1)
                }
            if (parts.size > 1) btn("All subjects") { parts.getOrNull(st.currentPage)?.let { rs.go(it.tree.all) } }
            btn("All decks") { if (parts.size > 1) rs.go(rs.deck.all) else parts.firstOrNull()?.let { rs.go(it.tree.all) } }
        }
    }
    /* every page as tall as the tallest (all are composed and measured, up to the room left), so nothing moves while swiping */
    var tall by remember(parts) { mutableIntStateOf(0) }
    val dn = LocalDensity.current
    androidx.compose.foundation.pager.HorizontalPager(st, Modifier.width(w).weight(1f, fill = false).then(if (parts.size > 1) Modifier.tourTarget(rs, "menu.pager") else Modifier), pageSpacing = 16.dp, verticalAlignment = Alignment.Top, beyondViewportPageCount = parts.size) { i ->
        val t = parts[i].tree
        Column(Modifier.fillMaxWidth().onSizeChanged { if (it.height > tall) tall = it.height }.heightIn(min = with(dn) { tall.toDp() }).verticalScroll(rememberScrollState())) {
            t.subjects.forEachIndexed { k, s ->
                val n = t.top.find { it.name == s.name } ?: return@forEachIndexed
                Row(Modifier.fillMaxWidth().padding(bottom = 5.dp).then(if (k == 0 && i == st.currentPage) Modifier.tourTarget(rs, "menu.subject") else Modifier), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** Reels Mode's home: the feed (assets/web/feed.html) */
@Composable
private fun FeedView(rs: Rs) {
    if (!rs.loaded) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(72.dp) }; return }
    LaunchedEffect(rs.deck) { rs.feed.show() }
    LaunchedEffect(rs.set.nocom) { rs.feed.web.nocom(rs.set.nocom) }
    LaunchedEffect(rs.set.avatar) { rs.feed.web.avatars(rs.set.avatar == "solid") }
    LaunchedEffect(rs.set.fakeload) { rs.feed.web.fake(rs.set.fakeload) }
    /* the app tour's feed part: once, after the first posts are in and nothing else is open; the page is scrolled up and held, then asked
       where its controls are (Feed.gotRects starts it) */
    LaunchedEffect(rs.deck) {
        if (rs.deck.empty || rs.tourSeen("feed")) return@LaunchedEffect
        kotlinx.coroutines.delay(2400)
        androidx.compose.runtime.snapshotFlow { rs.tourClear() && rs.tour == null && rs.tab == "feed" }.first { it }
        rs.feed.web.top(true); rs.feed.web.tourLock(true); kotlinx.coroutines.delay(600)
        if (rs.tourClear() && rs.tour == null && rs.tab == "feed") rs.feed.askRects() else rs.feed.web.tourLock(false)
    }
    /* the feed's song plays while the feed is on screen */
    androidx.compose.runtime.DisposableEffect(Unit) { rs.feed.web.replay(); onDispose { rs.feed.stopSong() } }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); rs.feed.show() } }   // new stories every 5 minutes
    val bg = P.bg.toArgb()
    val dn = LocalDensity.current
    /* the bars' sizes even while a story hides them, so the story's top row stays clear of the camera cutout */
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val top = WindowInsets.statusBarsIgnoringVisibility.getTop(dn) / dn.density
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val bot = WindowInsets.navigationBarsIgnoringVisibility.getBottom(dn) / dn.density
    LaunchedEffect(top, bot) { rs.feed.web.insets(top, bot) }
    rs.density = dn.density
    AndroidView(factory = { rs.feed.web.detach(); rs.feed.web.view.apply { setBackgroundColor(bg) } }, update = { it.setBackgroundColor(bg) },
        modifier = Modifier.fillMaxSize().onGloballyPositioned { rs.feedOrigin = it.positionInRoot() })   // where the page sits (the tour's feed targets)
}

/* Reels Mode's home bar: Menu, Home (the feed), Reels (the last card reviewed), the current one in the text colour and bolder, the
   others in a solid mid-tone. Three buttons, like the
   reviewer's (menu, home, stats) */
@Composable
fun NavBar(rs: Rs, current: String) {
    val back = rs.recent()
    /* the other views: a solid mid-tone (the text colour mixed into the background), never transparency */
    val off = androidx.compose.ui.graphics.lerp(P.bg, P.text.copy(alpha = 1f), .45f)
    @Composable fun item(svg: String, on: Boolean, enabled: Boolean = true, stroke: Float = 2f, tk: String? = null, long: (() -> Unit)? = null, go: () -> Unit) =
        Box(Modifier.size(51.dp, 42.dp).then(if (tk != null) Modifier.tourTarget(rs, tk) else Modifier).press(enabled, shape = RoundedCornerShape(8.dp), onLong = long, onClick = go), contentAlignment = Alignment.Center) {
            Icon(svg, 24.dp, if (on) P.text else off, if (on) stroke + .4f else stroke)
        }
    Row(Modifier.fillMaxWidth().solidLine(P.divider, true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        item(Ic.LIST, current == "menu") { if (rs.study) rs.home(); rs.tab = "menu" }
        /* Home: a long press (with a buzz) picks a feed filter */
        item(Ic.HOME, current == "feed", tk = "bar.home", long = { rs.reels.buzz(); rs.feed.pickOpen = true }) { if (rs.study) rs.home(); rs.tab = "feed"; if (current == "feed") rs.feed.web.top() }
        /* Reels: back to the last card reviewed; the first time, All decks */
        item(Ic.REELS, current == "reels", tk = "bar.reels", enabled = current == "reels" || !rs.deck.empty) { if (current != "reels") rs.go(back ?: rs.deck.all) }
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

/** one deck's page in the credits: the pie (a slice per subject, by its number of questions; solid colours, no outlines), its name, the
    total, and the legend (colour, subject, questions and share) */
@Composable
private fun CreditPage(d: pizza.kulhad.medslave.Deck, cols: List<Pair<Color, Color>>, dark: Boolean, name: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val decks = d.top.filter { it.cnt > 0 }
        val tot = decks.sumOf { it.cnt }.coerceAtLeast(1)
        val colOf = { i: Int -> cols[i % cols.size].let { if (dark) it.second else it.first } }
        androidx.compose.foundation.Canvas(Modifier.size(170.dp)) {
            var start = -90f
            decks.forEachIndexed { i, n ->
                val sweep = 360f * n.cnt / tot
                drawArc(colOf(i), start, sweep, useCenter = true)
                start += sweep
            }
        }
        Gap(10.dp)
        name?.takeIf { it.isNotBlank() }?.let { Txt(it, size = 13.6.sp, color = P.faint, lines = 1) }   // the deck these questions come from
        Txt("%,d questions".format(decks.sumOf { it.cnt }), bold = true)
        Gap(10.dp)
        decks.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colOf(i)))
                Txt(n.name, size = 14.4.sp, modifier = Modifier.weight(1f), lines = 1)
                Txt("%,d".format(n.cnt) + "  ·  " + "%.0f%%".format(java.util.Locale.US, 100f * n.cnt / tot), size = 13.6.sp, color = P.faint)
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

/* ---------------------------------------------------------------- credits (#cr): logo, name, and the decks as a pie chart with a legend */
@Composable
fun Credits(rs: Rs) {
    val cols = remember { DCOL.shuffled() }
    val dark = P.dark
    Popup("Credits", { rs.creditsOpen = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(64.dp)
            Gap(10.dp)
            /* the name opens the project on GitHub */
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Txt("medslave", size = 24.sp, bold = true, modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/AnubisNekhet/medslave-mobile")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
            Txt("by a medslave, for medslaves", color = P.faint)
            /* the version under the subtitle, small and faint */
            val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
            if (ver.isNotEmpty()) Txt("v$ver", size = 13.sp, color = P.faint, modifier = Modifier.padding(top = 2.dp))
            Gap(16.dp)
            /* a page per deck (swiped sideways, dots under them when there are several): its pie and legend */
            val parts = rs.deck.parts
            if (parts.size <= 1) CreditPage(parts.firstOrNull()?.tree ?: rs.deck, cols, dark, parts.firstOrNull()?.name)
            else {
                val st = androidx.compose.foundation.pager.rememberPagerState { parts.size }
                Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
                    parts.indices.forEach { i -> val on = i == st.currentPage
                        Box(Modifier.padding(horizontal = 3.dp).size(if (on) 7.dp else 6.dp).clip(CircleShape).background(if (on) P.text else P.border)) }
                }
                androidx.compose.foundation.pager.HorizontalPager(st, Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { i -> CreditPage(parts[i].tree, cols, dark, parts[i].name) }
            }
        }
    }
}
