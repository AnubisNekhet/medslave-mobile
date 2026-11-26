package pizza.kulhad.medslave.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import pizza.kulhad.medslave.Rs
import kotlin.math.abs
import kotlin.math.sign

private class Taps { var t = 0L; var x = 0f; var y = 0f }

@Composable
fun Study(rs: Rs) {
    val d = LocalDensity.current.density
    val reelsOn = rs.set.reels && rs.cur != null
    val taps = remember { Taps() }
    Column(Modifier.fillMaxSize().background(P.bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .onGloballyPositioned { rs.stageRect = it.boundsInWindow(); rs.stageH = it.size.height.toFloat() }
            .pointerInput(rs.set.flip) { cardGestures(rs, d, taps) }) {
            /* Reels: a card too tall to sit above the account row ends just above it and scrolls there (capReel) */
            val capPx = if (reelsOn && rs.reels.whoTop > 0 && (rs.web.contentH + 16) * d > rs.reels.whoTop - 6 * d) rs.stageH - rs.reels.whoTop + 6 * d else 0f
            LaunchedEffect(capPx > 0) { rs.web.long(capPx > 0) }
            /* the card view itself never changes size (resizing it as a new card slid in drew it in pieces): the page keeps the room
               under the account row free instead (--room) */
            val room = if (reelsOn && rs.reels.whoTop > 0) (rs.stageH - rs.reels.whoTop + 6 * d) / d else 0f
            LaunchedEffect(room) { rs.web.room(room) }
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = rs.tx.value; translationY = rs.ty.value; rotationZ = rs.rot.value; alpha = rs.alpha.value
                scaleX = rs.scale.value; scaleY = rs.scale.value
            }) {
                AndroidView(factory = { rs.web.detach(); rs.web.view }, modifier = Modifier.fillMaxSize())
            }
            if (reelsOn) ReelOverlay(rs)
            rs.ghost?.let { g -> Image(g.img, null, Modifier.fillMaxWidth().height((g.img.height / d).dp).graphicsLayer { translationY = g.top + g.y.value }, contentScale = ContentScale.FillBounds) }
            if (reelsOn) Bursts(rs)
            rs.fin?.let { FinCard(rs, it) }
        }
        if (rs.fin == null) Footer(rs, reelsOn)
    }
}

/* Tinder: a sideways drag moves the card (the answer side tints green/red), released far or fast enough it flies off.
   Flip: a swipe up reveals / means knew it (once the card's end is in view), down means missed it (from the card's top).
   Reels: a double tap likes the card. The WebView keeps scrolling and taps; a Tinder drag takes the touch from it. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.cardGestures(rs: Rs, d: Float, taps: Taps) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (rs.cur == null) return@awaitEachGesture
    val flip = rs.set.flip; val x0 = down.position.x; val y0 = down.position.y; val t0 = down.uptimeMillis
    var bot = rs.web.atBottom(); var top = rs.web.atTop(); rs.web.exUp = false; rs.web.exDown = false
    /* not while an explanation card under the finger can still scroll that way */
    fun canBot() = bot && !rs.web.exDown
    fun canTop() = top && !rs.web.exUp
    val tinder = !flip && !rs.leaving
    var drag = false; var dx = 0f; var dy = 0f; var multi = false; var lastGlow = 0f; var tUp = t0
    /* Reels: an answer card follows the finger (with its overlay) instead of waiting for the swipe to end, as reels do */
    val follow = flip && rs.set.reels && rs.stage == 1 && !rs.leaving; var rdrag = false
    fun glow(v: Float) { if (!rs.set.reels && abs(v - lastGlow) > .015f) { lastGlow = v; rs.web.glow(v) } }
    while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        if (ev.changes.count { it.pressed } > 1) { multi = true; bot = false; top = false }
        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
        dx = (c.position.x - x0) / d; dy = (c.position.y - y0) / d
        if (!c.pressed) { tUp = c.uptimeMillis; break }
        if (follow && !multi) {
            if (!rdrag && abs(dy) > 10 && abs(dy) > abs(dx) * 1.2f && (dy < 0 && canBot() || dy > 0 && canTop())) { rdrag = true; rs.preCapture() }
            if (rdrag) { c.consume()   /* the reel moves, not the card's page under it */
                val py = (if (dy < 0 && !bot || dy > 0 && !top) 0f else dy) * d; rs.scope.launch { rs.ty.snapTo(py); rs.oy.snapTo(py) } }
        }
        if (flip) {
            if (rs.stage == 1 && (bot || top)) glow(if (abs(dy) > 10 && abs(dy) > abs(dx)) (if (dy < 0 && bot) minOf(1f, -dy / 160) else if (dy > 0 && top) -minOf(1f, dy / 160) else 0f) else 0f)
        } else if (tinder) {
            if (!drag && abs(dx) > 10 && abs(dx) > abs(dy) * 1.2f) drag = true
            if (drag) {
                c.consume(); val px = dx * d; val py = dy * d * .2f; val r = dx / 20
                rs.scope.launch { rs.tx.snapTo(px); rs.ty.snapTo(py); rs.rot.snapTo(r) }
                if (rs.stage == 1) glow(dx / 160)
            }
        }
    }
    if (rdrag) {
        /* let go far or fast enough: it carries on (from where it is) and the next card comes in behind it; otherwise it springs back */
        val v = abs(dy) / (tUp - t0 + 1)
        if (!multi && (abs(dy) > 90 || v > .5f)) { rs.dragOff = rs.ty.value; if (dy < 0) rs.advance(true) else rs.advance(true, false) }
        else rs.scope.launch { val t = tween<Float>(260, easing = pizza.kulhad.medslave.EASE_OUTISH); launch { rs.oy.animateTo(0f, t) }; rs.ty.animateTo(0f, t) }
    } else if (flip) {
        if (!multi && abs(dy) > 50 && abs(dy) > abs(dx) * 1.2f) { if (dy < 0 && canBot()) rs.advance(true) else if (dy > 0 && canTop() && rs.stage == 1) rs.advance(true, false) }
        if (!rs.leaving && lastGlow != 0f) rs.web.glow(0f)
    } else if (drag) {
        val v = abs(dx) / (tUp - t0 + 1)
        if (abs(dx) > 100 || v > .6f) rs.fly(sign(dx).toInt(), dy * d)
        else { rs.scope.launch { val t = tween<Float>(250); launch { rs.tx.animateTo(0f, t) }; launch { rs.ty.animateTo(0f, t) }; rs.rot.animateTo(0f, t) }; if (lastGlow != 0f) rs.web.glow(0f) }
    }
    /* Reels double tap: two quick taps close together, not on the overlay's buttons */
    if (rs.set.reels && !drag && !multi && abs(dx) < 12 && abs(dy) < 12 && tUp - t0 < 350) {
        val wx = rs.stageRect.left + down.position.x; val wy = rs.stageRect.top + down.position.y
        if (rs.reels.excl.any { it.contains(Offset(wx, wy)) }) { taps.t = 0 }
        else if (tUp - taps.t < 320 && abs(down.position.x - taps.x) < 40 * d && abs(down.position.y - taps.y) < 40 * d) { taps.t = 0; rs.reels.burst(down.position.x, down.position.y) }
        else { taps.t = tUp; taps.x = down.position.x; taps.y = down.position.y }
    }
}

private fun hint(rs: Rs): String {
    val f = rs.set.flip
    return if (rs.stage == 1) (if (f) "↑ knew it · ↓ " + (if (rs.set.reels) "or Repost: " else "") + "again" else "→ knew it · ← again")
    else (if (rs.cur?.again == true) "Again · " else "") + (if (f) "Swipe up " else "Swipe ") + "to reveal the answer"
}

@Composable
private fun Footer(rs: Rs, reels: Boolean) {
    if (reels) {
        /* Reels: three buttons, like the home bar: menu, home (the feed), stats */
        Row(Modifier.fillMaxWidth().solidLine(P.divider, true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            IconBtn(Ic.LIST, Modifier.size(51.dp, 42.dp), size = 24.dp, stroke = 2f, plain = true) { rs.home(); rs.tab = "menu" }
            IconBtn(Ic.HOME, Modifier.size(51.dp, 42.dp), size = 24.dp, plain = true) { rs.home(); rs.tab = "feed" }
            IconBtn(Ic.PIE, Modifier.size(51.dp, 42.dp), size = 24.dp, plain = true) { rs.progressOpen = true }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(if (rs.cur != null) hint(rs) else "", size = 13.6.sp, color = P.faint, lines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.HOME, Modifier.size(42.dp, 38.dp)) { rs.home() }
            IconBtn(Ic.PIE, Modifier.size(42.dp, 38.dp)) { rs.progressOpen = true }
            val cl = rs.stage == 0 && rs.web.seqLeft > rs.web.seqN
            val s = RoundedCornerShape(8.dp)
            Box(Modifier.height(38.dp).border(1.dp, P.border, s).press(bg = P.card, shape = s) { rs.advance() }.padding(horizontal = 25.dp), contentAlignment = Alignment.Center) {
                Txt("Next ›", bold = true, modifier = Modifier.alpha(if (cl) 0f else 1f))
                Txt("Next Cloze ›", bold = true, modifier = Modifier.alpha(if (cl) 1f else 0f))
            }
        }
    }
}

/** the end screen: a card saying the deck is done, with Home and Redo deck */
@Composable
private fun FinCard(rs: Rs, f: pizza.kulhad.medslave.Fin) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 13.dp, vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().shadow(7.dp, RoundedCornerShape(8.dp)).border(1.dp, P.border, RoundedCornerShape(8.dp))
            .background(P.card, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("Deck finished", size = 22.sp, bold = true, align = TextAlign.Center)
            Gap(10.dp)
            Txt("You’ve been through all ${f.count} cards in ${f.name}" + (if (f.wr > 0) ", and went back over ${f.wr} you missed" else "") + ".", align = TextAlign.Center, lh = 22.sp)
            Gap(20.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Btn("Home", h = 42.dp) { rs.home() }
                Btn("Redo deck", p = true, h = 42.dp) { rs.redo() }
            }
        }
    }
}
