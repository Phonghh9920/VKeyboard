package com.vladgba.keyb

import android.util.DisplayMetrics
import kotlin.math.roundToInt

class KeybLayout(val c: KeybCtl, glob: Flexaml.FxmlNode) : Flexaml.FxmlNode(glob, Settings) {
    val dm: DisplayMetrics = c.ctx.resources.displayMetrics
    val rows = ArrayList<Row>()
    val keys: ArrayList<Key>
    val width: Int
        get() = dm.widthPixels
    var height = 1
    private var loadx = 0
    private var loady = 0
    var loaded = false
    var lastdate: Long = 0

    init {
        loadx = 0
        loady = 0
        keys = ArrayList()
        loaded = false

        try {
            loadAllRows(glob)
            height = loady
            loaded = true
        } catch (e: Exception) {
            c.prStack(e)
        }
    }


    fun loadKeybLayout(name: String): String {
        val file = PFile(c.ctx, name)
        lastdate = file.lastModified()
        return file.read()
    }

    fun getKey(x: Int, y: Int): Key? {
        var startHeight = 0
        for (row in rows) {
            if (y <= startHeight + row.height) {
                var keyOffset = 0
                for (key in row.keys) {
                    if (x <= keyOffset + key.width) return key
                    keyOffset += key.width
                }
                break
            }
            startHeight += row.height
        }
        return null
    }

    private fun loadKey(keyData: Flexaml.FxmlNode, cRow: Row) {
        val loadkey = Key(c, cRow, loadx, loady, keyData)
        keys.add(loadkey)
        cRow.keys.add(loadkey)
        loadx += loadkey.width
    }

    private fun loadAllRows(json: Flexaml.FxmlNode) {
        for (i in 0 until json.childCount()) loadRow(json[i])
    }

    private fun loadRow(row: Flexaml.FxmlNode) {
        loadx = 0
        val lNewRow = Row(this, row, loady)
        rows.add(lNewRow)
        for (i in 0 until row.childCount()) loadKey(row[i], lNewRow)
        loady += lNewRow.height
        lNewRow.calcWidth()
    }

    /** Calculates the position of an element on a line based on its index within its parent node. */
    private fun posOnLine(parent: Flexaml.FxmlNode, index: Int, first: Int, middle: Int, last: Int) =
        if (index == 0) first else if (index == parent.childCount() - 1) last else middle

    class Row(val layout: KeybLayout, val options: Flexaml.FxmlNode, var y: Int) : Flexaml.FxmlNode(options, layout) {
        val refSize = if (layout.dm.heightPixels > layout.dm.widthPixels) layout.dm.heightPixels / 18f else layout.dm.widthPixels / 8f
        var height = (this.float(ROW_HEIGHT, 1f) * refSize).toInt()
        var keys = ArrayList<Key>()

        fun calcWidth() {
            var fullWidth = 0f
            for (key in keys) fullWidth += key.float(KEY_WIDTH, 1f)

            var x = 0
            for (key in keys) {
                key.x = x
                key.width = (layout.dm.widthPixels * key.float(KEY_WIDTH, 1f) / fullWidth).roundToInt()
                x += key.width
            }
        }

        fun moveVertical(newY: Int) {
            y = newY
            for (key in keys) key.y = newY
        }
    }


}