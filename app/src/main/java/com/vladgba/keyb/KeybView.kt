package com.vladgba.keyb

import android.annotation.SuppressLint
import android.graphics.*
import android.util.Log
import android.view.*
import java.lang.*
import java.util.*
import kotlin.collections.*
import kotlin.math.*

@SuppressLint("ViewConstructor")
class KeybView(private val c: KeybCtl) : View(c.ctx), View.OnClickListener {
    private var buffer: Bitmap? = null
    private var bufferSh: Bitmap? = null
    private var paint = Paint()
    private var repMods = false

    fun clearBuffers() {
        buffer = null
        bufferSh = null
    }

    fun reload() {
        clearBuffers()
        requestLayout()
        invalidate()
    }

    override fun onClick(v: View) {}

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = c.keybLayout?.width ?: 1
        if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(width, c.keybLayout?.height ?: 1)
    }

    public override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d("sizes", "$w; $h; $oldw; $oldh")

        repaintKeyb()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {

        return true
    }

    fun repMod() {
        if (buffer == null || bufferSh == null) return
        repMods = true
        keybPaint(Canvas(buffer!!), false)
        keybPaint(Canvas(bufferSh!!), true)
        repMods = false
        invalidate()
    }


    fun repaintKeyb() {
        val w = if (width > 0) width else 1
        val h = c.keybLayout?.height.let { if (it != null && it > 0) it else 1 }

        bufferSh = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        buffer = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        c.keybLayout!!.let {
            for (row in it.rows) row.calcWidth()
        }
        keybPaint(Canvas(buffer!!), false)
        keybPaint(Canvas(bufferSh!!), true)
        invalidate()
    }

    private fun keybPaint(canvas: Canvas, sh: Boolean) {
        val paint = Paint()

        for (row in c.keybLayout!!.rows) {
            for (key in row.keys) {
                if (repMods && key.str(KEY_MODE) != KEY_MODE_META) continue
                canvas.save()
                paint.color = getColor(COLOR_KEYBOARD_BACKGROUND, key)
                val r = RectF(
                    key.x.toFloat(),
                    key.y.toFloat(),
                    key.x.toFloat() + key.width.toFloat(),
                    key.y.toFloat() + key.height.toFloat()
                )
                canvas.drawRect(r, paint)

                if (!key.bool(KEY_VISIBLE)) continue

                // Positions for subsymbols
                val x1 = key.width / 5f
                val x2 = key.width / 2f
                val x3 = key.width - x1
                val y1 = key.height / 5f
                val y2 = key.height / 2f
                val y3 = key.height - y1

                val rect = Rect(key.x, key.y, key.x + key.width, key.y + key.height)
                canvas.clipRect(rect)
                paint.isAntiAlias = true
                paint.textAlign = Paint.Align.CENTER
                paint.color = getColor(COLOR_KEY_BORDER, key)
                val padding = key.float(KEY_PADDING, key.height / 16f)
                val radius = key.float(KEY_BORDER_RADIUS, key.height / 6f)
                val shadow = key.float(KEY_SHADOW, key.height / 36f)


                val margin = padding + radius + 2
                val hb = key.str(KEY_HIDE_BORDERS)
                val lpad = if (hb.contains("l")) -margin else 0f
                val rpad = if (hb.contains("r")) margin else 0f
                val tpad = if (hb.contains("t")) -margin else 0f
                val bpad = if (hb.contains("b")) margin else 0f

                val recty = RectF(
                    (key.x + lpad + padding - shadow / 3),
                    (key.y + tpad + padding),
                    (key.x + rpad + key.width - padding + shadow / 3),
                    (key.y + bpad + key.height - padding + shadow)
                )
                canvas.drawRoundRect(recty, radius, radius, paint)
                paint.color = getColor(COLOR_KEY_BACKGROUND, key)

                if (key.str(KEY_MODE) == KEY_MODE_META && (key.num(KEY_MOD_META) and c.metaState) > 0) paint.color =
                    getColor(COLOR_KEY_MOD_BACKGROUND, key)
                canvas.drawRoundRect(RectF(
                    (key.x + lpad + padding),
                    (key.y + tpad + padding),
                    (key.x + rpad + key.width - padding),
                    (key.y + bpad + key.height - padding)
                ), radius, radius, paint)

                paint.textSize = key.height / key.float(KEY_TEXT_SIZE_SECONDARY)
                viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false)

                paint.color = getColor(COLOR_TEXT_PRIMARY, key)
                paint.textSize = key.height / key.float(KEY_TEXT_SIZE_PRIMARY)

                drawLabel(key, sh, canvas, paint)
                canvas.restore()
            }
        }
    }

    private fun drawLabel(key: Key, sh: Boolean, cv: Canvas, p: Paint) {
        if (key.str(KEY_KEY).isBlank()) return
        val lbl = shiftedLabel(sh, key)
        cv.drawText(lbl, key.x + key.width / 2f, key.y + (key.height + p.textSize - p.descent()) / 2, p)
    }

    private fun randColor(): Int {
        val a = arrayOf(0xffff0000, 0xffffff00, 0xffffffff, 0xff00ff00, 0xffff00ff, 0xff00ffff, 0xff0000ff, 0xff000000)
        return (a[Random().nextInt(8)]).toInt()
    }

    private fun getColor(styleName: String, node: Flexaml.FxmlNode = c.keybLayout!!): Int {
        return if (node.has(styleName)) hexNum(node.str(styleName)) else randColor()
    }

    private fun hexNum(s: String) = s.toLong(16).toInt()

    public override fun onDraw(canvas: Canvas) {
        try {
            if (buffer == null) repaintKeyb()

            canvas.drawBitmap((if (c.shiftPressed()) bufferSh else buffer)!!, 0f, 0f, null)
            drawModifierCode(canvas)
            if (c.recKey != null) canvas.drawText(c.recKey!!.record.size.toString(), 20f, 20f, paint)
            if (c.pointers[c.lastPointerId] == null) return
            if (c.wrapper != null) for ((_, point) in c.pointers.entries) drawKey(canvas, point)
        } catch (e: Exception) {
            c.prStack(e)
        }
    }

    private fun drawModifierCode(canvas: Canvas) {
        if (!Settings.bool(SETTING_DEBUG)) return
        paint.color = hexNum(Settings.str(COLOR_TEXT_PRIMARY))
        paint.textSize = 20f
        canvas.drawText(c.metaState.toString(2), 10f, 95f, paint)
    }

    fun drawKey(canvas: Canvas, curkey: Key) {
        canvas.save()

        if (curkey.childCount() == 0 && !curkey.bool(KEY_CLIPBOARD)) return
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = curkey.height / curkey.float(KEY_TEXT_SIZE_PRIMARY)

        val rectShadow = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = getColor(COLOR_KEY_SHADOW, curkey)
        canvas.drawRect(rectShadow, paint)

        paint.color = getColor(COLOR_TEXT_PREVIEW, curkey)

        val x1 = -curkey.width / 2f
        val x2 = curkey.width / 2f
        val x3 = curkey.width * 1.5f

        val y1 = -curkey.height / 2f
        val y2 = curkey.height / 2f
        val y3 = curkey.height * 1.5f


        if (!curkey.bool(KEY_CLIPBOARD) || curkey.charPos < 1 || curkey.str(curkey.charPos - 1).isEmpty()) {
            val rect = Rect(
                curkey.x - curkey.width,
                curkey.y - curkey.height,
                curkey.x + curkey.width * 2,
                curkey.y + curkey.height * 2
            )
            canvas.clipRect(rect)
        }
        val padding = curkey.float(KEY_PADDING, curkey.height / 16f)
        val radius = curkey.float(KEY_BORDER_RADIUS, curkey.height / 16f)
        paint.color = getColor(COLOR_KEY_BORDER, curkey)
        var recty = RectF(
            curkey.x + x1 * 2,
            curkey.y + y1 * 2,
            curkey.x - x1 + x3,
            curkey.y - y1 + y3
        )
        canvas.drawRoundRect(recty, radius, radius, paint)
        paint.color = getColor(COLOR_KEY_BACKGROUND, curkey)
        recty = RectF(
            curkey.x + x1 * 2 + padding / 3,
            curkey.y + y1 * 2,
            curkey.x - x1 + x3 - padding / 3,
            curkey.y - y1 + y3 - padding
        )
        canvas.drawRoundRect(recty, radius, radius, paint)
        paint.color = getColor(COLOR_TEXT_PRIMARY, curkey)
        val sh = c.shiftPressed()
        if (curkey.bool(KEY_CLIPBOARD) && curkey.charPos > 0 && curkey.str(curkey.charPos - 1).isNotEmpty()) {
            paint.textSize = curkey.height / curkey.float(KEY_TEXT_SIZE_SECONDARY)
            canvas.drawText(
                curkey.str(curkey.charPos - 1),
                width / 2f,
                curkey.height / 5 + (paint.textSize - paint.descent()) / 2,
                paint
            )
        }
        viewExtChars(curkey, canvas, paint, sh, x1, x2, x3, y1, y2, y3, true)
        canvas.restore()
    }

    private fun viewExtChars(
        curkey: Key?,
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
        if (curkey == null || curkey.childCount() == 0) return
        val xi = floatArrayOf(x1, x2, x3, x1, x3, x1, x2, x3)
        val yi = floatArrayOf(y1, y1, y1, y2, y2, y3, y3, y3)
        for (i in 0..7) {
            p.color = getColor(COLOR_KEY_PREVIEW_SELECTED, curkey)
            if (h) {
                if (curkey.charPos == i + 1) drawCircle(cv, curkey, xi[i], yi[i], p)
                p.color = getColor(COLOR_TEXT_PREVIEW, curkey)
            } else {
                p.color = getColor(COLOR_KEY_SECONDARY, curkey)
            }
            viewChar(curkey, i, xi[i], yi[i], cv, p, sh)
        }
        if (!h) return
        if (curkey.charPos == 0) {
            p.color = getColor(COLOR_KEY_PREVIEW_SELECTED, curkey)
            drawCircle(cv, curkey, x2, y2, p)
        }
        p.color = getColor(COLOR_TEXT_PREVIEW, curkey)
        cv.drawText(shiftedLabel(sh, curkey), (curkey.x + x2), curkey.y + y2 + (p.textSize - p.descent()) / 2, p)
    }

    private fun drawCircle(cv: Canvas, curkey: Key, xi: Float, yi: Float, p: Paint) {
        cv.drawCircle((curkey.x + xi), (curkey.y + yi), min(curkey.width, curkey.height) * 0.6f, p)
    }

    private fun shiftedLabel(sh: Boolean, curkey: Key): String {
        val lb = curkey.str(KEY_KEY)
        return if (sh && lb.length < 2) c.getShifted(lb[0].code, true).toChar().toString()
        else lb
    }

    private fun viewChar(
        key: Key,
        pos: Int,
        ox: Float,
        oy: Float,
        canvas: Canvas,
        paint: Paint,
        sh: Boolean
    ) {
        if (key.str(pos).isBlank()) return
        canvas.drawText(
            key.str(pos).also { if (it.length == 1) c.getShifted(it[0].code, sh).toChar().toString() },
            (key.x + ox),
            key.y + oy + (paint.textSize - paint.descent()) / 2,
            paint
        )
    }

    override fun onTouchEvent(me: MotionEvent): Boolean {
        invalidate()
        c.onTouchEvent(me)
        return true
    }
}
