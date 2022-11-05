package com.vladgba.keyb

import android.os.SystemClock
import android.util.Log
import kotlin.math.min

class Key(c: KeybController, parent: KeybModel.Row?, x: Int, y: Int, jdata: Map<String, Any>, pos: Int) {
    var extCharsRaw = ""
    var codes: IntArray? = null
    var label: CharSequence? = null
    var x = 0
    var y = 0
    var width: Int = 0
    var height: Int = 64
    var repeat = false
    var text: CharSequence? = null
    var clipboard = arrayOfNulls<CharSequence>(8)
    var extChars = arrayOfNulls<CharSequence>(8)
    var rand: Array<String?>? = null
    private var options: Map<String, Any>? = null
    var record: ArrayList<Record> = ArrayList()
    var recording: Boolean = false

    var pressX = 0
    var pressY = 0
    var relX = -1
    var relY = -1
    var cursorMoved = false
    var charPos = 0
    var longPressed = false
    val runnable = Runnable { c.longPress(this) }

    init {
        this.x = x
        this.y = y
        try {
            options = jdata
            label = getStr("key")
            codes = intArrayOf(getInt("code"))
            if (codes!![0] == 0 && label!!.length > 0 && getStr("text").length<1) {
                if (label!!.length > 1) text = label
                else codes!![0] = label!![0].code
            } else {
                text = getStr("text")
            }

            width = (parent!!.keySize * if (getStr("size") == "") 1f else getStr("size").toFloat()).toInt()
            height = (parent!!.keySize * if (getStr("size") == "") 1f else getStr("size").toFloat()).toInt()

            parseExt(getStr("ext"))
            //if (extChars!!.isNotEmpty()) extChars = padExtChars(extChars, pos)

            repeat = getBool("repeat")
            val rands = if (options!!.containsKey("rand")) (options!!.getValue("rand") as ArrayList<String>) else null
            if (rands != null) {
                rand = arrayOfNulls(rands.size)
                for (i in 0 until rands.size) {
                    rand!![i] = rands[i]
                }
            }
            this.height = parent.keySize
        } catch (e: Exception) {
            Log.d("Key", e.message!!)
        }
    }

    private fun parseExt(str: String) {
        extCharsRaw = str
        var hi = -1
        for (i in str.indices) {
            Log.d("Symbole", str[i].code.toString())
            if (str[i].code > 255) {
                Log.d("Symbol", "is UTF")
                if (str[i].code < 56320) {
                    Log.d("Symbol", "and first")
                    hi++
                } else {
                    Log.d("Symbol", "and second")
                }
            } else {
                Log.d("Symbol", "ASCII: " + str[i])
                hi++
            }

            if (extChars[hi] == null) {
                extChars[hi] = str[hi].toString()
            } else {
                extChars[hi] = "" + extChars[hi] + str[i].toString()
            }
        }
    }

    fun getStr(s: String): String {
        return if (options!!.containsKey(s)) (options!!.getValue(s) as String) else ""
    }

    fun getInt(s: String): Int {
        return if (options!!.containsKey(s)) (options!!.getValue(s) as String).toInt() else 0
    }

    fun getBool(s: String): Boolean {
        return options!!.containsKey(s) && !arrayOf(0, null, "", " ").contains(options!!.getValue(s) as String)
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
            if (i > 0) sb.append(chars.subSequence(p, min(i.let { p += it; p }, chars.length)))
            else sb.append(String(CharArray(-i)).replace("\u0000", " "))
        }
        return sb.toString()
    }

    class Record(s: String) {
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
            if (keyIndex == 0) {
                SystemClock.sleep(50) // wait until sendkeyevent is processed
                keybCtl.onText(keyText)
            } else {
                keybCtl.keyShiftable(keyState, keyIndex, keyMod)
            }
        }
    }
}