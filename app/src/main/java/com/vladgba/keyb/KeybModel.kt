package com.vladgba.keyb

import java.io.*
import kotlin.math.*
import android.os.Environment
import android.util.Log

class KeybModel(val c: KeybCtl, jsondat: String, portrait: Boolean, isJsonData: Boolean) {
    val rows = ArrayList<Row>()
    val keys: ArrayList<Key>
    var keySize = 0
    var minWidth = c.dm.widthPixels
    var height = 0
    private var loadx = 0
    private var loady = 0
    var loaded = false
    var lastdate: Long = 0

    init {
        loadx = 0
        loady = 0
        keys = ArrayList()
        loaded = false
        val dm = c.resources.displayMetrics

        try {
            val glob = LayoutParse(if (isJsonData) jsondat else loadKeybLayout(jsondat), c).parse()
            //val size = (glob.str("keyCount")).toFloat()
            //keySize = (if (portrait) min(dm.widthPixels, dm.heightPixels) / size else ceil(max(dm.widthPixels, dm.heightPixels) / size)).toInt()
            keySize =  (if (portrait) min(dm.widthPixels, dm.heightPixels) / 10f else ceil(max(dm.widthPixels, dm.heightPixels) / 10f)).toInt()

            loadAllRows(glob["keyb"])

            height = loady
            loaded = true
        } catch (e: Exception) {
            loaded = false
            c.prStack(e)
        }
    }

    constructor(c: KeybCtl, jsonName: String, portrait: Boolean): this(c, jsonName, portrait, false)

    fun loadKeybLayout(name: String): String {
        val sdcard = Environment.getExternalStorageDirectory()
        val file = File(sdcard, "vkeyb/$name.json")
        lastdate = file.lastModified()
        val text = StringBuilder()
        try {
            val br = BufferedReader(FileReader(file))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                text.append(line)
                text.append('\n')
            }
            br.close()
            Log.d("Keyb", "Done")
            return text.toString()
        } catch (e: Exception) {
            c.prStack(e)
        }
        return ""
    }

    fun getKey(x: Int, y: Int): Key? {
        var rowIndex = 0
        var keyIndex = 0
        for (row in rows) {
            if (y <= rowIndex + row.height) {
                var keyOffset = 0
                for (key in row.keys) {
                    if (x <= keyOffset + key.width) return key
                    keyOffset += key.width
                    keyIndex++
                }
                break
            }
            rowIndex += row.height
        }
        return null
    }

    private fun loadKey(jdata: LayoutParse.DataNode, cr: Row, pos: Int) {
        val loadkey = Key(c, cr, loadx, loady, jdata, pos)
        keys.add(loadkey)
        cr.keys.add(loadkey)
        loadx += loadkey.width
        if (loadx > minWidth) minWidth = loadx
    }

    private fun loadAllRows(json: LayoutParse.DataNode) {
        for (i in 0 until json.count()) {
            loadRow(json[i], posOnLine(json, i, 0, 3, 6))
        }
    }

    private fun loadRow(row: LayoutParse.DataNode, pos: Int) {
        loadx = 0
        val loadcurrentRow = Row(this, row)
        rows.add(loadcurrentRow)
        for (i in 0 until row.count()) loadKey(row[i], loadcurrentRow, pos + posOnLine(row, i, 1, 2, 3))
        loady += loadcurrentRow.height
        loadcurrentRow.calcWidth()

    }

    private fun posOnLine(json: LayoutParse.DataNode, i: Int, f: Int, m: Int, l: Int) = if (i == 0) f else if (i == json.count() - 1) l else m


    class Row(val parent: KeybModel, val options: LayoutParse.DataNode) {
        var height: Int = 0
        var keys = ArrayList<Key>()

        fun calcWidth() {
            var fullwidth = 0f
            for (key in keys) {
                fullwidth += key[KEY_WIDTH].ifEmpty { "1" }.toFloat()
            }
            var x = 0
            for (key in keys) {
                key.x = x
                key.width = (parent.c.dm.widthPixels * key[KEY_WIDTH].ifEmpty { "1" }.toFloat() / fullwidth).roundToInt()
                x += key.width
            }
        }

        fun has(s: String) = options.has(s) || parent.has(s)

        operator fun get(s: String): String {
            return if (options.has(s)) options.str(s)
            else if (parent.has(s)) parent[s]
            else ""
        }

        init {
            val hy = try {
                if (options.has("height")) options.str("height").toFloat() else 1f
            }  catch (_: Exception) {
                1f
            }
            height = (hy * parent.keySize).toInt()
            //keySize = Settings.getVal("height", parent.keySize.toString()).toInt()
        }
    }

    operator fun get(s: String) = Settings.str(s)

    fun has(s: String) = Settings.has(s)
}