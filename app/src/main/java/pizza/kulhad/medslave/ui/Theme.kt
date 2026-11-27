package pizza.kulhad.medslave.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pizza.kulhad.medslave.R

/* colours from style.css: the default theme's light/dark .card variables and the themes block (html[data-theme]) */
class Pal(
    val bg: Color,
    val card: Color,
    val text: Color,
    val faint: Color,
    val border: Color,
    val divider: Color,
    val press: Color,
    val accent: Color,
    val link: Color,
    val red: Color,
    val green: Color,
    val dark: Boolean,
    val hi: Color = text,
    val onHi: Color = card
)

/* hi: the fill of full-contrast buttons and a switch that's on; onHi: what sits on it. the chosen accent (Settings › Appearance) or,
   without one, the text colour as before */
fun Pal.accented(a: Color?): Pal {
    if (a == null) return this
    fun l(c: Color) = c.luminance()
    fun cr(x: Color, y: Color) = (maxOf(l(x), l(y)) + .05f) / (minOf(l(x), l(y)) + .05f)
    val on = if (dark) {
        card
    } else if (cr(a, card) >= cr(a, text)) {
        card
    } else {
        text // dark mode: always the dark card colour on the accent
    }
    return Pal(bg, card, text, faint, border, divider, press, accent, link, red, green, dark, a, on)
}

/** the accent in use: the chosen one, or by default the theme's blue (Dracula's cyan, Material You's main accent) */
fun accentOf(theme: String, i: Int, dark: Boolean): Color? {
    val l = accents(theme)
    if (l.isEmpty()) return null
    val k = if (i in l.indices) {
        i
    } else {
        when (theme) {
            "tokyo" -> 6
            "nord" -> 6
            "dracula" -> 4
            "you" -> 0
            else -> 11
        }.coerceIn(l.indices)
    }
    return l[k].let { if (dark) it.second else it.first }
}

/** each theme's own accent colours, [light, dark] pairs (Settings › Appearance: the circles under Theme) */
fun accents(theme: String): List<Pair<Color, Color>> = when (theme) {
    "tokyo" -> listOf("#f52a65" to "#f7768e", "#b15c00" to "#ff9e64", "#8c6c3e" to "#e0af68", "#587539" to "#9ece6a", "#118c74" to "#73daca", "#007197" to "#7dcfff", "#2e7de9" to "#7aa2f7", "#9854f1" to "#bb9af7").map { c(it.first) to c(it.second) }
    "nord" -> listOf("#bf616a", "#d08770", "#ebcb8b", "#a3be8c", "#b48ead", "#8fbcbb", "#88c0d0", "#81a1c1", "#5e81ac").map { c(it) to c(it) }
    "dracula" -> listOf("#cb3a2a" to "#ff5555", "#a34d14" to "#ffb86c", "#846e15" to "#f1fa8c", "#14710a" to "#50fa7b", "#036a96" to "#8be9fd", "#644ac9" to "#bd93f9", "#a3144d" to "#ff79c6").map { c(it.first) to c(it.second) }
    "you" -> You.accents
    else -> DCOL // default and Catppuccin: Catppuccin's Latte / Mocha accents
}

private fun c(s: String): Color {
    val h = s.removePrefix("#")
    val v = h.toLong(16)
    return if (h.length == 8) Color(((v and 0xff) shl 24 or (v ushr 8)).toInt()) else Color((0xff000000 or v).toInt())
}
private fun mix(a: String, p: Float, b: String) = lerp(c(b), c(a), p)

/** Material You (Android 12+): the phone's wallpaper colours, read as the app starts. light and dark palettes for the app, and the same
 colours as card variables for the web pages (css, injected by Page.theme) */
object You {
    var light: Pal? = null
    var dark: Pal? = null
    var css = ""
    var accents = listOf<Pair<Color, Color>>()
    val ok get() = android.os.Build.VERSION.SDK_INT >= 31
    fun load(ctx: android.content.Context) {
        if (!ok) return
        val r = android.R.color::class.java
        fun k(n: String): Int = runCatching { ctx.getColor(r.getField(n).getInt(null)) }.getOrDefault(0xFF808080.toInt())
        fun hx(i: Int) = String.format("#%06x", i and 0xffffff)
        fun rgb(i: Int) = "${(i shr 16) and 255}, ${(i shr 8) and 255}, ${i and 255}"
        fun col(i: Int) = Color(i)
        val red = c("#d20f39")
        val green = c("#40a02b")
        val redD = c("#f38ba8")
        val greenD = c("#a6e3a1")
        light = Pal(
            col(k("system_neutral2_50")), col(k("system_neutral1_10")), col(k("system_neutral1_900")), col(k("system_neutral2_700")), col(k("system_neutral2_200")),
            col(k("system_neutral2_200")), col(k("system_neutral2_100")), col(k("system_accent1_600")), col(k("system_accent1_600")), red, green, false
        )
        dark = Pal(
            col(k("system_neutral1_900")), col(k("system_neutral1_800")), col(k("system_neutral1_50")), col(k("system_neutral2_200")), col(k("system_neutral1_700")),
            col(k("system_neutral1_700")), col(k("system_neutral2_700")), col(k("system_accent1_200")), col(k("system_accent1_200")), redD, greenD, true
        )
        fun vars(d: Boolean): String {
            val s = if (d) {
                mapOf(
                    "bg" to "system_neutral1_900", "card" to "system_neutral1_800", "text" to "system_neutral1_50", "faint" to "system_neutral2_200",
                    "sel" to "system_accent2_700", "press" to "system_neutral2_700", "border" to "system_neutral1_700", "tagfg" to "system_neutral2_100", "tag" to "system_neutral2_700",
                    "hover" to "system_neutral2_700", "a1" to "system_accent1_200", "a1b" to "system_accent1_100", "a2" to "system_accent2_200", "a3" to "system_accent3_200"
                )
            } else {
                mapOf(
                    "bg" to "system_neutral2_50", "card" to "system_neutral1_10", "text" to "system_neutral1_900", "faint" to "system_neutral2_700",
                    "sel" to "system_accent2_100", "press" to "system_neutral2_100", "border" to "system_neutral2_200", "tagfg" to "system_neutral2_700", "tag" to "system_neutral2_100",
                    "hover" to "system_neutral2_50", "a1" to "system_accent1_600", "a1b" to "system_accent1_500", "a2" to "system_accent2_600", "a3" to "system_accent3_600"
                )
            }
            val v = s.mapValues { k(it.value) }
            fun h(n: String) = hx(v[n]!!)
            val rd = if (d) "#f38ba8" else "#d20f39"
            val gn = if (d) "#a6e3a1" else "#40a02b"
            return "background-color:${h("bg")};--card-bg:${h("card")};--text-fg:${h("text")};--text-fg-faint:${h("faint")};--text-bg-selected:${h("sel")};--press-bg:${h("press")};" +
                "--card-border:${h("border")};--card-box-shadow:${if (d) "#0000001f" else "#00000017"};--divider:${h("border")};--tag-fg:${h("tagfg")};--tag-bg:${h("tag")};" +
                "--tag-fg-active:${h("text")};--tag-bg-active:${h("sel")};--hover-bg:${h("hover")};--hover-fg:${h("text")};--tag-border:transparent;--cloze-fg:${h("a1")};" +
                "--cloze-bg:${h("card")};--link-fg:${h("a1")};--link-fg-active:${h("a1b")};--bold-fg:${h("a1")};--bold-italic-fg:${h("a1")};--italic-fg:${h("a3")};" +
                "--h1-fg:${h("a3")};--h2-fg:${h("a2")};--h3-fg:${h("a1")};--h4-fg:$rd;--h5-fg:${h("a1b")};--h6-fg:${h("faint")};--mark-color:color-mix(in srgb,${h("a1")} 22%,transparent);" +
                "--scheme-red:$rd;--scheme-green:$gn;--scheme-blue:${h("a1")};--ctp-text:${rgb(v["text"]!!)};--ctp-red:${rgb(c(rd).let { (it.value shr 32).toInt() })};" +
                "--ctp-green:${rgb(c(gn).let { (it.value shr 32).toInt() })};--ctp-blue:${rgb(v["a1"]!!)};--ctp-yellow:${if (d) "249, 226, 175" else "223, 142, 29"};" +
                "--ctp-teal:${if (d) "148, 226, 213" else "23, 146, 153"};--accent:${h("a1")}"
        }
        accents = listOf(1, 2, 3).map { col(k("system_accent${it}_600")) to col(k("system_accent${it}_200")) }
        css = "html[data-theme=you] .card:not(.night_mode){${vars(false)}}html[data-theme=you] .card.card.night_mode,html[data-theme=you] .card.card.nightMode{${vars(true)}}"
    }
}

fun pal(theme: String, dark: Boolean): Pal = if (theme == "you") {
    (if (dark) You.dark else You.light) ?: pal("default", dark)
} else if (!dark) {
    when (theme) {
        "ctp" -> Pal(c("#e6e9ef"), c("#eff1f5"), c("#4c4f69"), c("#6c6f85"), c("#bcc0cc"), c("#bcc0cc"), c("#ccd0da"), c("#8839ef"), c("#1e66f5"), c("#d20f39"), c("#40a02b"), false)
        "nord" -> Pal(c("#e5e9f0"), c("#eceff4"), c("#2e3440"), c("#4c566a"), c("#d8dee9"), c("#d8dee9"), c("#e5e9f0"), c("#5e81ac"), c("#5e81ac"), c("#bf616a"), c("#a3be8c"), false)
        "dracula" -> Pal(mix("#fffbeb", .92f, "#6c664b"), c("#fffbeb"), c("#1f1f1f"), c("#6c664b"), c("#cfcfde"), c("#cfcfde"), mix("#fffbeb", .95f, "#6c664b"), c("#644ac9"), c("#036a96"), c("#cb3a2a"), c("#14710a"), false)
        else -> Pal(c("#e6e9ef"), c("#eff1f5"), c("#4c4f69"), c("#6c6f85"), c("#bcc0cc"), c("#bcc0cc"), c("#dfe2e9"), c("#4c4f69"), c("#2a6ef5"), c("#d20f39"), c("#40a02b"), false)
    }
} else {
    when (theme) {
        "ctp" -> Pal(c("#181825"), c("#1e1e2e"), c("#cdd6f4"), c("#a6adc8"), c("#313244"), c("#313244"), c("#313244"), c("#cba6f7"), c("#89b4fa"), c("#f38ba8"), c("#a6e3a1"), true)
        "tokyo" -> Pal(c("#16161e"), c("#1a1b26"), c("#c0caf5"), c("#a9b1d6"), c("#292e42"), c("#292e42"), c("#292e42"), c("#7aa2f7"), c("#7aa2f7"), c("#f7768e"), c("#9ece6a"), true)
        "nord" -> Pal(c("#2e3440"), c("#3b4252"), c("#eceff4"), c("#d8dee9"), c("#434c5e"), c("#434c5e"), c("#434c5e"), c("#88c0d0"), c("#88c0d0"), c("#bf616a"), c("#a3be8c"), true)
        "dracula" -> Pal(c("#21222c"), c("#282a36"), c("#f8f8f2"), c("#f8f8f2b3"), c("#44475a"), c("#44475a"), c("#343746"), c("#bd93f9"), c("#8be9fd"), c("#ff5555"), c("#50fa7b"), true)
        else -> Pal(c("#242426"), c("#2e2f31"), c("#ffffffe6"), c("#ffffffb3"), c("#ffffff0a"), c("#ffffff1f"), c("#ffffff1f"), c("#ffffffe6"), c("#87b0f9"), c("#f38ba8"), c("#a6e3a1"), true)
    }
}

val LocalPal = staticCompositionLocalOf { pal("default", true) }
val P: Pal @Composable get() = LocalPal.current

val Carlito = FontFamily(Font(R.font.carlito_regular, FontWeight.Normal), Font(R.font.carlito_bold, FontWeight.Bold))
val RED = Color(0xFFE5534B) // destructive buttons, required marks
val HEART = Color(0xFFFF3040) // Reels like / Repost

/* catppuccin [latte, mocha] pairs (deck pills, avatars) */
val DCOL = listOf(
    "#dd7878" to "#f2cdcd", "#ea76cb" to "#f5c2e7", "#8839ef" to "#cba6f7", "#d20f39" to "#f38ba8", "#e64553" to "#eba0ac",
    "#fe640b" to "#fab387", "#df8e1d" to "#f9e2af", "#40a02b" to "#a6e3a1", "#179299" to "#94e2d5", "#04a5e5" to "#89dceb",
    "#209fb5" to "#74c7ec", "#1e66f5" to "#89b4fa", "#7287fd" to "#b4befe"
).map { c(it.first) to c(it.second) }

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = P.text,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    lines: Int = Int.MAX_VALUE,
    lh: TextUnit = TextUnit.Unspecified,
    italic: Boolean = false,
    spacing: TextUnit = TextUnit.Unspecified
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = Carlito,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            lineHeight = lh,
            letterSpacing = spacing,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null
        ),
        maxLines = lines,
        overflow = TextOverflow.Ellipsis
    )
}

/** a press highlight without a ripple, like the site's :active */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.press(enabled: Boolean = true, bg: Color? = null, pressed: Color = P.press, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp), onLong: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val col by animateColorAsState(if (down) pressed else bg ?: Color.Transparent, tween(if (down) 0 else 250), label = "press")
    return this.clip(shape).background(col).let { m -> if (onLong != null) m.combinedClickable(src, null, enabled, onLongClick = onLong, onClick = onClick) else m.clickable(src, null, enabled, onClick = onClick) }
}

/** .nx: the site's button: card colour, a border, 8dp corners, bold. p = primary (text colour, inverted) */
@Composable
fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    p: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    h: Dp = 38.dp,
    size: TextUnit = 16.sp,
    pad: Dp = 25.dp,
    onClick: () -> Unit
) {
    val s = RoundedCornerShape(8.dp)
    /* a disabled primary button: a lighter mid-tone of its colour (not the full-contrast fill), its label in the card colour */
    val hi = if (p && !enabled) androidx.compose.ui.graphics.lerp(P.card, P.hi, .42f) else P.hi
    Box(
        modifier.heightIn(min = h).border(1.dp, if (p) hi else P.border, s).press(enabled, if (p) hi else P.card, if (p) hi.copy(alpha = .85f) else P.press, s, onClick = onClick)
            .padding(horizontal = pad),
        contentAlignment = Alignment.Center
    ) {
        Txt(
            label,
            size = size,
            bold = true,
            color = when {
                p -> P.onHi
                !enabled -> P.faint
                danger -> RED
                else -> P.text
            },
            lines = 1
        )
    }
}

@Composable
fun IconBtn(svg: String, modifier: Modifier = Modifier, desc: String = "", size: Dp = 18.dp, stroke: Float = 2.2f, plain: Boolean = false, onClick: () -> Unit) {
    val s = RoundedCornerShape(8.dp)
    Box(
        (if (plain) modifier.press(shape = s, onClick = onClick) else modifier.border(1.dp, P.border, s).press(bg = P.card, shape = s, onClick = onClick)),
        contentAlignment = Alignment.Center
    ) { Icon(svg, size, P.text, stroke) }
}

fun Modifier.dashedBottom(color: Color, top: Boolean = false) = drawBehind {
    val y = if (top) 0f else size.height - 1
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
}
fun Modifier.solidLine(color: Color, top: Boolean) = drawBehind {
    val y = if (top) 0f else size.height - 1
    drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
}

/** the site's popups (#pop #cp #ip #sp #cr): a dimmed screen with a card in the middle, a header with a title and × */
@Composable
fun Popup(
    title: @Composable RowScope.() -> Unit,
    onClose: () -> Unit,
    maxW: Dp = 480.dp,
    maxH: Float = .8f,
    scrim: Float = .55f,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val hgt = LocalConfiguration.current.screenHeightDp
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)).clickable(remember { MutableInteractionSource() }, null) { onClose() }
            .systemBarsPadding().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = maxW).fillMaxWidth().heightIn(max = (hgt * maxH).dp).shadow(20.dp, RoundedCornerShape(8.dp))
                .border(1.dp, P.border, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(P.card)
                .clickable(remember { MutableInteractionSource() }, null) {}
        ) {
            Row(Modifier.fillMaxWidth().dashedBottom(P.divider).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = title)
                Box(Modifier.size(36.dp).press(shape = RoundedCornerShape(6.dp), onClick = onClose), contentAlignment = Alignment.Center) { Txt("×", size = 22.sp, color = P.faint) }
            }
            content()
            footer?.invoke()
        }
    }
}

@Composable fun Popup(title: String, onClose: () -> Unit, maxW: Dp = 480.dp, maxH: Float = .8f, footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Popup({ Txt(title, bold = true) }, onClose, maxW, maxH, footer = footer, content = content)

/** .sw: the settings switch */
@Composable
fun Switch(on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "sw")
    val bg = lerp(P.border, P.hi, x)
    val knob = lerp(P.faint, P.onHi, x)
    Box(Modifier.size(45.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(bg).clickable(enabled = enabled) { onChange(!on) }) {
        Box(Modifier.offset(x = (3 + 19 * x).dp, y = 3.dp).size(20.dp).clip(CircleShape).background(knob))
    }
}

/** a settings row (.so): bold label, optional small line under it, something on the right */
@Composable
fun Row2(label: String, small: String? = null, modifier: Modifier = Modifier, labelColor: Color = P.text, right: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(label, bold = true, color = labelColor)
            if (!small.isNullOrEmpty()) Txt(small, size = 13.sp, color = P.faint)
        }
        right()
    }
}

@Composable fun Gap(h: Dp) = Box(Modifier.height(h))

@Composable fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier, contentAlignment = Alignment.Center, content = content)
