package com.vladgba.keyb

import org.json.*
import java.io.*
import kotlin.math.*
import android.os.*
import android.graphics.*
import android.content.Context
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.widget.Toast

class KeybModel(context: KeybController, jsondat: String, portrait: Boolean, isJsonData: Boolean) {
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
    public var loaded = false
    public var bitmap: Bitmap? = null
    public var canv: Canvas? = null
    public var lastdate: Long = 0

    init {
        loaded = false
        val dm = context.resources.displayMetrics
        val displayWidth = dm.widthPixels
        val displayHeight = dm.heightPixels
        
        keys = ArrayList()

        Log.d("json", jsondat)
        loadx = 0
        loady = 0
        try {
            val glob = JsonParse.map(if (isJsonData) jsondat else loadKeybLayout(jsondat))
            val json = glob.getValue("keyb") as ArrayList<Any>
            val size = (glob.getValue("keyCount") as String).toFloat()
            if (portrait) {
                val lowerSize = min(displayWidth, displayHeight)
                dWidth = (lowerSize / size).toInt()
                dHeight = dWidth
            } else {
                val biggerSize = max(displayWidth, displayHeight)
                dWidth = ceil((biggerSize / size).toDouble()).toInt()
                dHeight = dWidth
            }
            for (i in 0 until json.size) {
                val pos = if (i == 0) 0 else if (i == json.size - 1) 6 else 3
                loadRow(json[i] as ArrayList<Any>, pos)
            }
            height = loady
            loaded = true
            context.erro = ""
        } catch (e: Exception) {
            loaded = false
            if (!isJsonData) context.erro = e.message!!
            Log.e("PSR", e.message!!)
        }
    }
    
    constructor(context: KeybController, jsonName: String, portrait: Boolean): this(context, jsonName, portrait, false) {
    }

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

    private fun loadKey(jdata: Map<String, Any>, pos: Int) {
        val loadkey = Key(loadcurrentRow, loadx, loady, jdata, pos)
        keys.add(loadkey)
        loadcurrentRow!!.keys.add(loadkey)
        loadx += loadkey.width
        if (loadx > minWidth) minWidth = loadx
    }

    private fun loadRow(row: ArrayList<Any>, pos: Int) {
        loadx = 0
        loadcurrentRow = Row(this)
        rows.add(loadcurrentRow!!)
        for (i in 0 until row.size) {
            val keypos = pos + if (i == 0) 1 else if (i == row.size - 1) 3 else 2
            loadKey(row[i] as Map<String, Any>, keypos)
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

    class KeyRecord(s: String) {
        var keyIndex: Int = 0
        var keyMod: Int = 0
        var keyState: Int = 0
        var keyText: String = ""

        init {
            keyText = s
        }

        constructor(key: Int, mod: Int, state: Int): this("") {
            keyIndex = key
            keyMod = mod
            keyState = state
        }

        fun replay(keybCtl: KeybController) {
            if (keyIndex == 0){
                SystemClock.sleep(50); // wait until sendkeyevent is processed
                keybCtl.onText(keyText)
            } else {
                keybCtl.keyShiftable(keyState, keyIndex, keyMod)
            }
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
        var clipboard = arrayOfNulls<CharSequence>(8)
        var extChars: CharSequence? = ""
        var rand: Array<String?>? = null
        private var options: Map<String, Any>? = null
        public var record: ArrayList<KeyRecord> = ArrayList()
        public var recording: Boolean = false

        init {
            height = parent!!.height
            width = parent.width
        }

        constructor(parent: Row?, x: Int, y: Int, jdata: Map<String, Any>, pos: Int) : this(parent) {
            this.x = x
            this.y = y
            try {
                options = jdata;
                label = getStr("key")
                codes = intArrayOf(getInt("code"))
                if (codes!![0] == 0 && label!!.length > 0 && getStr("text").length<1) {
                    if (label!!.length > 1) text = label
                    else codes!![0] = label!![0].code
                } else {
                    text = getStr("text")
                }
//TODO: height
                width = (parent!!.width * if (getStr("size") == "") 1f else getStr("size").toFloat()).toInt()
                
                extChars = getStr("ext")
                if (extChars!!.isNotEmpty()) extChars = padExtChars(extChars, pos)
                repeat = getBool("repeat")
                val rands = if (options!!.containsKey("rand")) (options!!.getValue("rand") as ArrayList<String>) else null
                if (rands != null) {
                    rand = arrayOfNulls(rands.size)
                    for (i in 0 until rands.size) {
                        rand!![i] = rands[i]
                    }
                }
            } catch (e: Exception) {
                Log.d("Key", e.message!!)
                return
            }
            this.height = parent.width
        }

        fun getStr(s: String): String {
            return if (options!!.containsKey(s)) (options!!.getValue(s) as String) else ""
        }

        fun getInt(s: String): Int {
            return if (options!!.containsKey(s)) (options!!.getValue(s) as String).toInt() else 0
        }

        fun getBool(s: String): Boolean {
            return options!!.containsKey(s) && (options!!.getValue(s) as String) != "0"
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
                    String(CharArray(-i)).replace("\u0000", " ")
                )
            }
            return sb.toString()
        }
    }
}