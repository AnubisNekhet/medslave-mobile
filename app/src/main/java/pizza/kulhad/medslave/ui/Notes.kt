package pizza.kulhad.medslave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pizza.kulhad.medslave.Rs

/* What's new: the release notes (assets/notes/<version>.md) once after an update (Updater.whatsNew). Markdown, the plain kind:
   # headings, - / * / 1. lists, paragraphs, **bold**, *italic* / _italic_, `code`, [links](…) shown as their text */

/** a block of the notes: h (heading level 1–3, 0: none), li (a list item's marker, "" for none), its text */
internal class MdBlock(val h: Int, val li: String, val text: String)

internal fun mdBlocks(md: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out.add(MdBlock(0, "", para.toString().trim()))
        para.clear()
    }
    md.replace("\r\n", "\n").lines().forEach { raw ->
        val l = raw.trim()
        val h = Regex("^(#{1,6})\\s+(.*)$").find(l)
        val ul = Regex("^[-*+]\\s+(.*)$").find(l)
        val ol = Regex("^(\\d+)[.)]\\s+(.*)$").find(l)
        when {
            l.isEmpty() -> flush()
            h != null -> {
                flush()
                out.add(MdBlock(minOf(3, h.groupValues[1].length), "", h.groupValues[2].trimEnd('#', ' ')))
            }
            ul != null -> {
                flush()
                out.add(MdBlock(0, "•", ul.groupValues[1]))
            }
            ol != null -> {
                flush()
                out.add(MdBlock(0, ol.groupValues[1] + ".", ol.groupValues[2]))
            }
            /* a list item's next line carries on that item */
            para.isEmpty() && out.isNotEmpty() && out.last().li.isNotEmpty() && raw.startsWith(" ") -> out[out.size - 1] = out.last().let { MdBlock(it.h, it.li, it.text + " " + l) }
            else -> para.append(if (para.isEmpty()) l else " $l")
        }
    }
    flush()
    return out
}

private val INLINE = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*|_(.+?)_|`(.+?)`|\\[(.+?)]\\((.*?)\\)")

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    INLINE.findAll(s).forEach { m ->
        append(s.substring(at, m.range.first))
        val g = m.groupValues
        when {
            g[1].isNotEmpty() || g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1] + g[2]) }
            g[3].isNotEmpty() || g[4].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[3] + g[4]) }
            g[5].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[5]) }
            else -> append(g[6])
        }
        at = m.range.last + 1
    }
    append(s.substring(at))
}

@Composable
private fun MdText(s: String, size: TextUnit, bold: Boolean = false, color: Color = P.text, modifier: Modifier = Modifier) {
    BasicText(inline(s), modifier, style = TextStyle(color = color, fontSize = size, fontFamily = Carlito, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, lineHeight = size * 1.35f))
}

@Composable
private fun Md(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        mdBlocks(md).forEach { b ->
            when {
                b.h > 0 -> MdText(
                    b.text,
                    if (b.h == 1) {
                        17.sp
                    } else if (b.h == 2) {
                        16.sp
                    } else {
                        15.sp
                    },
                    bold = true,
                    modifier = Modifier.padding(top = 4.dp)
                )
                b.li.isNotEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(b.li, size = 15.sp, color = P.faint)
                    MdText(b.text, 15.sp, modifier = Modifier.weight(1f))
                }
                else -> MdText(b.text, 15.sp)
            }
        }
    }
}

@Composable
fun NotesBox(rs: Rs) {
    val u = rs.updater
    val l = u.notes ?: return
    val close = { u.notes = null }
    val maxH = (LocalConfiguration.current.screenHeightDp * .6f).dp
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(remember { MutableInteractionSource() }, null) { close() }.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().shadow(20.dp, RoundedCornerShape(8.dp)).border(1.dp, P.border, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                .background(P.card).clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("What’s new", bold = true, size = 17.sp)
                Txt("medslave ${l.first().version}", size = 13.6.sp, color = P.faint)
            }
            Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                l.forEach { n ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (l.size > 1) Txt("Version ${n.version}", bold = true, size = 14.4.sp, color = P.faint)
                        Md(n.md)
                    }
                }
            }
            Box(Modifier.align(Alignment.End)) { Btn("OK", p = true, h = 37.dp, size = 15.sp, pad = 16.dp) { close() } }
        }
    }
}
