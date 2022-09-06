package com.vladgba.keyb

import android.content.Context
import android.text.TextUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class Keyboard(context: Context, jd: String, portrait: Boolean) {
    @JvmField
    val rows = ArrayList<Row>()
    private val keys: ArrayList<Key>
    @JvmField
    var shifted = false
    private var dWidth = 0
    private var dHeight = 0
    var height = 0
        private set
    var minWidth = 0
        private set
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
        loadKeyboard(jd)
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

    fun getKeys(): List<Key> {
        return keys
    }

    fun setShifted(shiftState: Boolean) {
        shifted = shiftState
    }

    private fun loadKeyboard(jd: String) {
        Log.d("json", jd)
        loadx = 0
        loady = 0
        try {
            val json = JSONObject(jd).getJSONArray("keyb")
            for (i in 0 until json.length()) {
                val pos = if (i == 0) 0 else if (i == json.length() - 1) 6 else 3
                loadRow(json.getJSONArray(i), pos)
            }
            height = loady
        } catch (e: JSONException) {
            Log.e("PSR", e.message!!)
        }
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

    class Row(parent: Keyboard) {
        var width: Int
        @JvmField
        var height: Int
        @JvmField
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
        var cursor = false
        var rand: Array<String?>? = null

        init {
            height = parent!!.height
            width = parent.width
        }

        constructor(parent: Row?, x: Int, y: Int, jdata: JSONObject, pos: Int) : this(parent) {
            this.x = x
            this.y = y
            try {
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