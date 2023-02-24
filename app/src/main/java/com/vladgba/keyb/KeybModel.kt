package com.vladgba.keyb

import java.io.*
import kotlin.math.*
import android.graphics.*
import android.os.Environment
import android.util.Log

class KeybModel(context: KeybCtl, jsondat: String, portrait: Boolean, isJsonData: Boolean) {
    val c = context
    val rows = ArrayList<Row>()
    val keys: ArrayList<Key>
    var keySize = 0
    var minWidth = 0
    var height = 0
    private var loadx = 0
    private var loady = 0
    private var loadcurrentRow: Row? = null
    var loaded = false
    var bitmap: Bitmap? = null
    var canv: Canvas? = null
    var lastdate: Long = 0
    var predict: JsonParse.JsonNode? = null

    init {
        Log.d("json", jsondat)
        loadx = 0
        loady = 0
        keys = ArrayList()
        loaded = false
        val dm = context.resources.displayMetrics

        try {
            val glob = JsonParse.map(if (isJsonData) jsondat else loadKeybLayout(jsondat))
            val size = (glob["keyCount"].str()).toFloat()
            keySize = (if (portrait) min(dm.widthPixels, dm.heightPixels) / size else ceil((max(dm.widthPixels, dm.heightPixels) / size).toDouble())).toInt()

            val json = glob["keyb"]
            for (i in 0 until json.len()) {
                val pos = if (i == 0) 0 else if (i == json.len() - 1) 6 else 3
                loadRow(json[i], pos)
            }
            
            predict = if (glob.have("dict")) glob["dict"] else null
            height = loady
            loaded = true
            context.erro = ""
        } catch (e: Exception) {
            loaded = false
            if (!isJsonData) context.erro = e.message!!
            Log.e("PSR", e.message!!)
            context.prStack(e)
        }
    }
    
    constructor(context: KeybCtl, jsonName: String, portrait: Boolean): this(context, jsonName, portrait, false)

    fun loadKeybLayout(name: String): String {
        val sdcard = Environment.getExternalStorageDirectory()
        val file = File(sdcard, "$name.json")
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
            Log.d("Keyb", "Error")
            Log.d("Keyb", e.message!!)
        }
        return ""
    }

    fun getKey(x: Int, y: Int): Key? {
        var mr = 0
        for (i in rows.indices) {
            val row = rows[i]
            if (row.keySize + mr >= y) {
                var mk = 0
                for (j in row.keys.indices) {
                    val k = row.keys[j]
                    if (k.width + mk >= x) return k
                    mk += k.width
                }
                break
            }
            mr += row.keySize
        }
        return null
    }

    private fun loadKey(jdata: JsonParse.JsonNode, pos: Int) {
        val loadkey = Key(c, loadcurrentRow, loadx, loady, jdata, pos)
        keys.add(loadkey)
        loadcurrentRow!!.keys.add(loadkey)
        loadx += loadkey.width
        if (loadx > minWidth) minWidth = loadx
    }

    private fun loadRow(row: JsonParse.JsonNode, pos: Int) {
        loadx = 0
        loadcurrentRow = Row(this)
        rows.add(loadcurrentRow!!)
        for (i in 0 until row.len()) {
            loadKey(row[i], pos + if (i == 0) 1 else if (i == row.len() - 1) 3 else 2)
        }
        loady += loadcurrentRow!!.keySize
    }

    class Row(parent: KeybModel) {
        var keySize: Int
        var keys = ArrayList<Key>()

        init {
            keySize = parent.keySize
        }
    }

}