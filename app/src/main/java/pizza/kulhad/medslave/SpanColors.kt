package pizza.kulhad.medslave

import kotlin.random.Random

/* medslave · span colours on import: a <span> whose style sets a text colour is brought into the app's palette.
   - already the app's own (a var(...), or exactly one of the accents, light or dark): left as it is
   - a CSS colour name (blue, crimson, …): the nearest light accent (by its light value)
   - any other value (hex, rgb(), hsl()): a random accent, the same one for the same value throughout the import
   black, white and greys (and near-grey values) lose the colour, so the text takes the theme's own text colour; inherit / currentColor /
   transparent are left alone. the accent is written as rgb(var(--ctp-…)), so it follows the theme and dark mode like the rest of the card */
class SpanColors(seed: Long = System.nanoTime()) {
    private val rnd = Random(seed)
    private val byValue = HashMap<String, String>()

    fun fix(html: String): String = SPAN.replace(html) { m ->
        val tag = m.value
        STYLE.replace(tag) { st -> val q = st.groupValues[1]; "style=$q" + fixStyle(st.groupValues[2]) + q }
    }

    private fun fixStyle(css: String): String = DECL.replace(css) { d ->
        val pre = d.groupValues[1]; val v = d.groupValues[2].trim(); val out = resolve(v)
        when (out) { null -> d.value; "" -> pre.takeIf { it.isNotEmpty() && it != ";" } ?: ""; else -> "${pre}color: $out" }
    }.replace(Regex("^\\s*;\\s*"), "")

    /** null: keep; "": drop the colour; else the new value */
    private fun resolve(v0: String): String? {
        val v = v0.replace(Regex("\\s*!important$", RegexOption.IGNORE_CASE), "").trim().lowercase()
        if (v.isEmpty() || v.contains("var(") || v in KEEP) return null
        NAMED[v]?.let { rgb -> return if (grey(rgb)) "" else "rgb(var(--ctp-${nearest(rgb)}))" }
        val rgb = parse(v) ?: return null   // something we can't read: leave it
        if (ACC.any { (_, l, d) -> rgb == l || rgb == d }) return null   // already one of the accents
        if (grey(rgb)) return ""
        return byValue.getOrPut(v) { "rgb(var(--ctp-${ACC[rnd.nextInt(ACC.size)].first}))" }
    }

    private fun nearest(c: Int): String = ACC.minBy { dist(c, it.second) }.first
    /** "redmean" colour distance: close to how different two colours look, without converting to Lab */
    private fun dist(a: Int, b: Int): Double {
        val r1 = a shr 16 and 255; val g1 = a shr 8 and 255; val b1 = a and 255; val r2 = b shr 16 and 255; val g2 = b shr 8 and 255; val b2 = b and 255
        val rm = (r1 + r2) / 2.0; val dr = (r1 - r2).toDouble(); val dg = (g1 - g2).toDouble(); val db = (b1 - b2).toDouble()
        return (2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db
    }
    private fun grey(c: Int): Boolean { val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255; return maxOf(r, g, b) - minOf(r, g, b) < 24 }

    private fun parse(v: String): Int? {
        HEX.matchEntire(v)?.let { val h = it.groupValues[1]
            val x = when (h.length) { 3, 4 -> h.take(3).map { c -> "$c$c" }.joinToString(""); else -> h.take(6) }
            return x.toInt(16) }
        RGB.matchEntire(v)?.let { val p = it.groupValues[1].split(Regex("[\\s,/]+")).filter { s -> s.isNotEmpty() }.take(3)
            if (p.size < 3) return null
            val n = p.map { s -> if (s.endsWith("%")) (s.dropLast(1).toDoubleOrNull() ?: return null) * 2.55 else s.toDoubleOrNull() ?: return null }
            return n.fold(0) { acc, x -> acc shl 8 or x.coerceIn(0.0, 255.0).toInt() } }
        HSL.matchEntire(v)?.let { val p = it.groupValues[1].split(Regex("[\\s,/]+")).filter { s -> s.isNotEmpty() }.take(3)
            if (p.size < 3) return null
            val h = (p[0].removeSuffix("deg").toDoubleOrNull() ?: return null) / 360.0; val s = (p[1].removeSuffix("%").toDoubleOrNull() ?: return null) / 100; val l = (p[2].removeSuffix("%").toDoubleOrNull() ?: return null) / 100
            val q = if (l < .5) l * (1 + s) else l + s - l * s; val pp = 2 * l - q
            fun hue(t0: Double): Int { var t = t0; if (t < 0) t += 1; if (t > 1) t -= 1
                val x = if (t < 1.0 / 6) pp + (q - pp) * 6 * t else if (t < .5) q else if (t < 2.0 / 3) pp + (q - pp) * (2.0 / 3 - t) * 6 else pp
                return (x * 255).toInt().coerceIn(0, 255) }
            return hue(h + 1.0 / 3) shl 16 or (hue(h) shl 8) or hue(h - 1.0 / 3) }
        return null
    }

    companion object {
        private val SPAN = Regex("<span\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val STYLE = Regex("style\\s*=\\s*([\"'])(.*?)\\1", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        /* color: only, not background-color or border-color */
        private val DECL = Regex("(^|;)\\s*color\\s*:\\s*([^;]*)", RegexOption.IGNORE_CASE)
        private val HEX = Regex("#([0-9a-f]{3,8})")
        private val RGB = Regex("rgba?\\((.*)\\)")
        private val HSL = Regex("hsla?\\((.*)\\)")
        private val KEEP = setOf("inherit", "initial", "unset", "revert", "currentcolor", "transparent")
        /* the default theme's accents (DCOL, Catppuccin's): its CSS variable, the light value, the dark value */
        private val ACC = listOf(
            Triple("flamingo", 0xdd7878, 0xf2cdcd), Triple("pink", 0xea76cb, 0xf5c2e7), Triple("mauve", 0x8839ef, 0xcba6f7), Triple("red", 0xd20f39, 0xf38ba8),
            Triple("maroon", 0xe64553, 0xeba0ac), Triple("peach", 0xfe640b, 0xfab387), Triple("yellow", 0xdf8e1d, 0xf9e2af), Triple("green", 0x40a02b, 0xa6e3a1),
            Triple("teal", 0x179299, 0x94e2d5), Triple("sky", 0x04a5e5, 0x89dceb), Triple("sapphire", 0x209fb5, 0x74c7ec), Triple("blue", 0x1e66f5, 0x89b4fa),
            Triple("lavender", 0x7287fd, 0xb4befe))
        /* the CSS colour names */
        private val NAMED: Map<String, Int> = ("aliceblue f0f8ff antiquewhite faebd7 aqua 00ffff aquamarine 7fffd4 azure f0ffff beige f5f5dc bisque ffe4c4 black 000000 " +
            "blanchedalmond ffebcd blue 0000ff blueviolet 8a2be2 brown a52a2a burlywood deb887 cadetblue 5f9ea0 chartreuse 7fff00 chocolate d2691e coral ff7f50 " +
            "cornflowerblue 6495ed cornsilk fff8dc crimson dc143c cyan 00ffff darkblue 00008b darkcyan 008b8b darkgoldenrod b8860b darkgray a9a9a9 darkgreen 006400 " +
            "darkgrey a9a9a9 darkkhaki bdb76b darkmagenta 8b008b darkolivegreen 556b2f darkorange ff8c00 darkorchid 9932cc darkred 8b0000 darksalmon e9967a " +
            "darkseagreen 8fbc8f darkslateblue 483d8b darkslategray 2f4f4f darkslategrey 2f4f4f darkturquoise 00ced1 darkviolet 9400d3 deeppink ff1493 " +
            "deepskyblue 00bfff dimgray 696969 dimgrey 696969 dodgerblue 1e90ff firebrick b22222 floralwhite fffaf0 forestgreen 228b22 fuchsia ff00ff " +
            "gainsboro dcdcdc ghostwhite f8f8ff gold ffd700 goldenrod daa520 gray 808080 green 008000 greenyellow adff2f grey 808080 honeydew f0fff0 hotpink ff69b4 " +
            "indianred cd5c5c indigo 4b0082 ivory fffff0 khaki f0e68c lavender e6e6fa lavenderblush fff0f5 lawngreen 7cfc00 lemonchiffon fffacd lightblue add8e6 " +
            "lightcoral f08080 lightcyan e0ffff lightgoldenrodyellow fafad2 lightgray d3d3d3 lightgreen 90ee90 lightgrey d3d3d3 lightpink ffb6c1 lightsalmon ffa07a " +
            "lightseagreen 20b2aa lightskyblue 87cefa lightslategray 778899 lightslategrey 778899 lightsteelblue b0c4de lightyellow ffffe0 lime 00ff00 " +
            "limegreen 32cd32 linen faf0e6 magenta ff00ff maroon 800000 mediumaquamarine 66cdaa mediumblue 0000cd mediumorchid ba55d3 mediumpurple 9370db " +
            "mediumseagreen 3cb371 mediumslateblue 7b68ee mediumspringgreen 00fa9a mediumturquoise 48d1cc mediumvioletred c71585 midnightblue 191970 " +
            "mintcream f5fffa mistyrose ffe4e1 moccasin ffe4b5 navajowhite ffdead navy 000080 oldlace fdf5e6 olive 808000 olivedrab 6b8e23 orange ffa500 " +
            "orangered ff4500 orchid da70d6 palegoldenrod eee8aa palegreen 98fb98 paleturquoise afeeee palevioletred db7093 papayawhip ffefd5 peachpuff ffdab9 " +
            "peru cd853f pink ffc0cb plum dda0dd powderblue b0e0e6 purple 800080 rebeccapurple 663399 red ff0000 rosybrown bc8f8f royalblue 4169e1 " +
            "saddlebrown 8b4513 salmon fa8072 sandybrown f4a460 seagreen 2e8b57 seashell fff5ee sienna a0522d silver c0c0c0 skyblue 87ceeb slateblue 6a5acd " +
            "slategray 708090 slategrey 708090 snow fffafa springgreen 00ff7f steelblue 4682b4 tan d2b48c teal 008080 thistle d8bfd8 tomato ff6347 " +
            "turquoise 40e0d0 violet ee82ee wheat f5deb3 white ffffff whitesmoke f5f5f5 yellow ffff00 yellowgreen 9acd32")
            .split(' ').chunked(2).associate { it[0] to it[1].toInt(16) }
    }
}
