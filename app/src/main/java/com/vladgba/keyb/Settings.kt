package com.vladgba.keyb

class Settings {
    companion object {

        var all: LayoutParse.DataNode = LayoutParse.DataNode()
        var lastModified = 0L
        var padding = 5f
        var primaryFont = 2f
        var secondaryFont = 4.5f
        var horTick = 30
        var verTick = 50
        var offset = 70
        var longPressTime = 300L

        operator fun get(s: String): LayoutParse.DataNode = if (all.has(s)) all[s] else LayoutParse.DataNode()
        fun has(k: String) = all.has(k)

        operator fun plusAssign(map: LayoutParse.DataNode) {
            if (all.count() > 0) {
                // TODO: adding
            } else {
                all = map
            }
        }
        fun getVal(s: String, d: String): String {
            if (!has(s)) return d
            val r = all.str(s)
            return r.ifEmpty { d }
        }

        fun setDefaults() {
            primaryFont = getVal("sizePrimary", "2").toFloat()
            secondaryFont = getVal("sizeSecondary", "4.5").toFloat()
            horTick = getVal("horizontalSense", "30").toInt()
            verTick = getVal("verticalSense", "50").toInt()
            offset = getVal("extendedSense", "70").toInt()
            longPressTime = getVal("longPressMs", "300").toLong()
        }

        fun str(s: String) = if (all.has(s)) all.str(s) else ""

        fun num(i: String) = if (all.has(i)) all.num(i) else 0
    }
}