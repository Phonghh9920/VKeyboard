package com.vladgba.keyb

import android.graphics.Paint
import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import kotlin.collections.ArrayList
import java.util.*
import kotlin.math.*
import android.os.*
import android.view.*

private const val MULTIBYTE_UTF = 56320

class Key(private var c: KeybCtl, private val parent: KeybModel.Row?, val x: Int, val y: Int, jsonData: JsonParse.JsonNode, pos: Int): KeyAction(c){
    private val angPos = intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)
    private val allocPositions = arrayOf(
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
    var extCharsRaw = ""
    var codes: IntArray? = null
    var label: CharSequence? = null
    var width: Int = 0
    var height: Int = 64
    var repeat = false
    var text: CharSequence? = null
    var extChars = arrayOfNulls<CharSequence>(8)
    var rand: Array<String?>? = null
    private var options: JsonParse.JsonNode? = null
    var record: ArrayList<Record> = ArrayList()
    var recording: Boolean = false
    private val mediaPressed = MediaPlayer()
    private val mediaReleased = MediaPlayer()

    var pressX = 0
    var pressY = 0
    var relX = -1
    var relY = -1
    var cursorMoved = false
    var charPos = 0
    var longPressed = false
    var hardPressed = false
    val longPressRunnable = Runnable { longPress() }

    init {
        try {
            options = jsonData
            label = getStr("key")
            codes = intArrayOf(getInt("code"))
            if (codes!![0] == 0 && label!!.isNotEmpty() && getStr("text").isEmpty()) {
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
            randomTexts()
            setSound(mediaPressed, "sound-p")
            setSound(mediaReleased, "sound-r")

            this.height = parent.keySize
        } catch (e: Exception) {
            Log.d("Key", e.message!!)
        }
    }

    private fun randomTexts() {
        if (!options!!.has("rand")) return
        val rands = options!!["rand"]
        rand = arrayOfNulls(rands.count())
        for (i in 0 until rands.count()) rand!![i] = rands[i].str()
    }

    private fun setSound(mpl: MediaPlayer, s: String) {
        if (getBool(s)) {
            try {
                mpl.setDataSource(getStr(s))
                mpl.prepare()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Sound file " + getStr(s) + " not loaded", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parseExt(str: String) {
        extCharsRaw = str
        var symbolIndex = -1
        for (i in str.indices) {
            if (str[i].code < MULTIBYTE_UTF) symbolIndex++
            extChars[symbolIndex] = "" + (extChars[symbolIndex] ?: "") + str[i].toString()
        }
    }

    fun getStr(s: String): String {
        return if (options!!.has(s)) options!![s].str() else ""
    }

    fun getInt(s: String): Int = try {
        if (options!!.has(s)) options!![s].num() else 0
    } catch (_: Exception) {
        if (options!!.has(s)) options!![s].str()[0].code else 0
    }

    fun getBool(s: String): Boolean = options!!.has(s) && !arrayOf(0, null, "", " ").contains(options!![s].str())

    private fun padExtChars(pos: Int) {
        Log.d("pos", pos.toString())
        val nExtChars = arrayOfNulls<CharSequence>(8)
        val currAlloc = allocPositions[pos - 1]
        for (i in currAlloc.indices) {
            if (i >= extChars.size) break
            nExtChars[currAlloc[i]-1] = extChars[i]
        }
        extChars = nExtChars
    }

    fun longPress() {
        if (getStr("hold").isEmpty()) return
        longPressed = true
        if (getStr("hold").length > 1) c.onText(getStr("hold"))
        else c.onKey(getInt("hold"))
    }
    
    fun hardPress(me: MotionEvent, pid: Int) {
        if (getInt("hard") == 0 || c.settings["hardPress"].str().isEmpty()) return
        if ((me.getPressure(pid)*1000).toInt() < c.settings["hardPress"].num()) return
        hardPressed = true
        if (getStr("hard").length > 1) c.onText(getStr("hard"))
        else c.onKey(getInt("hard"))
    }

    fun getExtPos(x: Int, y: Int): Int {
        if (abs(pressX - x) < c.offset && abs(pressY - y) < c.offset) return 0
        if (charPos == 0) c.handler.removeCallbacks(longPressRunnable)
        val angle = Math.toDegrees(atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        Log.d("ext", angle.toString())
        return angPos[ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
    }

    class Record(s: String) {
        private var keyIndex: Int = 0
        private var keyMod: Int = 0
        private var keyState: Int = 0
        var keyText: String = ""

        init {
            keyText = s
        }

        constructor(key: Int, mod: Int, state: Int): this("") {
            keyIndex = key
            keyMod = mod
            keyState = state
        }

        fun replay(keybCtl: KeybCtl) {
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
        val extPos = getExtPos(curX, curY)
        when (extPos) {
            in arrayOf(1, 3, 6, 8) -> c.recKey?.record?.clear()
            in arrayOf(4, 5) -> c.recKey = this.apply { recording = true }
            in arrayOf(2, 7) -> (c.recKey ?: return true).recording = false
            else -> {
                if (c.recKey == null || c.recKey!!.record.size == 0) return true
                for (i in 0 until c.recKey!!.record.size) c.recKey?.record?.get(i)?.replay(c)
            }
        }
        return true
    }

    fun swipeAction(r: Int, t: Int, s: String, add: Boolean): Int {
        preventLongPressOnMoved()
        c.onKey(if (getInt(s) == 0) codes!![0] else getInt(s))
        c.vibrate(this, "vibtick")
        return if (add) r + t else r - t

    }

    private fun preventLongPressOnMoved() {
        if (cursorMoved) return
        cursorMoved = true
        c.handler.removeCallbacks(longPressRunnable)
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

        if ((c.modifierState and getInt("modmeta")) > 0) {
            c.modifierState = c.modifierState and getInt("modmeta").inv()
            c.keyShiftable(KeyEvent.ACTION_UP, getInt("modkey"))
        } else {
            c.modifierState = c.modifierState or getInt("modmeta")
            c.keyShiftable(KeyEvent.ACTION_DOWN, getInt("modkey"))
        }
        c.picture?.repMod()
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
        if (c.getVal("debug", "") == "1") {
            val p = Paint().also { it.color = 0x0fff0000}
            c.keybLayout?.canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }

        if (mediaPressed.isPlaying) {
            mediaPressed.stop()
            mediaPressed.prepare()
        }
        mediaPressed.start()

        if (modifierAction()) return
        c.handler.postDelayed(longPressRunnable, c.longPressTime)
        longPressed = false
        hardPressed = false
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

    fun drag(curX: Int, curY: Int, me: MotionEvent, pid: Int) {
        if (getBool("mod")) return
        if (getBool("clipboard")) {
            charPos = getExtPos(curX, curY)
            return
        }
        
        if (longPressed || hardPressed) return // Not have alternative behavior
        hardPress(me, pid)
        if (relX < 0) return // Not have alternative behavior
        repeating(curX, curY)
        procExtChars(curX, curY)
    }

    fun release(curX: Int, curY: Int, pid: Int) {
        if (mediaReleased.isPlaying) {
            mediaReleased.stop()
            mediaReleased.prepare()
        }
        mediaReleased.start()
        if (longPressed || hardPressed) return
        if (charPos == 0) c.vibrate(this, "vibrelease")
        if (getBool("mod")) {
            if (c.lastPointerId != pid) modifierAction()
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
        if (text.isNullOrEmpty()) return false
        c.onText(if (text!!.length == 1) c.getShifted(text!![0].code, c.shiftPressed()).toChar().toString() else text!!)
        if (getStr("pos") == "") return true
        val pos = getInt("pos")
        for (i in 1..(-pos + if (pos < 0) 0 else text!!.length)) c.onKey(-21)
        return true
    }


    private fun langSwitch(): Boolean = if (getStr("lang").isNotEmpty() && charPos == 0) {
        c.apply { currentLayout = getStr("lang"); reloadLayout() }
        true
    } else false

    private fun prExtChars(): Boolean {
        if (extCharsRaw.length < 1 || charPos < 1) return false
        val textIndex = extChars[charPos - 1]
        if (textIndex.isNullOrEmpty() || textIndex == " ") return true
        if (textIndex.length > 1) c.onText(textIndex)
        else c.onKey(c.getFromString(textIndex.toString())[0])
        return true
    }

    operator fun get(s: String): String = if (options?.has(s) == true) options!![s].str()
        else if (parent?.has(s) == true) parent[s]
        else ""

    fun has(s: String): Boolean = options?.has(s) == true || parent?.has(s) == true
}
