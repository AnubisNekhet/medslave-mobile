package pizza.kulhad.medslave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
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
class Pal(val bg: Color, val card: Color, val text: Color, val faint: Color, val border: Color, val divider: Color, val press: Color,
          val accent: Color, val link: Color, val red: Color, val green: Color, val dark: Boolean)

private fun c(s: String): Color { val h = s.removePrefix("#"); val v = h.toLong(16); return if (h.length == 8) Color(((v and 0xff) shl 24 or (v ushr 8)).toInt()) else Color((0xff000000 or v).toInt()) }
private fun mix(a: String, p: Float, b: String) = lerp(c(b), c(a), p)

fun pal(theme: String, dark: Boolean): Pal = if (!dark) when (theme) {
    "ctp" -> Pal(c("#e6e9ef"), c("#eff1f5"), c("#4c4f69"), c("#6c6f85"), c("#bcc0cc"), c("#bcc0cc"), c("#ccd0da"), c("#8839ef"), c("#1e66f5"), c("#d20f39"), c("#40a02b"), false)
    "nord" -> Pal(c("#e5e9f0"), c("#eceff4"), c("#2e3440"), c("#4c566a"), c("#d8dee9"), c("#d8dee9"), c("#e5e9f0"), c("#5e81ac"), c("#5e81ac"), c("#bf616a"), c("#a3be8c"), false)
    "dracula" -> Pal(mix("#fffbeb", .92f, "#6c664b"), c("#fffbeb"), c("#1f1f1f"), c("#6c664b"), c("#cfcfde"), c("#cfcfde"), mix("#fffbeb", .95f, "#6c664b"), c("#644ac9"), c("#036a96"), c("#cb3a2a"), c("#14710a"), false)
    else -> Pal(c("#e6e9ef"), c("#eff1f5"), c("#4c4f69"), c("#6c6f85"), c("#bcc0cc"), c("#bcc0cc"), c("#dfe2e9"), c("#4c4f69"), c("#2a6ef5"), c("#d20f39"), c("#40a02b"), false)
} else when (theme) {
    "ctp" -> Pal(c("#181825"), c("#1e1e2e"), c("#cdd6f4"), c("#a6adc8"), c("#313244"), c("#313244"), c("#313244"), c("#cba6f7"), c("#89b4fa"), c("#f38ba8"), c("#a6e3a1"), true)
    "tokyo" -> Pal(c("#16161e"), c("#1a1b26"), c("#c0caf5"), c("#a9b1d6"), c("#292e42"), c("#292e42"), c("#292e42"), c("#7aa2f7"), c("#7aa2f7"), c("#f7768e"), c("#9ece6a"), true)
    "nord" -> Pal(c("#2e3440"), c("#3b4252"), c("#eceff4"), c("#d8dee9"), c("#434c5e"), c("#434c5e"), c("#434c5e"), c("#88c0d0"), c("#88c0d0"), c("#bf616a"), c("#a3be8c"), true)
    "dracula" -> Pal(c("#21222c"), c("#282a36"), c("#f8f8f2"), c("#f8f8f2b3"), c("#44475a"), c("#44475a"), c("#343746"), c("#bd93f9"), c("#8be9fd"), c("#ff5555"), c("#50fa7b"), true)
    else -> Pal(c("#242426"), c("#2e2f31"), c("#ffffffe6"), c("#ffffffb3"), c("#ffffff0a"), c("#ffffff1f"), c("#ffffff1f"), c("#ffffffe6"), c("#87b0f9"), c("#f38ba8"), c("#a6e3a1"), true)
}

val LocalPal = staticCompositionLocalOf { pal("default", true) }
val P: Pal @Composable get() = LocalPal.current

val Carlito = FontFamily(Font(R.font.carlito_regular, FontWeight.Normal), Font(R.font.carlito_bold, FontWeight.Bold))
val RED = Color(0xFFE5534B)       // destructive buttons, required marks
val HEART = Color(0xFFFF3040)     // Reels like / Repost

/* catppuccin [latte, mocha] pairs (deck pills, avatars) */
val DCOL = listOf("#dd7878" to "#f2cdcd", "#ea76cb" to "#f5c2e7", "#8839ef" to "#cba6f7", "#d20f39" to "#f38ba8", "#e64553" to "#eba0ac",
    "#fe640b" to "#fab387", "#df8e1d" to "#f9e2af", "#40a02b" to "#a6e3a1", "#179299" to "#94e2d5", "#04a5e5" to "#89dceb",
    "#209fb5" to "#74c7ec", "#1e66f5" to "#89b4fa", "#7287fd" to "#b4befe").map { c(it.first) to c(it.second) }

@Composable
fun Txt(text: String, modifier: Modifier = Modifier, size: TextUnit = 16.sp, color: Color = P.text, bold: Boolean = false,
        align: TextAlign = TextAlign.Start, lines: Int = Int.MAX_VALUE, lh: TextUnit = TextUnit.Unspecified, italic: Boolean = false, spacing: TextUnit = TextUnit.Unspecified) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = size, fontFamily = Carlito, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = align, lineHeight = lh, letterSpacing = spacing, fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else null),
        maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/** a press highlight without a ripple, like the site's :active */
@Composable
fun Modifier.press(enabled: Boolean = true, bg: Color? = null, pressed: Color = P.press, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp), onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val col by animateColorAsState(if (down) pressed else bg ?: Color.Transparent, tween(if (down) 0 else 250), label = "press")
    return this.clip(shape).background(col).clickable(src, null, enabled, onClick = onClick)
}

/** .nx: the site's button: card colour, a border, 8dp corners, bold. p = primary (text colour, inverted) */
@Composable
fun Btn(label: String, modifier: Modifier = Modifier, p: Boolean = false, danger: Boolean = false, enabled: Boolean = true, h: Dp = 38.dp,
        size: TextUnit = 16.sp, pad: Dp = 25.dp, onClick: () -> Unit) {
    val s = RoundedCornerShape(8.dp)
    Box(modifier.heightIn(min = h).border(1.dp, P.border, s).press(enabled, if (p) P.text else P.card, if (p) P.text.copy(alpha = .85f) else P.press, s, onClick)
        .padding(horizontal = pad), contentAlignment = Alignment.Center) {
        Txt(label, size = size, bold = true, color = when { !enabled -> P.faint; p -> P.card; danger -> RED; else -> P.text }, lines = 1)
    }
}
@Composable
fun IconBtn(svg: String, modifier: Modifier = Modifier, desc: String = "", size: Dp = 18.dp, stroke: Float = 2.2f, plain: Boolean = false, onClick: () -> Unit) {
    val s = RoundedCornerShape(8.dp)
    Box((if (plain) modifier.press(shape = s, onClick = onClick) else modifier.border(1.dp, P.border, s).press(bg = P.card, shape = s, onClick = onClick)),
        contentAlignment = Alignment.Center) { Icon(svg, size, P.text, stroke) }
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
fun Popup(title: @Composable RowScope.() -> Unit, onClose: () -> Unit, maxW: Dp = 480.dp, maxH: Float = .8f, scrim: Float = .55f,
          footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val hgt = LocalConfiguration.current.screenHeightDp
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)).clickable(remember { MutableInteractionSource() }, null) { onClose() }
        .systemBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = maxW).fillMaxWidth().heightIn(max = (hgt * maxH).dp).shadow(20.dp, RoundedCornerShape(8.dp))
            .border(1.dp, P.border, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).background(P.card)
            .clickable(remember { MutableInteractionSource() }, null) {}) {
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
    val bg = lerp(P.border, P.text, x); val knob = lerp(P.faint, P.card, x)
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
