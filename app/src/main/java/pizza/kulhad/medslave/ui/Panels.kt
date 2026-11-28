package pizza.kulhad.medslave.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import pizza.kulhad.medslave.Choice
import pizza.kulhad.medslave.IKS
import pizza.kulhad.medslave.ISL
import pizza.kulhad.medslave.ImportType
import pizza.kulhad.medslave.Rs
import pizza.kulhad.medslave.THEMES

/* ---------------------------------------------------------------- settings (#sp): a menu of Appearance, Reviewing, Data */
@Composable
fun SettingsPanel(rs: Rs) {
    val names = mapOf("ap" to "Appearance", "rv" to "Reviewing", "fd" to "Feed", "hd" to "Hidden cards", "hs" to "Hidden subjects", "ff" to "Feed filters", "dk" to "Decks", "pg" to "Progress")
    androidx.activity.compose.BackHandler(rs.settingsSec != null) { rs.settingsSec = upOf(rs.settingsSec) }
    Popup({ SettingsTitle(rs, names) }, { rs.settingsOpen = false }) { SettingsBody(rs, names) }
}

/** where a settings section's back goes: Feed's lists back to Feed, the rest to the menu */
private fun upOf(sec: String?) = if (sec == "hd" || sec == "hs" || sec == "ff") "fd" else null

@Composable
private fun SettingsTitle(rs: Rs, names: Map<String, String>, size: androidx.compose.ui.unit.TextUnit = 16.sp) {
    val sec = rs.settingsSec
    if (sec != null) Box(Modifier.size(30.dp).press { rs.settingsSec = upOf(sec) }, contentAlignment = Alignment.Center) { Txt("‹", size = 22.sp, color = P.faint) }
    Txt(names[sec] ?: "Settings", size = size, bold = true, modifier = Modifier.padding(start = if (sec != null) 4.dp else 0.dp))
}

@Composable
private fun SettingsBody(rs: Rs, names: Map<String, String>) {
    /* the site's svIn / svBk: a section slides in from the right (1.5em) and fades in over .22s; going back, the menu comes in from the left */
    val sec = rs.settingsSec
    key(sec) {
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) { a.animateTo(1f, tween(220, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
        val dir = if (sec != null) 1f else -1f
        Column(
            Modifier.graphicsLayer {
                translationX = (1 - a.value) * dir * 24 * density
                alpha = a.value
            }.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (sec) {
                null -> listOf("ap" to Ic.PALETTE, "rv" to Ic.CARDS, "fd" to Ic.HOME, "dk" to Ic.DB, "pg" to Ic.HISTORY).forEachIndexed { i, (k, ic) -> SecRow(rs, names, i, k, ic) }
                "fd" -> {
                    rs.feed.hiddenTick // the counts follow the lists
                    SecRow(rs, names, 0, "ff", Ic.FILTER, rs.feed.filters.size)
                    SecRow(rs, names, 1, "hd", Ic.STOP, rs.feed.hidden().size)
                    SecRow(rs, names, 2, "hs", Ic.BAN, rs.feed.hiddenSubjects().size)
                }
                "ap" -> Appearance(rs)
                "rv" -> {
                    Row2("Ordered mode") { Switch(rs.set.ordered) { rs.setOrdered(it) } }
                    /* what cards show: re-drawn at once, and the feed follows */
                    val opts = {
                        rs.set.save()
                        rs.web.opts(rs.set.source, rs.set.tags)
                        rs.feed.opts()
                        rs.cur?.let { c -> rs.web.show(c.json, c.o, rs.stage, rs.set.flip) }
                    }
                    Row2("Show source") {
                        Switch(rs.set.source) {
                            rs.set.source = it
                            opts()
                        }
                    }
                    if (rs.deck.hasMcq) {
                        Row2("Hide explanation", if (rs.set.flip) "On the back of the answer: swipe sideways to turn it over" else "On the back of the answer: swipe up or down to turn it over") {
                            Switch(rs.set.hidex) {
                                rs.set.hidex = it
                                rs.web.hidex(it)
                                opts()
                            }
                        }
                    }
                    Row2("Show tags", "At the bottom of each card") {
                        Switch(rs.set.tags) {
                            rs.set.tags = it
                            opts()
                        }
                    }
                    if (rs.set.reels) {
                        Row2("Red likes", "Likes in Instagram red instead of the accent") {
                            Switch(rs.set.redlike) {
                                rs.set.redlike = it
                                rs.set.save()
                            }
                        }
                    }
                    Row2("Volume keys", "Down if you knew it, up if you didn’t") {
                        Switch(rs.set.volkeys) {
                            rs.set.volkeys = it
                            rs.set.save()
                        }
                    }
                    var tdone by remember { mutableStateOf(false) }
                    LaunchedEffect(tdone) {
                        if (tdone) {
                            delay(1500)
                            tdone = false
                        }
                    }
                    Row2("Show the tutorial again", "The app tour and the swipe demo show again") {
                        Btn(if (tdone) "Done" else "Reset", enabled = !tdone, h = 37.dp, size = 14.4.sp, pad = 18.dp) {
                            rs.resetTutorial()
                            tdone = true
                        }
                    }
                }
                "hd" -> HiddenSec(rs)
                "hs" -> HiddenSubjSec(rs)
                "ff" -> FiltersSec(rs)
                "dk" -> DecksSec(rs)
                "pg" -> ProgressSec(rs)
            }
        }
    }
}

/** a row of the settings menu (or of Feed) that opens section k; n: a count shown before the chevron */
@Composable
private fun SecRow(rs: Rs, names: Map<String, String>, i: Int, k: String, ic: String, n: Int = -1) {
    Row(
        Modifier.fillMaxWidth().then(if (i > 0) Modifier.dashedBottom(P.divider, top = true) else Modifier).press(shape = RoundedCornerShape(10.dp)) { rs.settingsSec = k }
            .padding(horizontal = 3.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(ic, 24.dp, P.text, 1.8f)
        Txt(names[k]!!, bold = true, modifier = Modifier.weight(1f))
        if (n > 0) Txt(n.toString(), size = 14.sp, color = P.faint)
        Txt("›", size = 20.sp, color = P.faint)
    }
}

/** Settings › Feed › Feed filters: None and each filter (tap one to turn it on; a check marks the one that's on), the pencil edits one,
 × deletes it; Add a filter. a filter is a name and the decks the feed draws from while it's on */
@Composable
private fun FiltersSec(rs: Rs) {
    val f = rs.feed
    var editing by remember { mutableStateOf<pizza.kulhad.medslave.Feed.Filter?>(null) }
    var adding by remember { mutableStateOf(false) }
    Txt("With a filter on, the feed’s posts and stories come only from its subjects. Long press Home to switch filters.", size = 13.6.sp, color = P.faint, modifier = Modifier.padding(vertical = 6.dp))
    FilterRow(0, "None", "Every deck", noFilter(f)) { f.useFilter(null) }
    FilterRow(1, "Following", "Only accounts you follow", f.active == pizza.kulhad.medslave.FOLLOW_ID) { f.useFilter(pizza.kulhad.medslave.FOLLOW_ID) }
    f.filters.forEachIndexed { i, x ->
        FilterRow(i + 2, x.name, null, f.active == x.id, { editing = x }, { f.dropFilter(x.id) }) { f.useFilter(x.id) }
    }
    Btn("Add a filter", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), p = true, h = 38.dp, size = 14.4.sp, enabled = rs.deck.parts.isNotEmpty()) { adding = true }
    if (rs.deck.parts.isEmpty()) Txt("Add a deck first (Settings › Decks).", size = 12.8.sp, color = P.faint)
    if (adding) FilterEdit(rs, null) { adding = false }
    editing?.let { x -> FilterEdit(rs, x) { editing = null } }
}

/** no filter on: none chosen, or one that's since been deleted */
private fun noFilter(f: pizza.kulhad.medslave.Feed) = f.active == null || (f.active != pizza.kulhad.medslave.FOLLOW_ID && f.filters.none { it.id == f.active })

/** a filter's subjects as a line, by main deck ("Deck: A, B · Other deck: all"); decks no longer here are left out */
fun filterDecks(rs: Rs, x: pizza.kulhad.medslave.Feed.Filter): String =
    rs.deck.parts.mapNotNull { p ->
        val s = if (p.id in x.decks) listOf("all") else x.decks.filter { it.startsWith(p.id + "/") }.map { it.substringAfter('/') }
        if (s.isEmpty()) null else "${p.name}: ${s.joinToString(", ")}"
    }.joinToString(" · ").ifEmpty { "Nothing of it is here" }

@Composable
private fun FilterRow(i: Int, name: String, sub: String?, on: Boolean, edit: (() -> Unit)? = null, drop: (() -> Unit)? = null, pick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (i > 0) Modifier.dashedBottom(P.divider, top = true) else Modifier).press(shape = RoundedCornerShape(10.dp), onClick = pick).padding(horizontal = 3.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { if (on) Icon(Ic.CHECK, 20.dp, P.text, 2.4f) }
        Column(Modifier.weight(1f)) {
            Txt(name, size = 14.4.sp, bold = on, lines = 1)
            sub?.let { Txt(it, size = 12.sp, color = P.faint, lines = 2) }
        }
        if (edit != null) Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = edit), contentAlignment = Alignment.Center) { Txt("✎", size = 16.sp, color = P.faint) }
        if (drop != null) Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = drop), contentAlignment = Alignment.Center) { Txt("×", size = 20.sp, color = P.faint) }
    }
}

/** a new filter or one being edited: a name, then a page per main deck (swiped sideways, dots and its name on top) with a switch for all of
 it and one per subject (its first subdecks). entries are "<deck id>/<subject>" */
@Composable
private fun FilterEdit(rs: Rs, x: pizza.kulhad.medslave.Feed.Filter?, close: () -> Unit) {
    var name by remember { mutableStateOf(x?.name ?: "") }
    val parts = rs.deck.parts
    /* an older filter's whole deck becomes each of its subjects */
    val on = remember {
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            x?.decks?.forEach { k ->
                if ('/' in k) add(k) else parts.find { it.id == k }?.let { p -> addAll(p.tree.subjects.map { "${p.id}/${it.name}" }) }
            }
        }
    }
    androidx.compose.ui.window.Dialog(close, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(P.card).border(1.dp, P.border, RoundedCornerShape(8.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Txt(if (x != null) "Edit filter" else "Add a filter", bold = true)
            Field(name, { name = it }, "Name", Modifier.fillMaxWidth())
            val st = androidx.compose.foundation.pager.rememberPagerState { parts.size }
            if (parts.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    parts.indices.forEach { i ->
                        val cur = i == st.currentPage
                        Box(Modifier.padding(horizontal = 3.dp).size(if (cur) 7.dp else 6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (cur) P.text else P.border))
                    }
                }
            }
            Txt(parts.getOrNull(st.currentPage)?.name ?: "", size = 14.4.sp, bold = true, lines = 1, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            androidx.compose.foundation.pager.HorizontalPager(st, Modifier.fillMaxWidth().height(300.dp), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { i ->
                val p = parts[i]
                val keys = p.tree.subjects.map { "${p.id}/${it.name}" }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row2("All of ${p.name}", labelColor = P.accent) { Switch(keys.isNotEmpty() && keys.all { it in on }) { v -> if (v) keys.forEach { k -> if (k !in on) on.add(k) } else on.removeAll(keys) } }
                    p.tree.subjects.forEach { s ->
                        val k = "${p.id}/${s.name}"
                        Row2(s.name) { Switch(k in on) { v -> if (v) on.add(k) else on.remove(k) } }
                    }
                }
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Cancel", h = 36.dp, size = 14.4.sp, pad = 16.dp, onClick = close)
                Btn(if (x != null) "Save" else "Add", p = true, h = 36.dp, size = 14.4.sp, pad = 16.dp, enabled = on.isNotEmpty()) {
                    rs.feed.putFilter(x?.id, name.ifBlank { on.map { it.substringAfter('/') }.distinct().let { l -> l.take(3).joinToString(" + ") + if (l.size > 3) " +${l.size - 3}" else "" } }, on.toList())
                    close()
                }
            }
        }
    }
}

/** Home's long press: pick a feed filter (or none). managing them is in Settings › Feed › Feed filters */
@Composable
fun FilterPick(rs: Rs) {
    val f = rs.feed
    val close = { f.pickOpen = false }
    androidx.compose.ui.window.Dialog(close, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(P.card).border(1.dp, P.border, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Txt("Feed filter", bold = true, modifier = Modifier.padding(bottom = 6.dp))
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                FilterRow(0, "None", "Every deck", noFilter(f)) {
                    f.useFilter(null)
                    close()
                }
                FilterRow(1, "Following", "Only accounts you follow", f.active == pizza.kulhad.medslave.FOLLOW_ID) {
                    f.useFilter(pizza.kulhad.medslave.FOLLOW_ID)
                    close()
                }
                f.filters.forEachIndexed { i, x ->
                    FilterRow(i + 2, x.name, null, f.active == x.id) {
                        f.useFilter(x.id)
                        close()
                    }
                }
            }
            if (f.filters.isEmpty()) Txt("Make your own in Settings › Feed › Feed filters.", size = 12.8.sp, color = P.faint, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Settings › Feed › Hidden subjects: the subjects hidden by blocking an account; Unhide brings a subject's cards back */
@Composable
private fun HiddenSubjSec(rs: Rs) {
    rs.feed.hiddenTick // re-read when the list changes
    val l = rs.feed.hiddenSubjects()
    if (l.isEmpty()) {
        Txt("Blocking an account hides every subject it posts, and they show up here. Their cards don\u2019t come up in posts, stories or reels.", size = 14.sp, color = P.faint, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    l.forEachIndexed { i, h ->
        Row(Modifier.fillMaxWidth().then(if (i > 0) Modifier.dashedBottom(P.divider, top = true) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Txt(h.name, size = 14.4.sp)
                Txt(
                    listOfNotNull(
                        h.by.takeIf { it.isNotEmpty() }?.let { "Blocked @$it" },
                        if (h.cards > 0) {
                            "${h.cards} card${if (h.cards == 1) "" else "s"}"
                        } else if (h.by == pizza.kulhad.medslave.CD_HANDLE || h.by == pizza.kulhad.medslave.HY_HANDLE) {
                            null
                        } else {
                            "Not in this deck"
                        }
                    ).joinToString(" · "),
                    size = 12.sp,
                    color = P.faint
                )
            }
            Btn("Unhide", h = 34.dp, size = 13.6.sp, pad = 14.dp) { rs.feed.unhideSubject(h.name) }
        }
    }
}

/** Settings › Feed › Hidden cards: the cards hidden from a post's menu; Unhide lets one come up again */
@Composable
private fun HiddenSec(rs: Rs) {
    rs.feed.hiddenTick // re-read when the list changes
    val l = rs.feed.hidden()
    if (l.isEmpty()) {
        Txt("Cards you hide from a post\u2019s menu show up here. They don\u2019t come up in posts, stories or reels.", size = 14.sp, color = P.faint, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    l.forEachIndexed { i, h ->
        Row(Modifier.fillMaxWidth().then(if (i > 0) Modifier.dashedBottom(P.divider, top = true) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Txt(h.text.ifEmpty { "A card" }.let { if (it.length > 90) it.take(88).trimEnd() + "…" else it }, size = 14.4.sp)
                if (h.path.isNotEmpty()) Txt(h.path, size = 12.sp, color = P.faint)
            }
            Btn("Unhide", h = 34.dp, size = 13.6.sp, pad = 14.dp) { rs.feed.unhide(h.id, h.o) }
        }
    }
}

@Composable
private fun Head(t: String) = Txt(t, bold = true, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))

/** an option tile (.rvo): a preview on top, a label under it; the chosen one gets a text-coloured ring */
@Composable
private fun Tile(label: String, on: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val s = RoundedCornerShape(8.dp)
    val ring by androidx.compose.animation.animateColorAsState(if (on) P.text else P.divider, tween(200), label = "ring")
    val w by androidx.compose.animation.core.animateDpAsState(if (on) 2.dp else 1.dp, tween(200), label = "ringW")
    val fg by androidx.compose.animation.animateColorAsState(if (on) P.text else P.faint, tween(200), label = "lbl")
    Column(
        modifier.alpha(if (enabled) 1f else .45f).border(w, ring, s).clip(s)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().background(P.text.copy(alpha = .05f)), contentAlignment = Alignment.Center) { preview() }
        Box(Modifier.fillMaxWidth().solidLine(P.divider, true).padding(vertical = if (small) 6.dp else 8.dp), contentAlignment = Alignment.Center) {
            Txt(label, size = if (small) 12.sp else 14.4.sp, bold = true, color = fg, lines = 1)
        }
    }
}

/** a tiny card with three lines, for the previews */
@Composable
private fun Mini(modifier: Modifier = Modifier, answer: Boolean = false) {
    Column(
        modifier.size(46.dp, 58.dp).clip(RoundedCornerShape(6.dp)).border(1.5.dp, P.text.copy(alpha = .35f), RoundedCornerShape(6.dp))
            .background(P.card).padding(horizontal = 8.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.text.copy(alpha = .25f)))
        Box(Modifier.fillMaxWidth(if (answer) .55f else .75f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (answer) P.accent.copy(alpha = .6f) else P.text.copy(alpha = .25f)))
        Box(Modifier.fillMaxWidth(.55f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.text.copy(alpha = .25f)))
    }
}

/* the mode tiles' icons: auto (half sun, half moon), light (sun), dark (moon) */
private val MODE_IC = mapOf(
    "auto" to "<circle cx=\"12\" cy=\"12\" r=\"8\"/><path d=\"M12 4a8 8 0 0 1 0 16z\" fill=\"currentColor\"/>",
    "light" to "<circle cx=\"12\" cy=\"12\" r=\"4\"/><path d=\"M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41\"/>",
    "dark" to "<path d=\"M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z\"/>"
)

@Composable
private fun Appearance(rs: Rs) {
    Head("Mode")
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf("auto" to "Auto", "light" to "Light", "dark" to "Dark").forEach { (k, l) ->
            Tile(l, rs.set.mode == k, modifier = Modifier.weight(1f), small = true, onClick = {
                rs.set.mode = k
                rs.set.save()
            }) {
                Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) { Icon(MODE_IC[k]!!, 24.dp, P.text, 1.8f) }
            }
        }
    }
    Head("Theme")
    val ts = rememberScrollState()
    val dn = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(Unit) {
        val i = THEMES.indexOf(rs.set.theme).coerceAtLeast(0)
        ts.scrollTo(((i + 1) * 114 + 16) * dn.toInt() - ts.viewportSize)
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(ts).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val tp = mapOf("default" to "Default", "you" to "Material You", "ctp" to "Catppuccin", "tokyo" to "Tokyo Night", "nord" to "Nord", "dracula" to "Dracula")
        THEMES.filter { it != "you" || pizza.kulhad.medslave.ui.You.ok }.forEach { t ->
            val l = pal(t, false)
            val d = pal(t, true)
            val la = if (t == "tokyo" || t == "default") Color(0xFFE49320) else l.accent
            val da = if (t == "default") Color(0xFFF9E2AF) else d.accent
            Tile(tp[t]!!, rs.set.theme == t, modifier = Modifier.width(106.dp), small = true, onClick = {
                if (rs.set.theme != t) rs.set.accent = -1
                rs.set.theme = t
                rs.set.save()
            }) {
                Row(Modifier.height(58.dp)) {
                    listOf(Triple(l.bg, l.card, la), Triple(d.bg, d.card, da)).forEach { (bg, cd, ac) ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(bg), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.fillMaxWidth(.62f).fillMaxHeight(.58f).clip(RoundedCornerShape(5.dp)).background(cd).padding(horizontal = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(3.5.dp, Alignment.CenterVertically)
                            ) {
                                Box(Modifier.fillMaxWidth().height(3.5.dp).clip(RoundedCornerShape(2.dp)).background(ac))
                                Box(Modifier.fillMaxWidth(.6f).height(3.5.dp).clip(RoundedCornerShape(2.dp)).background(ac.copy(alpha = .55f)))
                            }
                        }
                    }
                }
            }
        }
    }
    val lock = rs.set.reels
    /* the accent: the theme's own colours as circles (its blue by default); it fills full-contrast buttons and switches */
    Head("Accent")
    val list = pizza.kulhad.medslave.ui.accents(rs.set.theme).map { if (P.dark) it.second else it.first }
    val now = pizza.kulhad.medslave.ui.accentOf(rs.set.theme, rs.set.accent, P.dark)
    val acs = rememberScrollState()
    LaunchedEffect(rs.set.theme) {
        val i = list.indexOf(now).coerceAtLeast(0)
        acs.scrollTo(((i * 48 + 19) * dn).toInt() - acs.viewportSize / 2)
    } // the chosen one in view
    Row(Modifier.fillMaxWidth().horizontalScroll(acs).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        list.forEachIndexed { i, col ->
            val sel = col == now
            Box(
                Modifier.size(38.dp).clip(CircleShape).border(2.dp, if (sel) P.text else Color.Transparent, CircleShape).padding(4.dp).clip(CircleShape)
                    .background(col).clickable {
                        rs.set.accent = i
                        rs.set.save()
                    }
            )
        }
    }
    Head("Review style")
    /* Reels is a review style of its own: Flip + Scroll up with the Reels overlay */
    val flipStyle = rs.set.style == "flip" || rs.set.style == "doom"
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Tile("Tinder", !lock && !flipStyle, modifier = Modifier.weight(1f), small = true, onClick = { setStyle(rs, "tinder") }) { TinderDemo() }
        Tile("Flip", !lock && flipStyle, modifier = Modifier.weight(1f), small = true, onClick = { setStyle(rs, "flip") }) { FlipDemo() }
        Tile("Reels", lock, modifier = Modifier.weight(1f), small = true, onClick = { setStyle(rs, "reels") }) { ReelsDemo() }
    }
    Head("New Card Reveal")
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf("none" to "No animation", "pop" to "Pop in", "up" to "Scroll up").forEach { (k, l) ->
            Tile(l, rs.set.rev == k, !lock, Modifier.weight(1f), small = true, onClick = {
                rs.set.reveal = k
                rs.set.save()
            }) { RevealDemo(k) }
        }
    }
    /* Reels Mode: the accounts' avatars, with waves (the default) or plain colour; each tile shows a story circle in that style */
    if (rs.set.reels) {
        Head("Profile style")
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("waves" to "Waves", "solid" to "Solid").forEach { (k, l) ->
                Tile(l, rs.set.avatar == k, modifier = Modifier.weight(1f), small = true, onClick = {
                    rs.set.avatar = k
                    rs.set.save()
                }) { AvatarDemo(k == "waves") }
            }
        }
    }
    if (rs.set.reels) {
        Row2("Loading animations", "Placeholders and short waits, as if the feed were online") {
            Switch(rs.set.fakeload) {
                rs.set.fakeload = it
                rs.set.save()
            }
        }
    }
    if (rs.set.reels) {
        Row2("Hide comments", "No comment buttons or comments in the feed and on reels") {
            Switch(rs.set.nocom) {
                rs.set.nocom = it
                rs.set.save()
            }
        }
    }
}

/** two story circles (the ring in the text colour, the avatar inside), in the given profile style */
@Composable
private fun AvatarDemo(waves: Boolean) {
    val cols = remember { DCOL.shuffled(java.util.Random(7)).take(2) }
    Row(Modifier.height(104.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Ic.HEART to cols[0], Ic.NOTE to cols[1]).forEach { (ic, c) ->
            val col = if (P.dark) c.second else c.first
            Box(Modifier.size(52.dp).border(2.dp, P.text, CircleShape).padding(4.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                if (waves) Waves(col)
                Icon(ic, 22.dp, Color(0xFF1E1E2E))
            }
        }
    }
}

/** tinder | flip | reels (Reels keeps the saved Tinder/Flip choice for when it's turned off) */
private fun setStyle(rs: Rs, v: String) {
    if (v == "reels") {
        rs.set.reels = true
    } else {
        rs.set.reels = false
        rs.set.style = v
    }
    rs.set.save()
    redraw(rs)
    rs.reels.onCard()
}

/** the card on screen again, in the current style */
private fun redraw(rs: Rs) {
    rs.cur?.let { c -> rs.web.show(c.json, c.o, rs.stage, rs.set.flip) }
}

@Composable
private fun TinderDemo() {
    val t by rememberInfiniteTransition(label = "t").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "t")
    /* rvT: rests, swipes right and fades (30–55%), the next card takes its place (56–78%) */
    val (x, r, a) = when {
        t < .30f -> Triple(0f, 0f, 1f)
        t < .55f -> ((t - .30f) / .25f).let { Triple(it * 58f, it * 22f, 1 - it) }
        t < .56f -> Triple(0f, 0f, 0f)
        t < .78f -> Triple(0f, 0f, (t - .56f) / .22f)
        else -> Triple(0f, 0f, 1f)
    }
    Box(Modifier.height(104.dp), contentAlignment = Alignment.Center) {
        Mini(
            Modifier.graphicsLayer {
                translationY = 5.6f * density
                scaleX = .9f
                scaleY = .9f
                alpha = .6f
            }
        )
        Mini(
            Modifier.graphicsLayer {
                translationX = x * density
                rotationZ = r
                alpha = a
            }
        )
        /* tpT: the finger lands (12–22%), drags right with the card and lifts off (30–48%) */
        val fs = seg(t, 0f to 1.6f, .12f to 1.6f, .22f to 1f, 1f to 1f)
        Tap(
            Modifier.graphicsLayer {
                translationX = seg(t, 0f to 0f, .30f to 0f, .48f to 45f, 1f to 45f) * density
                translationY = 12 * density
                scaleX = fs
                scaleY = fs
                alpha = seg(t, 0f to 0f, .12f to 0f, .22f to 1f, .30f to 1f, .48f to 0f, 1f to 0f)
            }
        )
    }
}

/** the finger in the review style previews (.tap) */
@Composable private fun Tap(m: Modifier) = Box(m.size(22.dp).clip(CircleShape).background(P.text.copy(alpha = .3f)).border(2.dp, P.text, CircleShape))

@Composable
private fun FlipDemo() {
    val t by rememberInfiniteTransition(label = "f").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "f")
    /* rvD: flips over (30–50%), holds, flips back (80–100%) */
    val deg = when {
        t < .30f -> 0f
        t < .50f -> (t - .30f) / .20f * 180f
        t < .80f -> 180f
        else -> 180f + (t - .80f) / .20f * 180f
    }
    Box(Modifier.height(104.dp), contentAlignment = Alignment.Center) {
        Mini(
            Modifier.graphicsLayer {
                rotationX = deg
                cameraDistance = 8 * density
            },
            answer = deg % 360f in 90f..270f
        )
        /* tpD: two swipes up, one before each flip */
        val fs = seg(t, 0f to 1.6f, .06f to 1.6f, .14f to 1f, .56f to 1f, .57f to 1.6f, .64f to 1f, 1f to 1f)
        Tap(
            Modifier.graphicsLayer {
                translationY = seg(t, 0f to 18f, .14f to 18f, .28f to -18f, .34f to -24f, .56f to -24f, .57f to 18f, .64f to 18f, .78f to -18f, .84f to -24f, 1f to -24f) * density
                scaleX = fs
                scaleY = fs
                alpha = seg(t, 0f to 0f, .06f to 0f, .14f to 1f, .28f to 1f, .34f to 0f, .56f to 0f, .57f to 0f, .64f to 1f, .78f to 1f, .84f to 0f, 1f to 0f)
            }
        )
    }
}

/** Reels: Repost is pressed (red, the arrows turn, a cross pops in), then the card and its buttons scroll up and the next one rises in */
@Composable
private fun ReelsDemo() {
    val t by rememberInfiniteTransition(label = "rl").animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "rl")
    val turn = seg(t, 0f to 0f, .22f to 0f, .34f to 1f, 1f to 1f)
    val x = seg(t, 0f to 0f, .27f to 0f, .35f to 1f, 1f to 1f)
    val s = androidx.compose.animation.core.FastOutSlowInEasing.transform(seg(t, 0f to 0f, .42f to 0f, .70f to 1f, 1f to 1f))
    Box(Modifier.height(104.dp).fillMaxWidth().clipToBounds()) {
        val fs = seg(t, 0f to 1.6f, .10f to 1.6f, .20f to 1f, 1f to 1f)
        ReelSlide(Modifier.graphicsLayer { translationY = -s * size.height }, turn, x) {
            Tap(
                Modifier.graphicsLayer {
                    scaleX = fs * .8f
                    scaleY = fs * .8f
                    alpha = seg(t, 0f to 0f, .10f to 0f, .20f to 1f, .28f to 1f, .36f to 0f, 1f to 0f)
                }
            )
        }
        ReelSlide(Modifier.graphicsLayer { translationY = (1 - s) * size.height }, 0f, 0f)
    }
}

@Composable
private fun ReelSlide(m: Modifier, turn: Float, x: Float, tap: @Composable () -> Unit = {}) {
    Box(m.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Mini()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Ic.HEART, 13.dp, P.text)
                Box(contentAlignment = Alignment.Center) {
                    Icon(Ic.REPOST, 15.dp, if (turn > 0f) HEART else P.text, modifier = Modifier.graphicsLayer { rotationZ = turn * 180f })
                    if (x > 0f) {
                        Icon(
                            Ic.CROSS,
                            15.dp,
                            HEART,
                            2.6f,
                            modifier = Modifier.graphicsLayer {
                                scaleX = x
                                scaleY = x
                            }
                        )
                    }
                    tap()
                }
            }
        }
    }
}

@Composable
private fun RevealDemo(k: String) {
    val t by rememberInfiniteTransition(label = "r").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "r")
    val p = ((t - .5f) / .25f).coerceIn(0f, 1f)
    Box(Modifier.height(99.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (k) {
            "pop" -> {
                val s = if (p < .65f) .8f + p / .65f * .21f else 1.01f - (p - .65f) / .35f * .01f
                Mini(
                    Modifier.graphicsLayer {
                        scaleX = s * .8f
                        scaleY = s * .8f
                        alpha = if (t < .5f) 1f else minOf(1f, p / .65f)
                    }
                )
            }
            "up" -> Mini(
                Modifier.graphicsLayer {
                    scaleX = .8f
                    scaleY = .8f
                    translationY = if (t < .5f) 0f else (1 - androidx.compose.animation.core.FastOutSlowInEasing.transform(p)) * 99 * density
                }
            )
            else -> Mini(
                Modifier.graphicsLayer {
                    scaleX = .8f
                    scaleY = .8f
                }
            )
        }
    }
}

@Composable
private fun DecksSec(rs: Rs) {
    /* every deck imported, oldest first (like Your playlists): name, counts and size; × deletes one */
    if (rs.deck.parts.isEmpty() && rs.deck.broken.isEmpty()) Txt("No decks yet. Add an Anki deck (.apkg) from a link or a file.", size = 14.sp, color = P.faint, modifier = Modifier.padding(vertical = 8.dp))
    rs.deck.parts.forEachIndexed { i, p ->
        Row(Modifier.fillMaxWidth().then(if (i > 0) Modifier.dashedBottom(P.divider, top = true) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Ic.CARDS, 22.dp, P.text, 1.8f)
            Column(Modifier.weight(1f)) {
                Txt(p.name, size = 14.4.sp, bold = true, lines = 1)
                Txt((p.meta?.let { "%,d cards · %,d notes · ".format(it.cards, it.notes) } ?: "") + "%.1f MB".format(p.bytes / 1048576.0), size = 12.sp, color = P.faint)
            }
            Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(enabled = !rs.busy) { rs.deleteDeck(p.id, p.name) }, contentAlignment = Alignment.Center) { Txt("×", size = 20.sp, color = P.faint) }
        }
    }
    /* decks that couldn't be read (a damaged deck.db): only deleting is possible */
    rs.deck.broken.forEachIndexed { i, id ->
        Row(Modifier.fillMaxWidth().then(if (i > 0 || rs.deck.parts.isNotEmpty()) Modifier.dashedBottom(P.divider, top = true) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Ic.CARDS, 22.dp, P.faint, 1.8f)
            Column(Modifier.weight(1f)) {
                Txt("Unreadable deck", size = 14.4.sp, bold = true, lines = 1, color = P.faint)
                Txt("This deck couldn’t be opened. Delete it and add it again.", size = 12.sp, color = P.faint)
            }
            Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(enabled = !rs.busy) { rs.deleteDeck(id, "the unreadable deck") }, contentAlignment = Alignment.Center) { Txt("×", size = 20.sp, color = P.faint) }
        }
    }
    if (rs.busy && rs.dkMsg.isNotEmpty()) Txt(rs.dkMsg, size = 13.sp, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    Btn("Add a deck", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), p = true, h = 38.dp, size = 14.4.sp, enabled = !rs.busy) { rs.deckPick() }
    Txt("Decks are stored inside the app, and Android won’t clear them on its own.", size = 12.8.sp, color = P.faint, modifier = Modifier.padding(bottom = 8.dp))
}

/** Add a deck: paste a link to an .apkg, or choose one from your files */
@Composable
fun AddDeck(rs: Rs) {
    var link by remember { mutableStateOf("") }
    val close = { rs.addDeck = false }
    androidx.compose.ui.window.Dialog(close, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(P.card).border(1.dp, P.border, RoundedCornerShape(8.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Txt("Add a deck", bold = true)
            Field(link, { link = it }, "Paste a link to an .apkg", Modifier.fillMaxWidth(), androidx.compose.ui.text.input.ImeAction.Go) {
                if (link.isNotBlank()) {
                    close()
                    rs.importUrl(link)
                }
            }
            Txt("A direct download link, or a Google Drive or Dropbox share link. Each deck you add sits next to the others.", size = 12.8.sp, color = P.faint)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("Choose a file", h = 36.dp, size = 14.4.sp, pad = 16.dp) {
                    close()
                    rs.deckFile()
                }
                Box(Modifier.weight(1f))
                Btn("Cancel", h = 36.dp, size = 14.4.sp, pad = 16.dp, onClick = close)
                Btn("Import", p = true, h = 36.dp, size = 14.4.sp, pad = 16.dp, enabled = link.isNotBlank()) {
                    close()
                    rs.importUrl(link)
                }
            }
        }
    }
}

@Composable
private fun ProgressSec(rs: Rs) {
    Row2("Back up progress")
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Export", Modifier.weight(1f), h = 37.dp, size = 14.4.sp) { rs.exportProgress() }
        Btn("Import", Modifier.weight(1f), h = 37.dp, size = 14.4.sp) { rs.importProgress() }
    }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (done) {
            delay(1500)
            done = false
        }
    }
    Row2("Reset all progress") {
        Btn(if (done) "Done" else "Reset", danger = !done, enabled = !done, h = 37.dp, size = 14.4.sp, pad = 18.dp) {
            rs.dialog = pizza.kulhad.medslave.Dlg("Reset your progress in every deck? Each deck starts again from its first card.", "Reset", cancel = true, danger = true) {
                rs.resetProgress()
                done = true
            }
        }
    }
    Gap(8.dp)
}

/* ---------------------------------------------------------------- progress (#ip): a ring and three numbers */
@Composable
fun ProgressPanel(rs: Rs) {
    val s = rs.S ?: return run { rs.progressOpen = false }
    // with a feed filter on in reels, only the cards it lets through count
    val f = rs.rf
    val tot = if (f == null) s.q.size else s.q.count { f(it.n) }
    val done = when {
        rs.cur == null -> tot
        f == null -> s.i
        else -> (0 until minOf(s.i, s.q.size)).count { f(s.q[it].n) }
    }
    val p = if (tot > 0) done.toFloat() / tot else 0f
    val a = remember { Animatable(0f) }
    LaunchedEffect(p) { a.animateTo(p, tween(600, easing = pizza.kulhad.medslave.EASE)) }
    // the deck being studied, its parent decks above it: key "<id>/A › B" puts the deck's name · A above and B in the middle; a deck's
    // own star key is its All subjects; the bare star key is All decks
    val part = rs.deck.parts.find { s.key.startsWith(it.id + "/") }
    val segs = s.key.removePrefix(part?.let { it.id + "/" } ?: "").let { if (it == "*") emptyList() else it.split(" › ") }
    val parents = listOfNotNull(part?.name) + segs.dropLast(1)
    val current = segs.lastOrNull() ?: if (part != null) "All subjects" else s.name
    val hi = P.hi
    Popup("Progress", { rs.progressOpen = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (parents.isNotEmpty()) Txt(parents.joinToString(" · "), size = 13.6.sp, color = P.faint, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Txt(current, size = 19.sp, bold = true, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(152.dp), contentAlignment = Alignment.Center) {
                val bg = P.border
                val fg = hi
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width * 10 / 120
                    val r = size.width * 52 / 120
                    val tl = Offset(center.x - r, center.y - r)
                    drawArc(bg, 0f, 360f, false, tl, Size(2 * r, 2 * r), style = Stroke(w))
                    if (a.value > 0) drawArc(fg, -90f, 360f * a.value, false, tl, Size(2 * r, 2 * r), style = Stroke(w, cap = StrokeCap.Round))
                }
                Txt("${Math.round(p * 100)}%", size = 30.sp, bold = true, color = hi)
            }
            rs.rfName?.let { Txt(it, size = 19.sp, bold = true, color = hi, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf(tot to "Total", done to "Done", (tot - done) to "Remaining").forEach { (n, l) ->
                    Column {
                        Txt("$n", size = 24.sp, bold = true)
                        Txt(l, size = 12.8.sp, color = P.faint)
                    }
                }
            }
            val note = androidx.compose.ui.text.buildAnnotatedString {
                val acc = androidx.compose.ui.text.SpanStyle(color = P.text)
                append("You can change the current deck in feed by either ")
                withStyle(acc) { append("changing your feed filter (long press the home button)") }
                append(" or ")
                withStyle(acc) { append("select a deck in the deck picker") }
                append(".")
            }
            androidx.compose.foundation.text.BasicText(
                note,
                Modifier.fillMaxWidth().padding(top = 14.dp),
                style = androidx.compose.ui.text.TextStyle(color = P.faint, fontSize = 12.8.sp, fontFamily = Carlito, textAlign = TextAlign.Center, lineHeight = 17.sp)
            )
        }
    }
}

/* ---------------------------------------------------------------- the first-visit tutorial (#tut): a looping demo of the swipes */
@Composable
fun Tutorial(rs: Rs) {
    val f = rs.set.flip
    val reel = rs.set.reels
    val close = { rs.tutorial = false }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(remember { MutableInteractionSource() }, null, onClick = close).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 336.dp).fillMaxWidth().shadow(20.dp, RoundedCornerShape(8.dp)).border(1.dp, P.border, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                .background(P.card).padding(horizontal = 19.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TutDemo(f, reel)
            Txt((if (f) "Swipe the card up" else "Swipe the card left or right") + " to show the answer.", align = TextAlign.Center, bold = true)
            Txt(
                if (reel) {
                    "Then swipe up if you knew it, or tap Repost if you didn’t. Swipe down to go back a card."
                } else if (f) {
                    "Then swipe up if you knew it, down if you didn’t."
                } else {
                    "Then swipe right if you knew it, left if you didn’t."
                },
                align = TextAlign.Center
            )
            Txt((if (reel) "" else "Or tap Next › if you knew it. ") + "Missed cards come back 20–40 cards later.", size = 13.6.sp, color = P.faint, align = TextAlign.Center)
            Btn("Got it", Modifier.padding(top = 4.dp), onClick = close)
        }
    }
}

private fun seg(t: Float, vararg k: Pair<Float, Float>): Float { // piecewise-linear keyframes (time 0–1 → value)
    for (i in 1 until k.size) {
        if (t <= k[i].first) {
            val (t0, v0) = k[i - 1]
            val (t1, v1) = k[i]
            return if (t1 == t0) v1 else v0 + (v1 - v0) * ((t - t0) / (t1 - t0))
        }
    }
    return k.last().second
}

@Composable
private fun TutDemo(f: Boolean, reel: Boolean) {
    val t by rememberInfiniteTransition(label = "tut").animateFloat(0f, 1f, infiniteRepeatable(tween(5600, easing = LinearEasing)), label = "tut")
    Box(Modifier.fillMaxWidth().height(136.dp), contentAlignment = Alignment.Center) {
        val ink = P.text
        if (!f) {
            /* tcs/tfs: the card swipes right (answer), comes back, swipes left */
            val cx = seg(t, 0f to 0f, .08f to 0f, .30f to 3.2f, .40f to 7f, .41f to 0f, .58f to 0f, .80f to -3.2f, .90f to -7f, .91f to 0f, 1f to 0f)
            val cr = seg(t, 0f to 0f, .08f to 0f, .30f to 9f, .40f to 18f, .41f to 0f, .58f to 0f, .80f to -9f, .90f to -18f, .91f to 0f, 1f to 0f)
            val ca = seg(t, 0f to 1f, .30f to 1f, .40f to 0f, .41f to 0f, .50f to 1f, .80f to 1f, .90f to 0f, .91f to 0f, 1f to 1f)
            val fx = seg(t, 0f to 0f, .08f to 0f, .30f to 3.2f, .38f to 4.6f, .50f to 4.6f, .51f to 0f, .58f to 0f, .80f to -3.2f, .88f to -4.6f, 1f to -4.6f)
            val fa = seg(t, 0f to 0f, .08f to 1f, .30f to 1f, .38f to 0f, .51f to 0f, .58f to 1f, .80f to 1f, .88f to 0f, 1f to 0f)
            TutCard(
                Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(.5f, 1.2f)
                    translationX = cx * 16 * density
                    rotationZ = cr
                    alpha = ca
                },
                ink
            )
            Finger(
                Modifier.graphicsLayer {
                    translationX = fx * 16 * density
                    translationY = 13 * density
                    alpha = fa
                },
                ink
            )
        } else {
            /* tcu/tfu: a swipe up flips the card (answer), a second one sends it up and away */
            val sy = seg(t, 0f to 1f, .14f to 1f, .21f to 0f, .28f to 1f, 1f to 1f)
            val cy = seg(t, 0f to 0f, .58f to 0f, .80f to -2.6f, .90f to -6f, .91f to 0f, 1f to 0f)
            val ca = seg(t, 0f to 1f, .80f to 1f, .90f to 0f, .91f to 0f, 1f to 1f)
            val fy = seg(t, 0f to 0f, .08f to 0f, .28f to -2.6f, .36f to -3.6f, .50f to -3.6f, .51f to 0f, .58f to 0f, .80f to -2.6f, .88f to -3.6f, 1f to -3.6f)
            val fa = seg(t, 0f to 0f, .08f to 1f, .28f to 1f, .36f to 0f, .51f to 0f, .58f to 1f, .80f to 1f, .88f to 0f, 1f to 0f)
            TutCard(
                Modifier.graphicsLayer {
                    scaleY = maxOf(.001f, sy)
                    translationY = cy * 16 * density
                    alpha = ca
                },
                ink
            )
            Finger(
                Modifier.graphicsLayer {
                    translationY = (fy * 16 + 27) * density
                    alpha = fa
                },
                ink
            )
            if (reel) {
                /* Repost being pressed while the answer rests */
                val press = seg(t, 0f to 0f, .34f to 0f, .38f to 1f, .42f to 1f, .46f to 0f, 1f to 0f)
                Column(Modifier.graphicsLayer { translationX = 82 * density }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Ic.REPOST, 29.dp, if (press > .5f) HEART else ink, modifier = Modifier.graphicsLayer { rotationZ = press * 180f })
                    Txt("Repost", size = 10.sp, bold = true)
                }
            }
        }
    }
}

@Composable private fun TutCard(m: Modifier, ink: Color) {
    Column(
        m.size(86.dp, 112.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, ink.copy(alpha = .35f), RoundedCornerShape(10.dp))
            .background(lerpC(P.card, ink, .06f)).padding(horizontal = 11.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        listOf(1f, .75f, .55f).forEach { Box(Modifier.fillMaxWidth(it).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = .25f))) }
    }
}

@Composable private fun Finger(m: Modifier, ink: Color) = Box(m.size(35.dp).clip(CircleShape).background(ink.copy(alpha = .3f)).border(2.dp, ink, CircleShape))
private fun lerpC(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)

/* ---------------------------------------------------------------- the import window (#imp): what each note type becomes, which field goes where */
private class TypeSel(val t: ImportType) {
    var k by mutableStateOf(t.kind)
    var bad by mutableStateOf(false)
    val m = mutableStateMapOf<String, List<Int>>().apply { t.plan.forEach { (k, v) -> put(k, v.toList()) } }
    fun slots(): List<Int> = m[k] ?: emptyList()
}

@Composable
fun ImportPanel(rs: Rs, types: List<ImportType>) {
    val sel = remember(types) { types.map { TypeSel(it) } }
    var st by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    Popup({ Txt("Import deck", bold = true) }, { rs.importCancel() }, maxW = 544.dp, maxH = .88f, footer = {
        Row(Modifier.fillMaxWidth().dashedBottom(P.divider, top = true).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(st, size = 13.6.sp, color = P.faint, modifier = Modifier.weight(1f))
            Btn("Import", p = true, h = 38.dp, pad = 22.dp) {
                var bad = false
                var any = false
                sel.forEach { s ->
                    val miss = s.k.isNotEmpty() && ISL[s.k]!!.withIndex().any { (i, l) -> l.endsWith("*") && s.slots()[i] < 0 }
                    s.bad = miss
                    if (miss) bad = true
                    if (s.k.isNotEmpty()) any = true
                }
                if (bad) {
                    st = "Choose the fields marked *"
                    return@Btn
                }
                if (!any) {
                    st = "Every note type is skipped"
                    return@Btn
                }
                rs.importGo(sel.associate { s -> s.t.id to Choice(s.k, s.slots().toIntArray()) })
            }
        }
    }) {
        Column(Modifier.weight(1f, false).verticalScroll(scroll).padding(horizontal = 16.dp)) {
            Txt("Choose what each note type becomes and which field goes where. Fields marked * are needed.", size = 14.4.sp, color = P.faint, modifier = Modifier.padding(top = 8.dp, bottom = 3.dp))
            sel.forEachIndexed { i, s ->
                val t = s.t
                Column(Modifier.fillMaxWidth().then(if (i < sel.size - 1) Modifier.dashedBottom(P.divider) else Modifier).padding(vertical = 13.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Txt(t.name, bold = true, modifier = Modifier.weight(1f))
                        Txt("%,d note%s".format(t.n, if (t.n == 1) "" else "s"), size = 14.sp, color = P.faint)
                    }
                    val kinds = IKS.filter { (k, _) -> k != "e" || t.kind == "e" }.toList() + ("" to "Skip")
                    Slot("Card type", true, false, kinds.map { it.second }, kinds.indexOfFirst { it.first == s.k }, null, false) {
                        s.k = kinds[it].first
                        s.bad = false
                    }
                    if (s.k.isNotEmpty()) {
                        ISL[s.k]!!.forEachIndexed { si, l ->
                            val req = l.endsWith("*")
                            val v = s.slots().getOrElse(si) { -1 }
                            val opts = listOf("None") + t.fields
                            Slot(l.removeSuffix("*"), false, req, opts, v + 1, if (v < 0) null else t.sample.getOrElse(v) { "" }, s.bad && req && v < 0) { j ->
                                s.m[s.k] = s.slots().toMutableList().also { it[si] = j - 1 }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** one row of the import window: a label and a select; under it a sample of the chosen field */
@Composable
private fun Slot(label: String, strong: Boolean, req: Boolean, opts: List<String>, sel: Int, sample: String?, bad: Boolean, pick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.width(112.dp).height(37.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(label, size = 14.7.sp, color = if (strong) P.text else P.faint, bold = strong, lines = 2)
            if (req) Txt("*", size = 14.7.sp, color = RED)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                Modifier.fillMaxWidth().height(37.dp).border(1.dp, if (bad) RED else P.border, RoundedCornerShape(8.dp)).press(bg = P.card, shape = RoundedCornerShape(8.dp)) { open = true }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(opts.getOrElse(sel) { "" }, size = 14.4.sp, modifier = Modifier.weight(1f), lines = 1)
                Icon(Ic.DOWN, 14.dp, P.faint, 2.4f)
            }
            if (sample != null) Txt(sample.ifEmpty { "Empty in the first notes" }, size = 12.8.sp, color = P.faint.copy(alpha = if (sample.isEmpty()) .7f else 1f), italic = sample.isEmpty(), lines = 1, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
    if (open) {
        Dialog({ open = false }) {
            Column(
                Modifier.widthIn(max = 360.dp).heightIn(max = (LocalConfiguration.current.screenHeightDp * .7f).dp).clip(RoundedCornerShape(8.dp)).background(P.card)
                    .border(1.dp, P.border, RoundedCornerShape(8.dp)).verticalScroll(rememberScrollState()).padding(6.dp)
            ) {
                opts.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().press(bg = if (i == sel) P.press else null) {
                            open = false
                            pick(i)
                        }.padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        Txt(o, bold = i == sel)
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- importing: the app's logo turning, how far along it is and what
   it's doing, while the deck is read and saved (the field window in between has its own screen) */
@Composable
fun ImportProgress(rs: Rs) {
    val pct = rs.impPct
    val shown by androidx.compose.animation.core.animateFloatAsState(pct ?: 0f, tween(400), label = "imp")
    Box(Modifier.fillMaxSize().background(P.bg.copy(alpha = .96f)).clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spinner(76.dp)
            Gap(4.dp)
            Txt(if (pct != null) "${shown.toInt()}%" else "Importing", size = 30.sp, bold = true)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.border)) {
                if (pct != null) Box(Modifier.fillMaxWidth(shown / 100f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.text))
            }
            Txt(rs.impStep, size = 14.4.sp, color = P.faint, align = TextAlign.Center, lines = 2)
            Gap(6.dp)
            Btn("Cancel", h = 36.dp, size = 14.4.sp, pad = 18.dp) { rs.importCancel() }
        }
    }
}

/* ---------------------------------------------------------------- an update from GitHub: Later / Update, then the download's bar */
@Composable
fun UpdateBox(rs: Rs) {
    val u = rs.updater
    val o = u.offer ?: return
    val busy = u.progress >= 0f
    val shown by androidx.compose.animation.core.animateFloatAsState(u.progress.coerceAtLeast(0f), tween(200), label = "upd")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(remember { MutableInteractionSource() }, null) { u.later() }.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().shadow(20.dp, RoundedCornerShape(8.dp)).border(1.dp, P.border, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                .background(P.card).clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Txt("Update available", bold = true, size = 17.sp)
            Txt("A new update has been detected. Please update to the latest version for bug fixes and changes.", lh = 21.sp)
            if (busy) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.border)) {
                        Box(Modifier.fillMaxWidth(shown).height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.text))
                    }
                    Txt("${(shown * 100).toInt()}%  ·  ${u.status}", size = 13.6.sp, color = P.faint)
                }
            } else if (u.status.isNotEmpty()) {
                Txt(u.status, size = 13.6.sp, color = P.faint)
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Later", enabled = !busy, h = 37.dp, size = 15.sp, pad = 16.dp) { u.later() }
                Btn("Update", p = true, enabled = !busy, h = 37.dp, size = 15.sp, pad = 16.dp) { u.update() }
            }
        }
    }
}

/* ---------------------------------------------------------------- alerts and confirms */
@Composable
fun DialogBox(rs: Rs, d: pizza.kulhad.medslave.Dlg) {
    val close = { rs.dialog = null }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(remember { MutableInteractionSource() }, null) { if (d.cancel) close() }.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().shadow(20.dp, RoundedCornerShape(8.dp)).border(1.dp, P.border, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                .background(P.card).clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Txt(d.text, lh = 21.sp)
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (d.cancel) Btn("Cancel", h = 37.dp, size = 15.sp, pad = 16.dp, onClick = close)
                Btn(d.ok, p = !d.danger, danger = d.danger, h = 37.dp, size = 15.sp, pad = 16.dp) {
                    close()
                    d.onOk()
                }
            }
        }
    }
}
