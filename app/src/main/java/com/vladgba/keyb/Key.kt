package com.vladgba.keyb

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlin.collections.ArrayList
import java.util.*
import kotlin.math.*

class Key(var c: KeybController, parent: KeybModel.Row?, x: Int, y: Int, jdata: Map<String, Any>, pos: Int) {
    private val angPos = intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)
    var extCharsRaw = ""
    var codes: IntArray? = null
    var label: CharSequence? = null
    var x = 0
    var y = 0
    var width: Int = 0
    var height: Int = 64
    var repeat = false
    var text: CharSequence? = null
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
    val runnable = Runnable { longPress(c) }

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
            height = parent!!.keySize

            parseExt(getStr("ext"))
            if (!extChars[0].isNullOrEmpty()) padExtChars(pos)

            repeat = getBool("repeat")
            val rands = if (options!!.containsKey("rand")) (options!!.getValue("rand") as ArrayList<String>) else null
            if (rands != null) {
                rand = arrayOfNulls(rands.size)
                for (i in 0 until rands.size) rand!![i] = rands[i]
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
            if (str[i].code < 56320) hi++
            extChars[hi] = "" + (extChars[hi] ?: "") + str[i].toString()
        }
    }

    fun getStr(s: String): String {
        return if (options!!.containsKey(s)) (options!!.getValue(s) as String) else ""
    }

    fun getInt(s: String): Int {
        try {
            return if (options!!.containsKey(s)) (options!!.getValue(s) as String).toInt() else 0
        } catch (_: Exception) {
            return (options!!.getValue(s) as String)[0].code
        }
    }

    fun getBool(s: String): Boolean {
        return options!!.containsKey(s) && !arrayOf(0, null, "", " ").contains(options!!.getValue(s) as String)
    }

    private fun padExtChars(pos: Int) {
        Log.d("pos", pos.toString())
        val nExtChars = arrayOfNulls<CharSequence>(8)
        val modes = arrayOf(
            intArrayOf(5, 7, 8),
            intArrayOf(4, 5, 6, 7, 8),
            intArrayOf(4, 6, 7),
            intArrayOf(2, 3, 5, 7, 8),
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            intArrayOf(1, 2, 4, 6, 7),
            intArrayOf(2, 3, 5),
            intArrayOf(1, 2, 3, 4, 5),
            intArrayOf(1, 2, 4)
        )
        val curv = modes[pos - 1]
        for (i in 0 until curv.size) {
            Log.d("pos = ", i.toString())
            Log.d("pos2 = ", curv[i].toString())
            if (i >= extChars.size) break
            nExtChars[curv[i]-1] = extChars[i]
        }
        extChars = nExtChars
    }

    fun longPress(c: KeybController) {
        if (getInt("hold") == 0) return
        longPressed = true
        if (getStr("hold").length > 1) c.onText(getStr("hold"))
        else c.onKey(getInt("hold"))
    }

    fun getExtPos(x: Int, y: Int): Int {
        if (abs(pressX - x) < c.offset && abs(pressY - y) < c.offset) return 0
        val angle = Math.toDegrees(atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        return angPos[ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
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

    fun recordAction(curX: Int, curY: Int): Boolean {
        if (!getBool("record")) return false
        if (arrayOf(1, 3, 6, 8).contains(getExtPos(curX, curY))) {
            if (c.recKey != null) c.recKey!!.record.clear()
        } else if (arrayOf(2, 4, 5, 7).contains(getExtPos(curX, curY))) {
            if (arrayOf(4, 5).contains(getExtPos(curX, curY))) {
                c.recKey = this
                c.recKey!!.recording = true
            } else {
                if (c.recKey == null) return true
                c.recKey!!.recording = false
            }
        } else {
            if (c.recKey == null || c.recKey!!.record.size == 0) return true
            for (i in 0 until c.recKey!!.record.size) {
                c.recKey!!.record.get(i).replay(c)
            }
        }
        return true
    }

    fun swipeAction(r: Int, t: Int, s: String, add: Boolean): Int {
        c.onKey(if (getInt(s) == 0) codes!![0] else getInt(s))
        c.vibrate(this, "vibtick")
        return if (add) r + t else r - t

    }

    fun repeating(curX: Int, curY: Int) {

        if (!cursorMoved && (curX - c.horizontalTick > pressX || curX + c.horizontalTick < pressX || curY - c.verticalTick > pressY || curY + c.verticalTick < pressY)) {
            cursorMoved = true
        }
        while (true) {
            if (curX - c.horizontalTick > relX) relX =
                swipeAction(relX, c.horizontalTick, "right", true)
            else if (curX + c.horizontalTick < relX) relX =
                swipeAction(relX, c.horizontalTick, "left", false)
            else break
        }
        while (true) {
            if (curY - c.verticalTick > relY) relY =
                swipeAction(relY, c.verticalTick, "bottom", true)
            else if (curY + c.verticalTick < relY) relY =
                swipeAction(relY, c.verticalTick, "top", false)
            else break
        }
    }

    fun procExtChars(curX: Int, curY: Int) {
        if (extCharsRaw.isNotEmpty()) {
            relX = curX
            relY = curY
            val tmpPos = charPos
            charPos = getExtPos(curX, curY)
            if (charPos != 0 && tmpPos != charPos) c.vibrate(this, "vibext")
        }
    }

    fun clipboardAction(): Boolean {
        if (!getBool("clipboard")) return false
        try {
            if (c.ctrlPressed()) {
                val tx = c.currentInputConnection.getSelectedText(0).toString()
                if (c.shiftPressed()) {
                    for (i in 0 until tx.length) c.onText("\\u" + tx[i].code.toString(16).uppercase().padStart(4, '0'))
                } else {
                    if (tx.indexOf("u") >= 0) {
                        val arr = tx.split("\\u")
                        for (i in 1 until arr.size) c.onText(arr[i].toInt(16).toChar().toString())
                    } else if (tx.indexOf(" ") >= 0) {
                        val arr = tx.split(" ")
                        for (i in 1 until arr.size) c.onText(arr[i].toInt().toChar().toString())
                    } else {
                        c.onText(tx.toInt().toChar().toString())
                    }
                }
                return true
            }
            if (charPos < 1) return true
            if (c.shiftPressed()) {
                extChars[charPos - 1] = c.currentInputConnection.getSelectedText(0)
            } else {
                if (extChars[charPos - 1] == null) return true
                c.onText(extChars[charPos - 1].toString())
            }
        } catch (_: Exception) { }
        return true
    }

    fun modifierAction(): Boolean {
        if (!getBool("mod")) return false

        if ((c.mod and getInt("modmeta")) > 0) {
            c.mod = c.mod and getInt("modmeta").inv()
            c.keyShiftable(KeyEvent.ACTION_UP, getInt("modkey"))
            c.keybView!!.repMod()
        } else {
            c.mod = c.mod or getInt("modmeta")
            c.keyShiftable(KeyEvent.ACTION_DOWN, getInt("modkey"))
            c.keybView!!.repMod()
        }
        return true
    }

    fun shiftAction(): Boolean {
        if (!c.shiftPressed() || !getBool("shift")) return false
        try {
            val tx = c.currentInputConnection.getSelectedText(0).toString()
            val res = when (getStr("shift")) {
                "upperAll" -> tx.uppercase(Locale.ROOT)
                "lowerAll" -> tx.lowercase(Locale.ROOT)
                else -> ""
            }
            if (res != "") c.onText(res)
        } catch (_: Exception) {}
        return true
    }
}