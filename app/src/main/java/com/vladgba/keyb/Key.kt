package com.vladgba.keyb

import android.media.MediaPlayer
import android.widget.Toast
import kotlin.collections.ArrayList
import java.util.*
import kotlin.math.*
import android.os.*
import android.view.*

const val MULTIBYTE_UTF = 56320
const val KEY_KEY = "key"
const val KEY_CODE = "code"
const val KEY_TEXT = "text"
const val KEY_WIDTH = "size"
const val KEY_LANG_SWITCH = "lang"
const val KEY_REPEATABLE = "repeat"
const val KEY_ADDIT_CHARS = "ext"
const val KEY_SHIFT = "shift"
const val KEY_MOD = "mod"
const val KEY_MOD_CODE = "modkey"
const val KEY_MOD_META = "modmeta"
const val CURSOR_OFFSET_POS = "pos"
const val KEY_SOUND_PRESS = "sound-p"
const val KEY_SOUND_RELEASE = "sound-r"
const val VIBRATE_TICK = "vibtick"
const val KEY_VIBRATE_ON_PRESS = "vibpress"
const val KEY_VIBRATE_ON_RELEASE = "vibrelease"
const val KEY_VIBRATE_ON_EXT = "vibext"
const val KEY_HOLD = "hold"
const val KEY_HARD_PRESS = "hard"
const val KEY_APP = "app"
const val KEY_SU_ACTION = "sudo"
const val KEY_CLIPBOARD = "clipboard"
const val KEY_RECORD = "record"
const val KEY_RANDOM = "rand"

class Key(
    private var c: KeybCtl,
    private val parent: KeybModel.Row,
    var x: Int,
    val y: Int,
    val options: LayoutParse.DataNode,
    pos: Int
) : KeyAction(c) {
    private val allocPositions = arrayOf(
        byteArrayOf(5, 7, 8),
        byteArrayOf(4, 5, 6, 7, 8),
        byteArrayOf(4, 6, 7),
        byteArrayOf(2, 3, 5, 7, 8),
        byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
        byteArrayOf(1, 2, 4, 6, 7),
        byteArrayOf(2, 3, 5),
        byteArrayOf(1, 2, 3, 4, 5),
        byteArrayOf(1, 2, 4)
    )
    var extCharsRaw = ""
    var codes = 0
    var label: CharSequence? = null
    var width: Int = 64
    var height: Int = 64
    var repeat = false
    var text: CharSequence? = null
    var extChars = arrayOfNulls<CharSequence>(8)
    var rand: Array<String?>? = null
    var record: ArrayList<Record> = ArrayList()
    var recording: Boolean = false
    private val mediaPressed = Media()
    private val mediaReleased = Media()

    var pressX = 0
    var pressY = 0
    var relX = -1
    var relY = -1
    var cursorMoved = false
    var charPos = 0
    var longPressed = false
    var hardPressed = false
    val longPressRunnable = Runnable { longPress() }

    class Media {
        enum class State {
            NOT_LOADED, READY, PLAYING
        }

        var state = State.NOT_LOADED
        val media = MediaPlayer()
        fun setSound(c: KeybCtl, k: Key, s: String) {
            try {
                if (k.options.str(s).isBlank()) return
                media.setDataSource(k.options.str(s))
                media.prepare()
                state = State.READY
            } catch (e: Exception) {
                state = State.NOT_LOADED
                Toast.makeText(c, "Sound file " + k.options.str(s) + " not loaded", Toast.LENGTH_LONG).show()
            }
        }

        fun play() {
            if (state == State.NOT_LOADED) return
            if (state == State.PLAYING) {
                media.stop()
                media.prepare()
            } else {
                state = State.PLAYING
                media.start()
            }
        }
    }

    init {
        try {
            label = options.str(KEY_KEY)
            codes = options.num(KEY_CODE).toInt()
            if (codes == 0 && label!!.isNotEmpty() && options.str(KEY_TEXT).isEmpty()) {
                if (label!!.length > 1) text = label
                else codes = label!![0].code
            } else {
                text = options.str(KEY_TEXT)
            }
            //width = (parent.height * if (options.str(KEY_WIDTH).isBlank()) 1f else options.str(KEY_WIDTH).toFloat()).toInt()
            height = parent.height

            parseExt(options.str(KEY_ADDIT_CHARS))
            if (!extChars[0].isNullOrEmpty()) padExtChars(pos)

            repeat = options.bool(KEY_REPEATABLE)
            randomTexts()
            mediaPressed.setSound(c, this, KEY_SOUND_PRESS)
            mediaReleased.setSound(c, this, KEY_SOUND_RELEASE)

        } catch (e: Exception) {
            c.prStack(e)
        }
    }

    private fun randomTexts() {
        if (!options.has(KEY_RANDOM) || options.str(KEY_RANDOM).isBlank()) return
        val rands = options[KEY_RANDOM]
        rand = arrayOfNulls(rands.childs.size)
        for (i in 0 until rands.childs.size) {
            rand!![i] = rands.str(i)
            c.log(rands.str(i))
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

    private fun padExtChars(pos: Int) {
        val nExtChars = arrayOfNulls<CharSequence>(8)
        val currAlloc = allocPositions[pos - 1]
        for (i in currAlloc.indices) {
            if (i >= extChars.size) break
            nExtChars[currAlloc[i] - 1] = extChars[i]
        }
        extChars = nExtChars
    }

    fun longPress() {
        if (options.str(KEY_HOLD).isBlank()) return
        longPressed = true
        c.onText(options.str(KEY_HOLD))
    }

    fun hardPress(me: MotionEvent, pid: Int) {
        if (options.str(KEY_HARD_PRESS).isBlank() || Settings.str("hardPress").isBlank()) return
        if ((try { me.getPressure(pid) } catch (_: IllegalArgumentException) { 0f } * 1000).toInt() < Settings.num("hardPress")) return
        hardPressed = true
        c.onText(options.str(KEY_HARD_PRESS))
    }

    fun getExtPos(x: Int, y: Int): Int {
        if (abs(pressX - x) < Settings.offset && abs(pressY - y) < Settings.offset) return 0
        if (charPos == 0) c.handler.removeCallbacks(longPressRunnable)
        val angle = Math.toDegrees(atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        return intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)[ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
    }


    class Record(var keyText: String?) {
        private var keyIndex: Int = 0
        private var keyMod: Int = 0
        private var keyState: Int = 0

        constructor(key: Int, mod: Int, state: Int) : this(null) {
            keyIndex = key
            keyMod = mod
            keyState = state
        }

        fun replay(keybCtl: KeybCtl) {
            if (keyText.isNullOrEmpty()) {
                keybCtl.keyShiftable(keyState, keyIndex, keyMod)
            } else {
                SystemClock.sleep(50) // wait until sendkeyevent is processed
                keybCtl.onText(keyText!!)
            }
        }
    }

    fun recordAction(curX: Int, curY: Int): Boolean {
        if (options.str(KEY_RECORD).isBlank()) return false
        val extPos = getExtPos(curX, curY)
        when (extPos) {
            in arrayOf(1, 3, 6, 8) -> record.clear()
            in arrayOf(4, 5) -> c.recKey = this.apply { recording = true }
            in arrayOf(2, 7) -> (c.recKey ?: return true).recording = false
            else -> {
                if (record.size == 0) return true
                for (i in 0 until record.size) record.get(i).replay(c)
            }
        }
        return true
    }

    fun swipeAction(r: Int, t: Int, s: String, add: Boolean): Int {
        preventLongPressOnMoved()
        c.onKey(if (options.num(s) == 0) codes else options.num(s))
        c.vibrate(this, VIBRATE_TICK)
        return if (add) r + t else r - t

    }

    private fun preventLongPressOnMoved() {
        if (cursorMoved) return
        cursorMoved = true
        c.handler.removeCallbacks(longPressRunnable)
    }

    fun repeating(curX: Int, curY: Int) {
        if (!options.bool(KEY_REPEATABLE)) return
        while (true) {
            relX = if (curX - Settings.horTick > relX) swipeAction(relX, Settings.horTick, "right", true)
            else if (curX + Settings.horTick < relX) swipeAction(relX, Settings.horTick, "left", false)
            else break
        }
        while (true) {
            relY = if (curY - Settings.verTick > relY) swipeAction(relY, Settings.verTick, "bottom", true)
            else if (curY + Settings.verTick < relY) swipeAction(relY, Settings.verTick, "top", false)
            else break
        }
    }

    fun procExtChars(curX: Int, curY: Int) {
        if (extCharsRaw.isNotEmpty()) {
            relX = curX
            relY = curY
            val tmpPos = charPos
            charPos = getExtPos(curX, curY)
            if (charPos != 0 && tmpPos != charPos) c.vibrate(this, KEY_VIBRATE_ON_EXT)
        }
    }

    fun clipboardAction(): Boolean {
        if (!options.bool(KEY_CLIPBOARD)) return false
        try {
            if (c.ctrlPressed()) {
                val tx = c.currentInputConnection.getSelectedText(0).toString()
                if (c.shiftPressed()) {
                    char2utfEscape(tx)
                } else {
                    if (tx.indexOf("u") >= 0) utf2char(tx)
                    else if (tx.indexOf("b") >= 0) bin2char(tx)
                    else if (tx.indexOf("x") >= 0) hex2char(tx)
                    else if (tx.indexOf("0") >= 0) oct2char(" $tx") // " 0555 0456", " 0123"
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
            c.prStack(e)
        }
        return true
    }

    fun modifierAction(): Boolean {
        if (!options.bool(KEY_MOD)) return false

        if ((c.modifierState and options.num(KEY_MOD_META)) > 0) {
            c.modifierState = c.modifierState and options.num(KEY_MOD_META).inv()
            c.keyShiftable(KeyEvent.ACTION_UP, options.num(KEY_MOD_CODE))
        } else {
            c.modifierState = c.modifierState or options.num(KEY_MOD_META)
            c.keyShiftable(KeyEvent.ACTION_DOWN, options.num(KEY_MOD_CODE))
        }
        c.picture?.repMod()
        return true
    }

    fun shiftAction(): Boolean {
        if (!c.shiftPressed() || !options.bool(KEY_SHIFT)) return false
        try {
            val tx = c.currentInputConnection.getSelectedText(0).toString()
            c.log(tx)
            val res = when (options.str(KEY_SHIFT)) {
                "upperAll" -> tx.uppercase(Locale.ROOT)
                "lowerAll" -> tx.lowercase(Locale.ROOT)
                else -> ""
            }
            if (res != "") c.setText(res)
        } catch (_: Exception) {
        }
        return true
    }

    fun press(curX: Int, curY: Int) {

        mediaPressed.play()

        if (modifierAction()) return
        c.handler.postDelayed(longPressRunnable, Settings.longPressTime)
        longPressed = false
        hardPressed = false
        c.vibrate(this, KEY_VIBRATE_ON_PRESS)
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
        if (options.str(KEY_MOD).isNotBlank()) return
        if (options.bool(KEY_CLIPBOARD)) {
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
        mediaReleased.play()
        if (longPressed || hardPressed) return
        if (charPos == 0) c.vibrate(this, KEY_VIBRATE_ON_RELEASE)
        if (options.str(KEY_MOD).isNotBlank()) {
            if (c.lastPointerId != pid) modifierAction()
            return
        }
        if (prExtChars() || curY == 0 || cursorMoved) return
        if (recordAction(curX, curY) || /*clipboardAction() ||*/ shiftAction()) return
        if (options.str(KEY_APP).isNotBlank()) c.startActivity(c.packageManager.getLaunchIntentForPackage(options.str(KEY_APP)))
        if (options.str(KEY_SU_ACTION).isNotBlank()) suExec(options.str(KEY_SU_ACTION))
        if (!rand.isNullOrEmpty()) return c.onText(rand!![Random().nextInt(rand!!.size)]!!)
        if (langSwitch() || textInput()) return
        if (repeat || relX < 0 || charPos == 0) return c.onKey(codes)
    }

    private fun textInput(): Boolean {
        if (text.isNullOrEmpty()) return false
        c.onText(if (text!!.length == 1) c.getShifted(text!![0].code, c.shiftPressed()).toChar().toString() else text!!)
        if (options.str(CURSOR_OFFSET_POS).isBlank()) return true
        val pos = options.num(CURSOR_OFFSET_POS)
        for (i in 1..(-pos + if (pos < 0) 0 else text!!.length)) c.onKey(-21)
        return true
    }

    private fun langSwitch(): Boolean = if (options.str(KEY_LANG_SWITCH).isNotBlank() && charPos == 0) {
        c.apply { currentLayout = options.str(KEY_LANG_SWITCH); reloadLayout() }
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

    operator fun get(s: String): String = if (options.has(s)) options.str(s) else if (parent.has(s)) parent[s] else ""

    fun has(s: String): Boolean = options.has(s) || parent.has(s)
}
