package com.vladgba.keyb

import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.*
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.vladgba.keyb.Flexaml.FxmlNode
import com.vladgba.keyb.KeybLayout.Row
import java.util.*
import kotlin.math.*

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
    var charPos = 0
    val longPressRunnable = Runnable { longPress() }
    val hardRepeatRunnable = Runnable { hardPressAction() }
    val action = KeyAction(c)

    var state = State.RELEASED

    enum class State {
        PRESSED, LONG_PRESSED, HOLD_REPEAT,
        MOVED, HARD_PRESSED, HARD_REPEAT,
        HOLD_MOVED, HOLD_HARD_PRESSED,
        RELEASED,
    }

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
            detectInputSource()
            loadSounds()
        } catch (e: Exception) {
            c.prStack(e)
        }
    }
    private fun loadSounds() {
        mediaPressed.setSound(c, this, KEY_SOUND_PRESS)
        mediaReleased.setSound(c, this, KEY_SOUND_RELEASE)
        /*sound[KEY_FEEDBACK_PRESS] = Media().apply { setSound(c, this@Key, KEY_FEEDBACK_PRESS)}*/
    }
    private fun detectInputSource() {
        if (num(KEY_CODE) != 0) inputMode = InputMode.CODE
        if (str(KEY_TEXT).isNotEmpty()) inputMode = InputMode.TEXT
    }

    fun longPress() {
        val holdStr = str(KEY_HOLD)
        if (holdStr.isBlank() && !bool(KEY_HOLD_REPEAT)) return
        if (bool(KEY_HOLD_REPEAT)) c.keyboardHandler.postDelayed(longPressRunnable, num(SENSE_HOLD_PRESS_REPEAT).toLong())

        if (state == State.PRESSED) state = State.LONG_PRESSED
        else if (state == State.LONG_PRESSED) state = State.HOLD_REPEAT

        c.onText(holdStr.ifBlank { text() })
    }

    fun hardPress(me: MotionEvent, pid: Int) {
        if (state == State.HARD_PRESSED) return
        if (str(KEY_HARD_PRESS).isBlank() || str(SENSE_HARD_PRESS).isBlank()) return
        try {
            if ((me.getPressure(pid) * 1000).toInt() > num(SENSE_HARD_PRESS) && state == State.PRESSED)
                hardPressAction()
        } catch (_: Exception) {
        }
    }

    private fun hardPressAction() {
        state = when (state) {
            State.PRESSED -> State.HARD_PRESSED
            State.HARD_PRESSED -> State.HARD_REPEAT
            State.LONG_PRESSED -> State.HOLD_HARD_PRESSED
            else -> state
        }
        if (bool(KEY_HARD_REPEAT) && (state == State.HARD_PRESSED || state == State.HARD_REPEAT)) c.keyboardHandler.postDelayed(hardRepeatRunnable, num(SENSE_HOLD_PRESS).toLong())

        if (state != State.RELEASED) {
            c.onText(str(KEY_HARD_PRESS))
        }
    }

    fun getExtPos(x: Int, y: Int): Int {
        val ofs = num(SENSE_ADDITIONAL_CHARS)
        if (abs(press.x - x) < ofs && abs(press.y - y) < ofs) return 0
        if (charPos == 0) c.keyboardHandler.removeCallbacks(longPressRunnable)
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
        if (state == State.PRESSED) state = State.MOVED
        c.onKey(if (has(s)) num(s) else code())
        c.vibrate(this, KEY_VIBRATE_TICK)
        return if (add) r + t else r - t
    }

    private fun preventLongPressOnMoved() {
        if (state == State.MOVED || state == State.HOLD_MOVED) return
        c.keyboardHandler.removeCallbacks(longPressRunnable)
    }

    fun repeating(curX: Int, curY: Int) {
        if (str(KEY_MODE) != KEY_MODE_JOY) return
        while (true) relative!!.x =
            procRepeating(curX, relative!!.x, SENSE_HORIZONTAL_TICK, KEY_RIGHT_ACTION, KEY_LEFT_ACTION) ?: break
        while (true) relative!!.y =
            procRepeating(curY, relative!!.y, SENSE_VERTICAL_TICK, KEY_BOTTOM_ACTION, KEY_TOP_ACTION) ?: break
    }

    private fun procRepeating(cur: Int, initial: Int, pname: String, pos: String, neg: String): Int? {
        val ht = num(pname)
        if (ht < 1) return null
        return if (cur - ht > initial) swipeAction(initial, ht, pos, true)
        else if (cur + ht < initial) swipeAction(initial, ht, neg, false)
        else null
    }

    fun procExtChars(curX: Int, curY: Int) {
        if (childCount() > 0) {
            relative!!.x = curX
            relative!!.y = curY
            val tmpPos = charPos
            charPos = getExtPos(curX, curY)
            if (charPos != 0 && tmpPos != charPos) {
                if (state == State.PRESSED) state = State.MOVED
                c.vibrate(this, KEY_VIBRATE_ADDITIONAL)
            }
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
        c.invalidate()
        return true
    }

    fun shiftAction(): Boolean {
        if (!c.shiftPressed() || str(KEY_ACTION_ON_SHIFT).isBlank()) return false


        if (c.ctrlPressed() && this[KEY_ACTION_ON_CTRL].paramCount() != 0) handleAction(this[KEY_ACTION_ON_CTRL])
        if (c.altPressed() && this[KEY_ACTION_ON_ALT].paramCount() != 0) handleAction(this[KEY_ACTION_ON_ALT])
        if (c.shiftPressed() && this[KEY_ACTION_ON_SHIFT].paramCount() != 0) handleAction(this[KEY_ACTION_ON_SHIFT])

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

    private fun handleAction(node: FxmlNode) {

    }

    fun press(curX: Int, curY: Int) {
        mediaPressed.play()
        if (modifierAction()) return
        if (has(SENSE_HOLD_PRESS)) c.keyboardHandler.postDelayed(longPressRunnable, num(SENSE_HOLD_PRESS).toLong())

        state = State.PRESSED

        c.vibrate(this, KEY_VIBRATE_PRESS)
        press = Point(curX, curY)
        charPos = 0
        relative = if (str(KEY_MODE) == KEY_MODE_META) null else Point(curX, curY)
    }

    fun drag(curX: Int, curY: Int, me: MotionEvent, pid: Int) {
        if (str(KEY_MODE) == KEY_MODE_META) return
        if (bool(KEY_CLIPBOARD)) {
            charPos = getExtPos(curX, curY)
            return
        }

        if (state == State.HARD_PRESSED || state == State.HOLD_HARD_PRESSED || state == State.LONG_PRESSED) return
        hardPress(me, pid)
        if (relative == null) return
        repeating(curX, curY)
        procExtChars(curX, curY)
    }

    fun release(curX: Int, curY: Int, pid: Int) {
        mediaReleased.play()
        if (charPos == 0) c.vibrate(this, KEY_VIBRATE_RELEASE)
        when (state) {
            State.PRESSED -> {
                if (str(KEY_MODE) == KEY_MODE_META) {
                    if (c.lastPointerId != pid) modifierAction()
                } else if (str(KEY_MODE) == KEY_MODE_RANDOM && this[KEY_RANDOM].childCount() > 0) {
                    return c.onText(this[KEY_RANDOM].str(Random().nextInt(this[KEY_RANDOM].childCount())))
                } else if (!(recordAction(curX, curY) || modifiedAction() || clipboardAction() || langSwitch() || textInput())) c.onKey(code())
            }

            State.MOVED -> {
                prExtChars()
            }

            State.HOLD_MOVED -> prExtChars(this[KEY_HOLD])
            //State.HARD_PRESSED -> c.onKey(code(this[KEY_HARD_PRESS]))
            //State.LONG_PRESSED -> c.onKey(code(this[KEY_HOLD]))
            //State.HOLD_HARD_PRESSED -> c.onKey(code(this[KEY_HOLD][KEY_HARD_PRESS]))
            else -> {}
        }

        state = State.RELEASED

        doActions()
    }

    private fun doActions() {
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
    }

    fun code(node: FxmlNode = this) = when (inputMode) {
        InputMode.CODE -> node.num(KEY_CODE)
        InputMode.TEXT -> node.str(KEY_TEXT)[0].code
        else -> node.str(KEY_KEY)[0].code
    }

    private fun text(node: FxmlNode = this) = when (inputMode) {
        InputMode.TEXT -> node.str(KEY_TEXT)
        InputMode.CODE -> node.num(KEY_CODE).toChar().toString()
        else -> node.str(KEY_KEY)
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
            c.apply { currentLayoutName = langStr; reloadLayout() }
        }
        return true
    }

    private fun prExtChars(node: FxmlNode = this): Boolean {
        if (childCount() == 0 || charPos < 1) return false
        val textIndex = node.str(charPos - 1)
        if (textIndex.isBlank()) return true
        if (textIndex.length > 1) c.onText(textIndex)
        else c.onKey(c.getFromString(textIndex)[0])
        return true
    }

    fun onDraw(canvas: Canvas) {
        canvas.save()
        if (bool(KEY_DO_NOT_SHOW)) return
        c.paint.color = c.getColor(COLOR_KEYBOARD_BACKGROUND, this)
        val r = RectF(
            x.toFloat(),
            y.toFloat(),
            x.toFloat() + width.toFloat(),
            y.toFloat() + height.toFloat()
        )
        canvas.drawRect(r, c.paint)

        if (!bool(KEY_VISIBLE)) return

        // Positions for subsymbols
        val x1 = width / 5f
        val x2 = width / 2f
        val x3 = width - x1
        val y1 = height / 5f
        val y2 = height / 2f
        val y3 = height - y1

        canvas.clipRect(Rect(x, y, x + width, y + height))
        c.paint.isAntiAlias = true
        c.paint.textAlign = Paint.Align.CENTER
        c.paint.color = c.getColor(COLOR_KEY_BORDER, this)
        val padding = float(KEY_PADDING, height / 16f)
        val radius = float(KEY_BORDER_RADIUS, height / 6f)
        val shadow = float(KEY_SHADOW, height / 36f)


        val margin = padding + radius + 2
        val hb = str(KEY_HIDE_BORDERS)
        val lpad = if (hb.contains("l")) -margin else 0f
        val rpad = if (hb.contains("r")) margin else 0f
        val tpad = if (hb.contains("t")) -margin else 0f
        val bpad = if (hb.contains("b")) margin else 0f

        val recty = RectF(
            (x + lpad + padding - shadow / 3),
            (y + tpad + padding),
            (x + rpad + width - padding + shadow / 3),
            (y + bpad + height - padding + shadow)
        )
        canvas.drawRoundRect(recty, radius, radius, c.paint)
        c.paint.color = c.getColor(COLOR_KEY_BACKGROUND, this)

        if (str(KEY_MODE) == KEY_MODE_META && (num(KEY_MOD_META) and c.metaState) > 0) c.paint.color =
            c.getColor(COLOR_KEY_BACKGROUND_META, this)
        canvas.drawRoundRect(RectF(
            (x + lpad + padding),
            (y + tpad + padding),
            (x + rpad + width - padding),
            (y + bpad + height - padding)
        ), radius, radius, c.paint)

        c.paint.textSize = height / float(KEY_TEXT_SIZE_SECONDARY)
        viewExtChars(canvas, c.paint, c.shiftPressed(), x1, x2, x3, y1, y2, y3, false)

        c.paint.color = c.getColor(COLOR_TEXT_PRIMARY, this)
        c.paint.textSize = height / float(KEY_TEXT_SIZE_PRIMARY)

        drawLabel(canvas, c.paint)
        canvas.restore()
    }

    fun drawKey(canvas: Canvas) {
        canvas.save()

        if (childCount() == 0 && !bool(KEY_CLIPBOARD)) return
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = height / float(KEY_TEXT_SIZE_PRIMARY)

        val rectShadow = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        paint.color = c.getColor(COLOR_KEY_SHADOW, this)
        canvas.drawRect(rectShadow, paint)

        paint.color = c.getColor(COLOR_TEXT_PREVIEW, this)

        val x1 = -width / 2f
        val x2 = width / 2f
        val x3 = width * 1.5f

        val y1 = -height / 2f
        val y2 = height / 2f
        val y3 = height * 1.5f


        if (!bool(KEY_CLIPBOARD) || charPos < 1 || str(charPos - 1).isEmpty()) {
            val rect = Rect(
                x - width,
                y - height,
                x + width * 2,
                y + height * 2
            )
            canvas.clipRect(rect)
        }
        val padding = float(KEY_PADDING, height / 16f)
        val radius = float(KEY_BORDER_RADIUS, height / 16f)
        paint.color = c.getColor(COLOR_KEY_BORDER, this)
        var recty = RectF(
            x + x1 * 2,
            y + y1 * 2,
            x - x1 + x3,
            y - y1 + y3
        )
        canvas.drawRoundRect(recty, radius, radius, paint)
        paint.color = c.getColor(COLOR_KEY_BACKGROUND, this)
        recty = RectF(
            x + x1 * 2 + padding / 3,
            y + y1 * 2,
            x - x1 + x3 - padding / 3,
            y - y1 + y3 - padding
        )
        canvas.drawRoundRect(recty, radius, radius, paint)
        paint.color = c.getColor(COLOR_TEXT_PRIMARY, this)
        val sh = c.shiftPressed()
        if (bool(KEY_CLIPBOARD) && charPos > 0 && str(charPos - 1).isNotEmpty()) {
            paint.textSize = height / float(KEY_TEXT_SIZE_SECONDARY)
            canvas.drawText(
                str(charPos - 1),
                width / 2f,
                height / 5 + (paint.textSize - paint.descent()) / 2,
                paint
            )
        }
        viewExtChars(canvas, paint, sh, x1, x2, x3, y1, y2, y3, true)
        canvas.restore()
    }

    private fun drawLabel(cv: Canvas, p: Paint) {
        if (str(KEY_KEY).isBlank()) return
        cv.drawText(shiftedLabel(), x + width / 2f, y + (height + p.textSize - p.descent()) / 2, p)
    }

    private fun viewExtChars(
        cv: Canvas,
        p: Paint,
        sh: Boolean,
        x1: Float,
        x2: Float,
        x3: Float,
        y1: Float,
        y2: Float,
        y3: Float,
        h: Boolean
    ) {
        if (childCount() == 0) return
        val xi = floatArrayOf(x1, x2, x3, x1, x3, x1, x2, x3)
        val yi = floatArrayOf(y1, y1, y1, y2, y2, y3, y3, y3)
        for (i in 0..7) {
            p.color = c.getColor(COLOR_KEY_POPUP_SELECTED, this)
            if (h) {
                if (charPos == i + 1) drawCircle(cv, xi[i], yi[i], p)
                p.color = c.getColor(COLOR_TEXT_PREVIEW, this)
            } else {
                p.color = c.getColor(COLOR_KEY_SECONDARY, this)
            }
            viewChar(i, xi[i], yi[i], cv, p, sh)
        }
        if (!h) return
        if (charPos == 0) {
            p.color = c.getColor(COLOR_KEY_POPUP_SELECTED, this)
            drawCircle(cv, x2, y2, p)
        }
        p.color = c.getColor(COLOR_TEXT_PREVIEW, this)
        cv.drawText(shiftedLabel(), (x + x2), y + y2 + (p.textSize - p.descent()) / 2, p)
    }

    private fun drawCircle(cv: Canvas, xi: Float, yi: Float, p: Paint) {
        cv.drawCircle((x + xi), (y + yi), min(width, height) * 0.6f, p)
    }
    private fun viewChar(
        pos: Int,
        ox: Float,
        oy: Float,
        canvas: Canvas,
        paint: Paint,
        sh: Boolean
    ) {
        if (str(pos).isBlank()) return
        canvas.drawText(
            str(pos).also { if (it.length == 1) c.getShifted(it[0].code, sh).toChar().toString() },
            (x + ox),
            y + oy + (paint.textSize - paint.descent()) / 2,
            paint
        )
    }

    private fun shiftedLabel(): String {
        val lb = str(KEY_KEY)
        return if (c.shiftPressed() && lb.length < 2) c.getShifted(lb[0].code, true).toChar().toString()
        else lb
    }
}
