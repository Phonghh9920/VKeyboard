package com.vladgba.keyb

import android.graphics.Paint
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import kotlin.collections.ArrayList
import java.util.*
import kotlin.math.*

class Key(var c: KeybController, parent: KeybModel.Row?, x: Int, y: Int, jdata: Map<String, Any>, pos: Int): KeyAction(c){
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
    val mpr = MediaPlayer()
    val mr = MediaPlayer()

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
            if (codes!![0] == 0 && label!!.length > 0 && getStr("text").length < 1) {
                if (label!!.length > 1) text = label
                else codes!![0] = label!![0].code
            } else {
                text = getStr("text")
            }

            width = (parent!!.keySize * if (getStr("size") == "") 1f else getStr("size").toFloat()).toInt()
            height = parent.keySize

            parseExt(getStr("ext"))
            if (!extChars[0].isNullOrEmpty()) padExtChars(pos)

            repeat = getBool("repeat")
            if (options!!.containsKey("rand")) {
                val rands = (options!!.getValue("rand") as ArrayList<String>)
                rand = arrayOfNulls(rands.size)
                for (i in 0 until rands.size) rand!![i] = rands[i]
            }

            if (getBool("sound-p")) {
                try {
                    mpr.setDataSource(getStr("sound-p"))
                    mpr.prepare()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Sound file " + getStr("sound-p") + "not loaded", Toast.LENGTH_LONG).show()
                }
            }
            if (getBool("sound-r")) {
                try {
                    mr.setDataSource(getStr("sound-r"))
                    mr.prepare()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Sound file " + getStr("sound-r") + " not loaded", Toast.LENGTH_LONG).show()
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
            return if (options!!.containsKey(s)) (options!!.getValue(s) as String)[0].code else 0
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
        if (charPos == 0) c.handler.removeCallbacks(runnable)
        val angle = Math.toDegrees(atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        Log.d("ext", angle.toString())
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
        if (!cursorMoved) {
            cursorMoved = true
            c.handler.removeCallbacks(runnable)
        }
        c.onKey(if (getInt(s) == 0) codes!![0] else getInt(s))
        c.vibrate(this, "vibtick")
        return if (add) r + t else r - t

    }

    fun repeating(curX: Int, curY: Int) {
        if(!getBool("repeat")) return
        while (true) {
            if (curX - c.horTick > relX) relX = swipeAction(relX, c.horTick, "right", true)
            else if (curX + c.horTick < relX) relX = swipeAction(relX, c.horTick, "left", false)
            else break
        }
        while (true) {
            if (curY - c.verTick > relY) relY = swipeAction(relY, c.verTick, "bottom", true)
            else if (curY + c.verTick < relY) relY = swipeAction(relY, c.verTick, "top", false)
            else break
        }
    }

    fun procExtChars(curX: Int, curY: Int) {
        Log.d("extChars", extCharsRaw)
        if (extCharsRaw.isNotEmpty()) {
            relX = curX
            relY = curY
            val tmpPos = charPos
            charPos = getExtPos(curX, curY)
            Log.d("extCharspos", charPos.toString())
            if (charPos != 0 && tmpPos != charPos) c.vibrate(this, "vibext")
        }
    }

    fun clipboardAction(): Boolean {
        if (!getBool("clipboard")) return false
        try {
            if (c.ctrlPressed()) {
                val tx = c.currentInputConnection.getSelectedText(0).toString()
                if (c.shiftPressed()) {
                    char2utfEscape(tx)
                } else {
                    Log.d("clip", tx)
                    if (tx.indexOf("u") >= 0) utf2char(tx)
                    else if (tx.indexOf("b") >= 0) bin2char(tx)
                    else if (tx.indexOf("x") >= 0) hex2char(tx)
                    else if (tx.indexOf("0") == 0) oct2char(" " + tx) // " 0555 0456", " 0123"
                    else if (tx.indexOf(" ") >= 0) dec2char(tx)
                    else dec2char(tx)
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
        } catch (e: Exception) {
            Log.d("clip", e.stackTraceToString())
        }
        return true
    }

    fun modifierAction(): Boolean {
        if (!getBool("mod")) return false

        if ((c.mod and getInt("modmeta")) > 0) {
            c.mod = c.mod and getInt("modmeta").inv()
            c.keyShiftable(KeyEvent.ACTION_UP, getInt("modkey"))
        } else {
            c.mod = c.mod or getInt("modmeta")
            c.keyShiftable(KeyEvent.ACTION_DOWN, getInt("modkey"))
        }
        c.keybView!!.repMod()
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

    fun press(curX: Int, curY: Int) {
        if (c.getVal(c.sett, "debug", "") == "1") {
            val p = Paint().also { it.color = 0x0fff0000}
            c.keybLayout!!.canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }

        if (mpr.isPlaying) {
            mpr.stop()
            mpr.prepare()
        }
        mpr.start()

        if (modifierAction()) return
        c.handler.postDelayed(runnable, c.longPressTime)
        longPressed = false
        c.vibrate(this, "vibpress")
        pressX = curX
        pressY = curY
        relX = -1
        relY = -1
        cursorMoved = false
        charPos = 0
        if (repeat || extCharsRaw.isNotEmpty()) {
            relX = curX
            relY = curY
        }
    }

    fun drag(curX: Int, curY: Int) {
        if (getBool("mod")) return
        if (getBool("clipboard")) {
            charPos = getExtPos(curX, curY)
            return
        }
        if (relX < 0) return // Not have alternative behavior
        repeating(curX, curY)
        procExtChars(curX, curY)
    }

    fun release(curX: Int, curY: Int, pid: Int) {
        if (mr.isPlaying) {
            mr.stop()
            mr.prepare()
        }
        mr.start()
        if(longPressed) return
        if (charPos == 0) c.vibrate(this, "vibrelease")
        if (getBool("mod")) {
            if (c.lastpid != pid) modifierAction()
            return
        }
        if (prExtChars() || curY == 0 || cursorMoved) return
        if (recordAction(curX, curY) || clipboardAction() || shiftAction()) return

        if (getBool("app")) c.startActivity(c.packageManager.getLaunchIntentForPackage(getStr("app")))

        if (getBool("sudo")) suExec(getStr("sudo"))

        if (rand != null && rand!!.isNotEmpty()) {
            return c.onText(rand!![Random().nextInt(rand!!.size)]!!)
        }
        if (langSwitch()) return
        if (textInput()) return
        if (repeat && !cursorMoved) return c.onKey(codes!![0])
        if (relX < 0 || charPos == 0) return c.onKey(codes?.get(0) ?: 0)
    }

    private fun textInput(): Boolean {
        if (text != null && text!!.length > 0) {
            if (text!!.length == 1) c.onText(c.getShifted(text!![0].code, c.shiftPressed()).toChar().toString())
            else c.onText(text!!)
            if (getInt("pos") < 0) for (i in 1..-getInt("pos")) c.onKey(-21)
            else if (getInt("pos") >= 0) for (i in 1..text!!.length - getInt("pos")) c.onKey(-21)
            return true
        }
        return false
    }

    private fun langSwitch(): Boolean {
        if (getStr("lang").isNotEmpty() && charPos == 0) {
            c.currentLayout = getStr("lang")
            c.reload()
            return true
        }
        return false
    }

    private fun prExtChars(): Boolean {
        if (extCharsRaw.length < 1 || charPos < 1) return false
        val textIndex = extChars[charPos - 1]
        if (textIndex.isNullOrEmpty() || textIndex == " ") return true
        if (textIndex.length > 1) c.onText(textIndex)
        else c.onKey(c.getFromString(textIndex.toString())[0])
        return true
    }
}