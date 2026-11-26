package pizza.kulhad.medslave.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/* The site's icons are inline SVG (lucide-style line icons). This draws those same strings: <path d>, <circle>, <ellipse>, <rect>,
   <line>, <polyline>, <polygon>, each stroked (or filled when it says fill="currentColor"). */
class SvgEl(val path: Path, val fill: Boolean)

private val CACHE = HashMap<String, List<SvgEl>>()
private val EL = Regex("<(path|circle|rect|ellipse|line|polyline|polygon)\\b([^>]*?)/?>")
private val AT = Regex("([\\w-]+)=\"([^\"]*)\"")

fun svgParse(src: String): List<SvgEl> = CACHE.getOrPut(src) {
    EL.findAll(src).mapNotNull { m ->
        val a = AT.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        fun f(k: String) = a[k]?.toFloatOrNull() ?: 0f
        val d = when (m.groupValues[1]) {
            "path" -> a["d"]
            "circle" -> { val r = f("r"); "M${f("cx") - r},${f("cy")}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z" }
            "ellipse" -> { val rx = f("rx"); val ry = f("ry"); "M${f("cx") - rx},${f("cy")}a$rx,$ry 0 1,0 ${2 * rx},0a$rx,$ry 0 1,0 ${-2 * rx},0Z" }
            "rect" -> { val x = f("x"); val y = f("y"); val w = f("width"); val h = f("height"); val r = minOf(f("rx").takeIf { it > 0 } ?: f("ry"), w / 2, h / 2)
                if (r <= 0) "M$x,${y}h${w}v${h}h${-w}Z"
                else "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,$r v${h - 2 * r}a$r,$r 0 0 1 ${-r},$r h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}Z" }
            "line" -> "M${f("x1")},${f("y1")}L${f("x2")},${f("y2")}"
            "polyline", "polygon" -> { val p = (a["points"] ?: "").trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.chunked(2).filter { it.size == 2 }
                if (p.isEmpty()) null else "M" + p.joinToString("L") { "${it[0]},${it[1]}" } + if (m.groupValues[1] == "polygon") "Z" else "" }
            else -> null
        } ?: return@mapNotNull null
        runCatching { SvgEl(PathParser().parsePathString(d).toPath(), a["fill"] == "currentColor") }.getOrNull()
    }.toList()
}

/** an icon from SVG markup, in a [box]×[box] viewBox, stroked in [color] */
@Composable
fun Icon(svg: String, size: Dp, color: Color, stroke: Float = 2f, fill: Boolean = false, box: Float = 24f, modifier: Modifier = Modifier, fillColor: Color = color) {
    val els = remember(svg) { svgParse(svg) }
    Canvas(modifier.size(size)) {
        val k = this.size.width / box
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            els.forEach { e ->
                if (fill || e.fill) drawPath(e.path, fillColor, style = Fill)
                if (!e.fill) drawPath(e.path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/* icons from index.html */
object Ic {
    const val GEAR = "<circle cx=\"12\" cy=\"12\" r=\"3\"/><path d=\"M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z\"/>"
    const val CAL = "<rect x=\"3\" y=\"4\" width=\"18\" height=\"18\" rx=\"2\"/><path d=\"M16 2v4M8 2v4M3 10h18\"/><path d=\"M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01\"/>"
    const val HISTORY = "<path d=\"M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8\"/><path d=\"M3 3v5h5\"/><path d=\"M12 7v5l4 2\"/>"
    const val LIST = "<path d=\"M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01\"/>"
    const val REELS = "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"4\"/><path d=\"M3 8h18M8 3l3 5M14 3l3 5\"/><path d=\"m10 11.5 4.5 2.75L10 17Z\"/>"
    const val HOME = "<path d=\"M3 10.5 12 3l9 7.5\"/><path d=\"M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5\"/>"
    const val PIE = "<path d=\"M21.21 15.89A10 10 0 1 1 8 2.83\"/><path d=\"M22 12A10 10 0 0 0 12 2v10z\"/>"
    const val DOWN = "<polyline points=\"6 9 12 15 18 9\"/>"
    const val RIGHT = "<polyline points=\"9 6 15 12 9 18\"/>"
    const val HEART = "<path d=\"M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z\"/>"
    const val COMMENT = "<path d=\"M7.9 20A9 9 0 1 0 4 16.1L2 22Z\"/>"
    const val REPOST = "<path d=\"m17 2 4 4-4 4\"/><path d=\"M3 11v-1a4 4 0 0 1 4-4h14\"/><path d=\"m7 22-4-4 4-4\"/><path d=\"M21 13v1a4 4 0 0 1-4 4H3\"/>"
    const val SAVE = "<path d=\"m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z\"/>"   // bookmark
    const val STOP = "<path d=\"M2.586 16.726A2 2 0 0 1 2 15.312V8.688a2 2 0 0 1 .586-1.414l4.688-4.688A2 2 0 0 1 8.688 2h6.624a2 2 0 0 1 1.414.586l4.688 4.688A2 2 0 0 1 22 8.688v6.624a2 2 0 0 1-.586 1.414l-4.688 4.688a2 2 0 0 1-1.414.586H8.688a2 2 0 0 1-1.414-.586z\"/><path d=\"M8 12h8\"/>"   // stop sign (octagon)
    const val CROSS = "<path d=\"M9.6 9.6l4.8 4.8M14.4 9.6l-4.8 4.8\"/>"
    const val SEND = "<path d=\"m22 2-7 20-4-9-9-4Z\"/><path d=\"M22 2 11 13\"/>"
    const val NOTE = "<path d=\"M9 18V5l12-2v13\"/><circle cx=\"6\" cy=\"18\" r=\"3\"/><circle cx=\"18\" cy=\"16\" r=\"3\"/>"
    const val SLASH = "<path d=\"M3 3l18 18\"/>"
    const val PALETTE = "<circle cx=\"12\" cy=\"12\" r=\"9\"/><circle cx=\"8.5\" cy=\"10\" r=\"1.2\"/><circle cx=\"12\" cy=\"7.5\" r=\"1.2\"/><circle cx=\"15.5\" cy=\"10\" r=\"1.2\"/><path d=\"M12 21a2.5 2.5 0 0 1 0-5h2a3 3 0 0 0 3-3\"/>"
    const val CARDS = "<rect x=\"7\" y=\"3\" width=\"12\" height=\"16\" rx=\"2\"/><path d=\"M5 6.5v12a2 2 0 0 0 2 2h9\"/>"
    const val DB = "<ellipse cx=\"12\" cy=\"5.5\" rx=\"7.5\" ry=\"2.8\"/><path d=\"M4.5 5.5v13c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8v-13M4.5 12c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8\"/>"
    const val ALL = "<path d=\"M12 2v20\"/><path d=\"M9.5 5.5c.5-1.3 1.5-2 2.5-2 1.7 0 3 .9 3 2.2S13.7 8 12 8s-3 .9-3 2.2 1.3 2.3 3 2.3 3 .9 3 2.2-1.3 2.3-3 2.3-3 .9-3 2.2 1.3 2.3 3 2.3\"/><circle cx=\"9.3\" cy=\"5.6\" r=\".6\" fill=\"currentColor\"/>"
}

/* credits: a subject icon by name (deck.js DICO) */
val DICO = listOf(
    Regex("surg") to "<path d=\"M11 16.586V19a1 1 0 0 1-1 1H2L18.37 3.63a1 1 0 1 1 3 3l-9.663 9.663a1 1 0 0 1-1.414 0L8 14\"/>",
    Regex("obg|obst|gyn") to "<circle cx=\"12\" cy=\"9\" r=\"6\"/><path d=\"M12 15v7M9 19h6\"/>",
    Regex("fmt|forens") to "<path d=\"m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z\"/><path d=\"m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z\"/><path d=\"M7 21h10M12 3v18M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2\"/>",
    Regex("micro") to "<path d=\"M6 18h8M3 22h18M14 22a7 7 0 1 0 0-14h-1M9 14h2\"/><path d=\"M9 12a2 2 0 0 1-2-2V6h6v4a2 2 0 0 1-2 2Z\"/><path d=\"M12 6V3a1 1 0 0 0-1-1H9a1 1 0 0 0-1 1v3\"/>",
    Regex("pharm") to "<path d=\"m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z\"/><path d=\"m8.5 8.5 7 7\"/>",
    Regex("ophth|eye") to "<path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\"/><circle cx=\"12\" cy=\"12\" r=\"3\"/>",
    Regex("paed|pediat") to "<path d=\"M9 12h.01M15 12h.01M10 16c.5.3 1.2.5 2 .5s1.5-.2 2-.5\"/><path d=\"M19 6.3a9 9 0 0 1 1.8 3.9 2 2 0 0 1 0 3.6 9 9 0 0 1-17.6 0 2 2 0 0 1 0-3.6A9 9 0 0 1 12 3c2 0 3.5 1.1 3.5 2.5s-.9 2.5-2 2.5c-.8 0-1.5-.4-1.5-1\"/>",
    Regex("medic") to "<path d=\"M11 2v2M5 2v2M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1\"/><path d=\"M8 15a6 6 0 0 0 12 0v-3\"/><circle cx=\"20\" cy=\"10\" r=\"2\"/>",
    Regex(".") to "<path d=\"M12 7v14\"/><path d=\"M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z\"/>")
