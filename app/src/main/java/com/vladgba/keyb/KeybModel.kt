package com.vladgba.keyb

import android.content.Context
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class KeybModel(context: Context, jsonName: String, portrait: Boolean) {
    val rows = ArrayList<Row>()
    val keys: ArrayList<Key>
    var shifting = false
    private var dWidth = 0
    private var dHeight = 0
    var height = 0
    var minWidth = 0
    private var loadx = 0
    private var loady = 0
    private var loadcurrentRow: Row? = null

    init {
        val dm = context.resources.displayMetrics
        val displayWidth = dm.widthPixels
        val displayHeight = dm.heightPixels
        val sp = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        if (portrait) {
            val size = sp.getString("size", "10")!!.toFloat()
            val lowerSize = min(displayWidth, displayHeight)
            dWidth = (lowerSize / size).toInt()
            dHeight = dWidth
        } else {
            val size = sp.getString("sizeland", "20")!!.toFloat()
            val biggerSize = max(displayWidth, displayHeight)
            dWidth = ceil((biggerSize / size).toDouble()).toInt()
            dHeight = dWidth
        }
        keys = ArrayList()

        Log.d("json", jsonName)
        loadx = 0
        loady = 0
        try {
            val json = JSONObject(loadKeybLayout(jsonName)).getJSONArray("keyb")
            for (i in 0 until json.length()) {
                val pos = if (i == 0) 0 else if (i == json.length() - 1) 6 else 3
                loadRow(json.getJSONArray(i), pos)
            }
            height = loady
        } catch (e: JSONException) {
            Log.e("PSR", e.message!!)
        }
    }

    fun loadKeybLayout(name: String): String {
        val sdcard = Environment.getExternalStorageDirectory()
        val file = File(sdcard, "$name.json")
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
        } catch (e: IOException) {
            Log.d("Keyb", "Error")
            Log.d("Keyb", e.message!!)
        }
        return ""
    }

    fun resize(newWidth: Int) {
        for (row in rows) {
            val numKeys = row.keys.size
            var totalWidth = 0
            for (keyIndex in 0 until numKeys) {
                val key = row.keys[keyIndex]
                totalWidth += key.width
            }
            if (totalWidth > newWidth) {
                var x = 0
                val scaleFactor = newWidth.toFloat() / totalWidth
                for (keyIndex in 0 until numKeys) {
                    val key = row.keys[keyIndex]
                    key.width *= scaleFactor.toInt()
                    key.x = x
                    x += key.width
                }
            }
        }
        minWidth = newWidth
    }

    fun getKey(x: Int, y: Int): Key? {
        var mr = 0
        for (i in rows.indices) {
            val row = rows[i]
            if (row.height + mr >= y) {
                var mk = 0
                for (j in row.keys.indices) {
                    val k = row.keys[j]
                    if (k.width + mk >= x) return k
                    mk += k.width
                }
                break
            }
            mr += row.height
        }
        return null
    }

    fun setShifted(shiftState: Boolean) {
        shifting = shiftState
    }

    private fun loadKey(jdata: JSONObject, pos: Int) {
        val loadkey = Key(loadcurrentRow, loadx, loady, jdata, pos)
        if (loadkey.codes == null) return
        keys.add(loadkey)
        loadcurrentRow!!.keys.add(loadkey)
        loadx += loadkey.width
        if (loadx > minWidth) minWidth = loadx
    }

    @Throws(JSONException::class)
    private fun loadRow(row: JSONArray, pos: Int) {
        loadx = 0
        loadcurrentRow = Row(this)
        rows.add(loadcurrentRow!!)
        for (i in 0 until row.length()) {
            val keypos = pos + if (i == 0) 1 else if (i == row.length() - 1) 3 else 2
            loadKey(row.getJSONObject(i), keypos)
        }
        loady += loadcurrentRow!!.height
    }

    class Row(parent: KeybModel) {
        var width: Int
        var height: Int
        var keys = ArrayList<Key>()

        init {
            width = parent.dWidth
            height = parent.dHeight
        }
    }

    class Key(parent: Row?) {
        var codes: IntArray? = null
        var label: CharSequence? = null
        var width: Int
        var height: Int
        var x = 0
        var y = 0
        var repeat = false
        var text: CharSequence? = null
        var lang: CharSequence? = null
        var extChars: CharSequence? = null
        var forward = 0
        var backward = 0
        var stylepos = ""
        var bg = ""
        var cursor = false
        var rand: Array<String?>? = null
        private var options: JSONObject? = null

        init {
            height = parent!!.height
            width = parent.width
        }

        constructor(parent: Row?, x: Int, y: Int, jdata: JSONObject, pos: Int) : this(parent) {
            this.x = x
            this.y = y
            try {
                options = jdata;
                label = if (jdata.has("key")) jdata.getString("key") else ""
                codes = intArrayOf(if (jdata.has("code")) jdata.getInt("code") else 0)
                if (codes!![0] == 0 && !TextUtils.isEmpty(label)) codes!![0] = label!![0].code
                width = parent!!.width * if (jdata.has("size")) jdata.getInt("size") else 1
                extChars = if (jdata.has("ext")) jdata.getString("ext") else ""
                if (extChars!!.isNotEmpty()) extChars = padExtChars(extChars, pos)
                cursor = jdata.has("cur") && jdata.getInt("cur") == 1
                repeat = jdata.has("repeat") && jdata.getInt("repeat") == 1
                text = if (jdata.has("text")) jdata.getString("text") else ""
                lang = if (jdata.has("lang")) jdata.getString("lang") else null
                forward = if (jdata.has("forward")) jdata.getInt("forward") else 0
                backward = if (jdata.has("backward")) jdata.getInt("backward") else 0
                stylepos = if (jdata.has("stylepos")) jdata.getString("stylepos") else ""
                bg = if (jdata.has("bg")) jdata.getString("bg") else ""
                val rands = if (jdata.has("rand")) jdata.getJSONArray("rand") else null
                if (rands == null) {
                    rand = null
                } else {
                    rand = arrayOfNulls(rands.length())
                    for (i in 0 until rands.length()) {
                        rand!![i] = rands.getString(i)
                    }
                }
            } catch (e: JSONException) {
                Log.d("Key", e.message!!)
                return
            }
            this.height = parent.width
        }

        fun getStr(s: String): String {
            return if (options!!.has(s)) options!!.getString(s) else ""
        }

        fun getInt(s: String): Int {
            return if (options!!.has(s)) options!!.getInt(s) else 0
        }

        fun getBool(s: String): Boolean {
            return options!!.has(s) && options!!.getInt(s) == 1
        }

        private fun padExtChars(chars: CharSequence?, pos: Int): CharSequence {
            val modes = arrayOf(
                intArrayOf(-4, 1, -1, 2),
                intArrayOf(-3, 5),
                intArrayOf(-3, 1, -1, 2),
                intArrayOf(-1, 2, -1, 1, 2),
                intArrayOf(8),
                intArrayOf(2, -1, 1, -1, 2),
                intArrayOf(-1, 2, -1, 1),
                intArrayOf(5),
                intArrayOf(2, -1, 1)
            )
            val sb = StringBuilder()
            val curv = modes[pos - 1]
            var p = 0
            for (i in curv) {
                if (p >= chars!!.length) break
                if (i > 0) sb.append(chars.subSequence(p, min(i.let { p += it; p }, chars.length))) else sb.append(
                    String(
                        CharArray(-i)
                    ).replace("\u0000", " ")
                )
            }
            return sb.toString()
        }
    }
}