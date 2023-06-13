package com.vladgba.keyb

import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.vladgba.keyb.Flexaml.FxmlNode
import com.vladgba.keyb.KeybLayout.Row
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil

class Key(private var c: KeybCtl, var row: Row, var x: Int, var y: Int, opts: FxmlNode) : FxmlNode(opts, row) {

    class Point(var x: Int, var y: Int)
    enum class InputMode {
        LABEL, CODE, TEXT
    }

    var inputMode = InputMode.LABEL

    var width: Int = 0
    val height
        get() = row.height

    var record: ArrayList<Record> = ArrayList()
    var recording: Boolean = false
    private val mediaPressed = Media()
    private val mediaReleased = Media()
    
    var press = Point(0, 0)
    private var relative: Point? = null
    var cursorMoved = false
    var charPos = 0
    var pressed = false
    var longPressed = false
    var hardPressed = false
    val longPressRunnable = Runnable { longPress() }
    val action = KeyAction(c)

    class Media {
        enum class State { NOT_LOADED, READY, PLAYING }

        var state = State.NOT_LOADED
        val media = MediaPlayer()
        fun setSound(c: KeybCtl, k: Key, s: String) {
            try {
                if (k.str(s).isBlank()) return
                media.setDataSource(k.str(s))
                media.prepare()
                state = State.READY
            } catch (e: Exception) {
                state = State.NOT_LOADED
                Toast.makeText(c.ctx, "Sound file " + k.str(s) + " not loaded", Toast.LENGTH_LONG).show()
            }
        }

        fun play() {
            if (state == State.NOT_LOADED) return
            if (state == State.PLAYING) {
                media.stop()
                media.prepare()
            } else {
                state = State.PLAYING
            }
            media.start()
        }
    }

    init {
        try {
            if (num(KEY_CODE) != 0) inputMode = InputMode.CODE
            if (str(KEY_TEXT).isNotEmpty()) inputMode = InputMode.TEXT
            mediaPressed.setSound(c, this, KEY_SOUND_PRESS)
            mediaReleased.setSound(c, this, KEY_SOUND_RELEASE)

        } catch (e: Exception) {
            c.prStack(e)
        }
    }

    fun longPress() {
        val holdStr = str(KEY_HOLD)
        if (holdStr.isBlank() && !bool(KEY_HOLD_REPEAT)) return
        if (bool(KEY_HOLD_REPEAT)) c.handler.postDelayed(longPressRunnable, num(SENSE_HOLD_PRESS_REPEAT).toLong())
        longPressed = true
        c.onText(holdStr.ifBlank { text() })
    }

    fun hardPress(me: MotionEvent, pid: Int) {
        if (str(KEY_HARD_PRESS).isBlank() || str(SENSE_HARD_PRESS).isBlank()) return
        try {
            if ((me.getPressure(pid) * 1000).toInt() > num(SENSE_HARD_PRESS)) {
                hardPressed = true
                c.onText(str(KEY_HARD_PRESS))
            }
        } catch (_: Exception) {
        }
    }

    fun getExtPos(x: Int, y: Int): Int {
        val ofs = num(SENSE_ADDITIONAL_CHARS)
        if (abs(press.x - x) < ofs && abs(press.y - y) < ofs) return 0
        if (charPos == 0) c.handler.removeCallbacks(longPressRunnable)
        return calcPos(x, y)
    }

    private fun calcPos(x: Int, y: Int): Int {
        Math.toDegrees(atan2((press.y - y).toDouble(), (press.x - x).toDouble())).also {
            return arrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)[
                ceil(((if (it < 0) 360.0 else 0.0) + it + 22.5) / 45.0).toInt() - 1]
        }
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
                keybCtl.keyShifted(keyState, keyIndex, keyMod)
            } else {
                SystemClock.sleep(50) // wait until sendkeyevent is processed
                keybCtl.onText(keyText!!)
            }
        }
    }

    fun recordAction(curX: Int, curY: Int): Boolean {
        if (str(KEY_RECORD).isBlank()) return false
        when (getExtPos(curX, curY)) {
            in arrayOf(1, 3, 6, 8) -> record.clear()
            in arrayOf(4, 5) -> c.recKey = this.apply { recording = true }
            in arrayOf(2, 7) -> (c.recKey ?: return true).recording = false
            else -> {
                if (record.size == 0) return true
                for (i in 0 until record.size) record[i].replay(c)
            }
        }
        return true
    }

    private fun swipeAction(r: Int, t: Int, s: String, add: Boolean): Int {
        preventLongPressOnMoved()
        c.onKey(if (has(s)) num(s) else code())
        c.vibrate(this, KEY_VIBRATE_TICK)
        return if (add) r + t else r - t
    }

    private fun preventLongPressOnMoved() {
        if (cursorMoved) return
        cursorMoved = true
        c.handler.removeCallbacks(longPressRunnable)
    }

    fun repeating(curX: Int, curY: Int) {
        if (str(KEY_MODE) != KEY_MODE_JOY) return
        while (true) {
            val ht = num(SENSE_HORIZONTAL_TICK)
            if (ht < 1) return
            relative!!.x = if (curX - ht > relative!!.x) swipeAction(relative!!.x, ht, KEY_RIGHT_ACTION, true)
            else if (curX + ht < relative!!.x) swipeAction(relative!!.x, ht, KEY_LEFT_ACTION, false)
            else break
        }
        while (true) {
            val vt = num(SENSE_VERTICAL_TICK)
            if (vt < 1) return
            relative!!.y = if (curY - vt > relative!!.y) swipeAction(relative!!.y, vt, KEY_BOTTOM_ACTION, true)
            else if (curY + vt < relative!!.y) swipeAction(relative!!.y, vt, KEY_TOP_ACTION, false)
            else break
        }
    }

    fun procExtChars(curX: Int, curY: Int) {
        if (childCount() > 0) {
            relative!!.x = curX
            relative!!.y = curY
            val tmpPos = charPos
            charPos = getExtPos(curX, curY)
            if (charPos != 0 && tmpPos != charPos) c.vibrate(this, KEY_VIBRATE_ADDITIONAL)
        }
    }

    fun clipboardAction(): Boolean {
        if (!bool(KEY_CLIPBOARD)) return false
        try {
            if (c.ctrlPressed()) {
                val tx = (c.wrapper?.currentInputConnection ?: return true).getSelectedText(0).toString()
                action.apply {
                    if (c.shiftPressed()) {
                        char2utfEscape(tx)
                    } else {
                        // TODO: refactor to modular
                        if (tx.indexOf("\\u") >= 0) utf2char(tx)
                        else if (tx.indexOf("0b") >= 0) bin2char(tx)
                        else if (tx.indexOf("0x") >= 0) hex2char(tx)
                        else if (tx.indexOf("0") >= 0) oct2char(" $tx")
                        //else if (tx.indexOf(" ") >= 0) dec2char(tx)
                        else dec2char(tx)
                    }
                }
                return true
            }
            if (charPos < 1) return true
            if (c.shiftPressed()) {
                this[charPos - 1] =
                    (c.wrapper?.currentInputConnection ?: return true).getSelectedText(0).toString()
            } else {
                if (this.str(charPos - 1).isBlank()) return true
                c.onText(this[charPos - 1].toString())
            }
        } catch (e: Exception) {
            c.prStack(e)
        }
        return true
    }

    fun modifierAction(): Boolean {
        if (str(KEY_MODE) != KEY_MODE_META || !has(KEY_CODE) || !has(KEY_MOD_META)) return false

        if ((c.metaState and num(KEY_MOD_META)) > 0) {
            c.metaState = c.metaState and num(KEY_MOD_META).inv()
            c.keyShifted(KeyEvent.ACTION_UP, num(KEY_CODE))
        } else {
            c.metaState = c.metaState or num(KEY_MOD_META)
            c.keyShifted(KeyEvent.ACTION_DOWN, num(KEY_CODE))
        }
        c.view.repMod()
        return true
    }

    fun shiftAction(): Boolean {
        if (!c.shiftPressed() || str(KEY_ACTION_ON_SHIFT).isBlank()) return false
        try {
            val tx = (c.wrapper?.currentInputConnection ?: return true).getSelectedText(0).toString()
            val res = when (str(KEY_ACTION_ON_SHIFT)) {
                ACTION_UPPER_ALL -> tx.uppercase(Locale.ROOT)
                ACTION_LOWER_ALL -> tx.lowercase(Locale.ROOT)
                else -> null
            }
            if (res != null) c.setText(res)
        } catch (_: Exception) {
        }
        return true
    }

    fun press(curX: Int, curY: Int) {
        mediaPressed.play()
        if (modifierAction()) return
        if (has(SENSE_HOLD_PRESS)) c.handler.postDelayed(longPressRunnable, num(SENSE_HOLD_PRESS).toLong())
        pressed = true
        longPressed = false
        hardPressed = false
        c.vibrate(this, KEY_VIBRATE_PRESS)
        press = Point(curX, curY)
        cursorMoved = false
        charPos = 0
        relative = if (str(KEY_MODE) == KEY_MODE_META) null else Point(curX, curY)
    }

    fun drag(curX: Int, curY: Int, me: MotionEvent, pid: Int) {
        if (str(KEY_MODE) == KEY_MODE_META) return
        if (bool(KEY_CLIPBOARD)) {
            charPos = getExtPos(curX, curY)
            return
        }

        if (longPressed || hardPressed) return
        hardPress(me, pid)
        if (relative == null) return
        repeating(curX, curY)
        procExtChars(curX, curY)
    }

    fun release(curX: Int, curY: Int, pid: Int) {
        pressed = false
        mediaReleased.play()
        if (longPressed || hardPressed) return
        if (charPos == 0) c.vibrate(this, KEY_VIBRATE_RELEASE)
        if (str(KEY_MODE) == KEY_MODE_META) {
            if (c.lastPointerId != pid) modifierAction()
            return
        } else if (str(KEY_MODE) == KEY_MODE_RANDOM && this[KEY_RANDOM].childCount() > 0) {
            return c.onText(this[KEY_RANDOM].str(Random().nextInt(this[KEY_RANDOM].childCount())))
        }

        if (prExtChars() || curY == 0 || cursorMoved) return

        if (recordAction(curX, curY) || shiftAction() || clipboardAction()) return

        val actions = this[KEY_ACTION]
        if (actions.childCount() > 0) {
            for (act in actions.childs) {
                if (act is FxmlNode) {
                    if (act.params.isEmpty()) continue
                    when (act.str("type")) {
                        ACTION_SU -> action.suExec(act.str("cmd"))
                        ACTION_APP -> c.ctx.startActivity(
                            Intent(Intent.ACTION_MAIN).addFlags(FLAG_ACTIVITY_NEW_TASK).apply {
                                component = ComponentName(
                                    act.str("pkg"),
                                    act.str("class")
                                )
                            })
                    }
                }
            }
            return
        }

        if (langSwitch() || textInput()) return
        if (!cursorMoved && charPos == 0) return c.onKey(code())
    }

    fun code() = when (inputMode) {
        InputMode.CODE -> num(KEY_CODE)
        InputMode.TEXT -> str(KEY_TEXT)[0].code
        else -> str(KEY_KEY)[0].code
    }

    private fun text() = when (inputMode) {
        InputMode.TEXT -> str(KEY_TEXT)
        InputMode.CODE -> num(KEY_CODE).toChar().toString()
        else -> str(KEY_KEY)
    }

    private fun textInput(): Boolean {
        if (inputMode != InputMode.TEXT) return false
        c.onText(if (text().length == 1) c.getShifted(text()[0].code, c.shiftPressed()).toChar().toString() else text())
        if (str(KEY_TEXT_CURSOR_OFFSET).isBlank()) return true
        val pos = num(KEY_TEXT_CURSOR_OFFSET)
        for (i in 1..(-pos + if (pos < 0) 0 else text().length)) c.onKey(-21)
        return true
    }

    private fun langSwitch(): Boolean {
        val langStr = str(KEY_LAYOUT)
        (langStr.isNotBlank() && charPos == 0) || return false
        if (langStr[0] == '@') {
            when (langStr) {
                LAYOUT_PREV -> c.prevLayout()
                LAYOUT_NEXT -> c.nextLayout()
                LAYOUT_NUM -> c.numLayout()
                LAYOUT_TEXT -> c.textLayout()
                LAYOUT_EMOJI -> c.emojiLayout()
            }
        } else {
            c.apply { currentLayout = langStr; reloadLayout() }
        }
        return true
    }

    private fun prExtChars(): Boolean {
        if (childCount() == 0 || charPos < 1) return false
        val textIndex = this.str(charPos - 1)
        if (textIndex.isBlank()) return true
        if (textIndex.length > 1) c.onText(textIndex)
        else c.onKey(c.getFromString(textIndex)[0])
        return true
    }
}
