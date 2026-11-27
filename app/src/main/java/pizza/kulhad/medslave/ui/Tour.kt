package pizza.kulhad.medslave.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pizza.kulhad.medslave.Rs

/* ---------------------------------------------------------------- the app tour (instruction.md): the first time the Menu, and in Reels Mode
   the feed, show with a deck loaded, a coach mark walks through their controls. the screen dims, a spotlight cuts out round one control, a
   finger circle shows the gesture (tap, hold, swipe) and one line says what it does. nothing behind it ever opens: every touch goes to the
   tour (any tap = next). parts: "menu", "feed"; seen ones kept in medslave:tour; Settings › Reviewing › Show the tutorial again resets them */

/** a step: the target's key (Rs.tourRects), its line, the gesture ('t' tap, 'h' hold, 's' swipe), and whether its spotlight is round */
class TStep(val key: String, val text: String, val g: Char = 't', val round: Boolean = false)

/** marks a control the tour can point at: its bounds in the root, kept while it's on screen */
fun Modifier.tourTarget(rs: Rs, key: String): Modifier = composed {
    DisposableEffect(key) { onDispose { rs.tourRects.remove(key) } }
    onGloballyPositioned { rs.tourRects[key] = it.boundsInRoot() }
}

/** a part's steps, without the ones whose control isn't there (one deck: no pager; no Reels Mode: no bar) */
private fun tourSteps(rs: Rs, part: String): List<TStep> {
    val reels = rs.set.reels
    val l = if (part == "menu") {
        val p = rs.deck.parts.firstOrNull()
        val kids = p?.tree?.subjects?.firstOrNull()?.let { s -> p.tree.top.find { it.name == s.name }?.kids?.isNotEmpty() } == true
        listOf(
            TStep("menu.logo", "Tap the logo for the credits: a pie of every deck’s subjects."),
            TStep("menu.pager", "Swipe sideways for your other decks.", 's'),
            TStep("menu.all", if (rs.deck.parts.size > 1) "Study every card in this deck, or in all of them." else "Study every card in your deck."),
            TStep("menu.subject", "Tap a subject to start it" + (if (reels) " as reels" else "") + "." + (if (kids) " ⌄ picks a subdeck." else "")),
            TStep("menu.gear", "Settings: decks, progress, feed filters and how things look.", round = true),
            TStep("bar.home", "Home is your feed.", round = true),
            TStep("bar.reels", "Reels picks up where you left off.", round = true)
        )
    } else {
        listOf(
            TStep("feed.arch", "Your saved cards. Tap the bookmark on any post to save it.", round = true),
            TStep("feed.story", "Stories: a few cards from each subject. Tap an account to open its profile.", round = true),
            TStep("feed.heat", "Your daily cards. Tap a day for its count, and keep the streak going."),
            TStep("feed.more", "⋯ › Hide keeps a card out of your feed and reels; Block hides everything that account posts.", round = true),
            TStep("feed.acc", "Tap an account for its profile. Follow it for the Following filter."),
            TStep("bar.home", "Hold Home to switch feed filters.", 'h', round = true)
        )
    }
    return l.filter { it.key in rs.tourRects }
}

/** piecewise-linear keyframes (time 0–1 → value) */
private fun kf(t: Float, vararg k: Pair<Float, Float>): Float {
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
fun Tour(rs: Rs) {
    val part = rs.tour ?: return
    val steps = remember(part) { tourSteps(rs, part) }
    if (steps.isEmpty()) {
        LaunchedEffect(part) { rs.tourEnd() }
        return
    }
    var i by remember(part) { mutableIntStateOf(0) }
    val st = steps[i.coerceAtMost(steps.lastIndex)]
    val last = i >= steps.lastIndex
    val next = { if (last) rs.tourEnd() else i++ }
    val r = rs.tourRects[st.key] ?: Rect.Zero
    val rm = remember { rs.rm() }
    val tt by rememberInfiniteTransition(label = "tour").animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "t")
    val t = if (rm) .5f else tt
    LaunchedEffect(part, i) {
        if (st.g == 'h') {
            kotlinx.coroutines.delay(1000)
            rs.reels.buzz()
        }
    } // the hold's buzz, once (nothing opens)
    androidx.activity.compose.BackHandler { rs.tourEnd() }
    val ink = P.text
    /* the overlay fades in quickly as the tour starts (straight away with animations off) */
    val shown = remember(part) { androidx.compose.animation.core.Animatable(if (rm) 1f else 0f) }
    LaunchedEffect(part) { shown.animateTo(1f, tween(220, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    /* every touch is the tour's: a tap anywhere goes on, drags do nothing, nothing underneath gets them */
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.pointerInput(part, i) {
            awaitEachGesture {
                val d = awaitFirstDown()
                d.consume()
                val up = waitForUpOrCancellation()
                if (up != null) {
                    up.consume()
                    next()
                }
            }
        }
    ) {
        val h = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val pad = 6.dp.toPx()
            drawRect(Color.Black.copy(alpha = .58f))
            val hole = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
            val rad = maxOf(hole.width, hole.height) / 2
            if (st.round) {
                drawCircle(Color.Black, rad, hole.center, blendMode = BlendMode.Clear)
                drawCircle(ink.copy(alpha = .55f), rad, hole.center, style = Stroke(1.5.dp.toPx()))
            } else {
                drawRoundRect(Color.Black, hole.topLeft, hole.size, CornerRadius(10.dp.toPx()), blendMode = BlendMode.Clear)
                drawRoundRect(ink.copy(alpha = .55f), hole.topLeft, hole.size, CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
            /* the finger: appears, does the gesture, fades */
            val f = 18.dp.toPx()
            val a = kf(t, 0f to 0f, .12f to 1f, .78f to 1f, .95f to 0f)
            val c = if (st.g == 's') Offset(kf(t, 0f to hole.right - hole.width * .15f, .18f to hole.right - hole.width * .15f, .72f to hole.left + hole.width * .15f, 1f to hole.left + hole.width * .15f), hole.center.y) else hole.center
            val sc = when (st.g) {
                't' -> kf(t, 0f to 1.25f, .15f to 1f, .3f to .82f, .45f to 1f)
                'h' -> kf(t, 0f to 1.25f, .15f to 1f, .25f to .85f, .78f to .85f, .9f to 1f)
                else -> kf(t, 0f to 1.2f, .15f to .9f, .72f to .9f, .85f to 1f)
            }
            if (st.g == 't' && t in .3f..0.8f) {
                val k = (t - .3f) / .5f
                drawCircle(ink.copy(alpha = .5f * (1 - k) * a), f * (1 + 1.4f * k), c, style = Stroke(2.dp.toPx()))
            } // the tap's ripple
            drawCircle(ink.copy(alpha = .3f * a), f * sc, c)
            drawCircle(ink.copy(alpha = .9f * a), f * sc, c, style = Stroke(2.dp.toPx()))
            if (st.g == 'h') {
                val k = kf(t, 0f to 0f, .25f to 0f, .75f to 1f, 1f to 1f) // the hold: a ring fills round the finger
                if (k > 0) drawArc(ink.copy(alpha = a), -90f, 360f * k, false, Offset(c.x - f * 1.6f, c.y - f * 1.6f), androidx.compose.ui.geometry.Size(f * 3.2f, f * 3.2f), style = Stroke(3.dp.toPx()))
            }
        }
        /* the line, the step dots, Skip and Next: at the bottom when the control is in the top half, at the top otherwise */
        val top = r.center.y > h / 2
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp, vertical = if (top) 18.dp else 92.dp), contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter) {
            val sh = RoundedCornerShape(8.dp)
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(sh).background(P.card).border(1.dp, P.border, sh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt(st.text, size = 15.sp, lh = 21.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        steps.indices.forEach { k -> Box(Modifier.size(if (k == i) 7.dp else 6.dp).clip(CircleShape).background(if (k == i) P.text else P.border)) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!last) Btn("Skip", h = 34.dp, size = 13.6.sp, pad = 14.dp) { rs.tourEnd() }
                    Btn(if (last) "Done" else "Next", p = true, h = 34.dp, size = 13.6.sp, pad = 16.dp) { next() }
                }
            }
        }
    }
}
