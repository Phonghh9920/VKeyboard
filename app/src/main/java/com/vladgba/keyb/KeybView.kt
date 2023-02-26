package com.vladgba.keyb

import android.annotation.SuppressLint
import android.graphics.*
import android.view.*
import java.lang.*
import java.util.*
import kotlin.collections.*
import kotlin.math.*

@SuppressLint("ViewConstructor")
class KeybView(private val c: KeybCtl) :  View(c), View.OnClickListener {
    private var buffer: Bitmap? = null
    private var bufferSh: Bitmap? = null
    private var paint = Paint()
    private var repMods = false

    fun reload() {
        buffer = null
        requestLayout()
        invalidate()
    }

    override fun onClick(v: View) {}

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = c.keybLayout?.minWidth ?: 0
        if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(width, c.keybLayout?.height ?: 0)
    }

    public override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        repaintKeyb()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return true
    }

    fun repMod() {
        if (buffer == null || bufferSh == null) return
        repMods = true
        keybPaint(buffer!!.width, buffer!!.height, buffer!!, false)
        keybPaint(bufferSh!!.width, bufferSh!!.height, bufferSh!!, true)
        repMods = false
        invalidate()
    }


    private fun repaintKeyb() {
        bufferSh = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        buffer = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        keybPaint(width, height, buffer!!, false)
        keybPaint(width, height, bufferSh!!, true)

        c.keybLayout.also {
            if (it == null) return
            it.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
            it.canv = Canvas(c.keybLayout?.bitmap!!)
        }
        invalidate()
    }

    private fun keybPaint(w: Int, h: Int, b: Bitmap, sh: Boolean) {
        val canvas = Canvas(b)
        val paint = Paint()
        if (!repMods) {
            paint.color = getColor("keyboardBackground")
            val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawRect(r, paint)
        }
        val keys = c.keybLayout!!.keys
        for (key in keys) {
            if (repMods && !key.getBool("mod")) continue

            if(key.getBool("mod")) {
                paint.color = getColor("keyboardBackground")
                val r = RectF(key.x.toFloat(), key.y.toFloat(), key.x.toFloat() + key.width.toFloat(), key.y.toFloat() + key.height.toFloat())
                canvas.drawRect(r, paint)
            }
            canvas.save()

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
            paint.color = getColor("keyBorder")
            val padding = if (key.has("padding")) key["padding"].toFloat() else key.height / 16f
            val radius = if (key.has("radius")) key["radius"].toInt().toFloat() else key.height / 6f
            val shadow = if (key.has("shadow")) key["shadow"].toFloat() else key.height / 36f

            val margin = max(padding + shadow, radius)
            val lpad = if (key.getStr("stylepos").contains("l")) -margin else 0f
            val rpad = if (key.getStr("stylepos").contains("r")) margin else 0f
            val tpad = if (key.getStr("stylepos").contains("t")) -margin else 0f
            val bpad = if (key.getStr("stylepos").contains("b")) margin else 0f

            var recty = RectF(
                (key.x + lpad + padding - shadow / 3),
                (key.y + tpad + padding),
                (key.x + rpad + key.width - padding + shadow / 3),
                (key.y + bpad + key.height - padding + shadow)
            )
            canvas.drawRoundRect(recty, radius, radius, paint)
            paint.color = if (key.getStr("bg").isEmpty()) getColor("keyBackground") else Color.parseColor("#" + key.getStr("bg"))
            if (key.getBool("mod") && (key.getInt("modmeta") and c.modifierState) > 0) paint.color = getColor("modBackground")
            recty = RectF(
                (key.x + lpad + padding),
                (key.y + tpad + padding),
                (key.x + rpad + key.width - padding),
                (key.y + bpad + key.height - padding)
            )
            canvas.drawRoundRect(recty, radius, radius, paint)
            paint.textSize = key.height / c.secondaryFont
            paint.color = getColor("primaryText")
            viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false)

            paint.color = getColor("primaryText")
            paint.textSize = key.height / c.primaryFont

            drawLabel(key, sh, canvas, paint)
            canvas.restore()
        }
    }

    private fun drawLabel(key: Key, sh: Boolean, cv: Canvas, p: Paint) {
        if (key.label == null) return
        val lbl = shiftedLabel(sh, key)
        cv.drawText(lbl, key.x + key.width / 2f, key.y + (key.height + p.textSize - p.descent()) / 2, p)
    }

    private fun randColor(): Int {
        val a = arrayOf(0xffff0000, 0xffffff00, 0xffffffff, 0xff00ff00, 0xffff00ff, 0xff00ffff, 0xff0000ff, 0xff000000)
        return (a[Random().nextInt(8)]).toInt()
    }

    private fun getColor(styleName: String): Int {
        c.settings.also {
            if (it.count() == 0) return randColor()
            val curTheme = if (c.night && it.has("colorSwitch") && it["colorSwitch"].bool()) "colorNight" else "colorDay"
            if (!it.has(curTheme)) return randColor()
            val theme = it[curTheme]
            return if (theme.has(styleName)) theme[styleName].str().toLong(16).toInt() else randColor()
        }
    }

    public override fun onDraw(canvas: Canvas) {
        try {
            if (buffer == null) repaintKeyb()

            canvas.drawBitmap((if (c.shiftPressed()) bufferSh else buffer)!!, 0f, 0f, null)
            drawModifierCode(canvas)
            canvas.drawText(c.errorInfo, 40f, 20f, paint)
            if (c.recKey != null) canvas.drawText(c.recKey!!.record.size.toString(), 20f, 20f, paint)
            if (c.points[c.lastPointerId] == null) return
            drawKey(canvas, c.points[c.lastPointerId]!!)
        } catch (e: Exception) {
            c.prStack(e)
        }
    }

    private fun drawModifierCode(canvas: Canvas) {
        if (c.getVal("debug", "") != "1") return
        if (c.keybLayout!!.bitmap != null) canvas.drawBitmap(c.keybLayout!!.bitmap!!, 0f, 0f, null)
        paint.color = (if (c.night) 0xffffffff else 0xff000000).toInt()
        paint.textSize = 20f
        canvas.drawText(c.modifierState.toString(2), 10f, 95f, paint)
    }

    fun drawKey(canvas: Canvas, curkey: Key) {
        canvas.save()

        if (curkey.extCharsRaw.isEmpty() && !curkey.getBool("clipboard")) return
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = curkey.height / c.primaryFont
        
        val rectShadow = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = getColor("shadowColor")
        canvas.drawRect(rectShadow, paint)
        
        paint.color = getColor("previewText")

        val x1 = -curkey.width / 2f
        val x2 = curkey.width / 2f
        val x3 = curkey.width * 1.5f

        val y1 = -curkey.height / 2f
        val y2 = curkey.height / 2f
        val y3 = curkey.height * 1.5f


        if (!curkey.getBool("clipboard") || curkey.charPos < 1 || curkey.extChars[curkey.charPos - 1] == null) {
            val rect = Rect(
                curkey.x - curkey.width,
                curkey.y - curkey.height,
                curkey.x + curkey.width * 2,
                curkey.y + curkey.height * 2
            )
            canvas.clipRect(rect)
        }
        val padding = c.getVal("padding", (c.keybLayout?.height!! / 16f).toString()).toFloat() * 2
        val radius = c.getVal("radius", (c.keybLayout?.height!! / 16f).toString()).toFloat() * 2
        paint.color = getColor("keyBorder")
        var recty = RectF(
            curkey.x + x1 * 2,
            curkey.y + y1 * 2,
            curkey.x - x1 + x3,
            curkey.y - y1 + y3
        )
        canvas.drawRoundRect(recty, radius, radius, paint)
        paint.color = getColor("keyBackground")
        recty = RectF(
            curkey.x + x1 * 2 + padding / 3,
            curkey.y + y1 * 2,
            curkey.x - x1 + x3 - padding / 3,
            curkey.y - y1 + y3 - padding
        )
        canvas.drawRoundRect(recty, radius, radius, paint)
        paint.color = getColor("primaryText")
        val sh = c.shiftPressed()
        if (curkey.getBool("clipboard") && curkey.charPos > 0 && curkey.extChars[curkey.charPos - 1] != null) {
            paint.textSize = curkey.height / c.secondaryFont
            canvas.drawText(
                curkey.extChars[curkey.charPos - 1].toString(),
                width / 2f,
                curkey.height / 5 + (paint.textSize - paint.descent()) / 2,
                paint
            )
        }
        viewExtChars(curkey, canvas, paint, sh, x1, x2, x3, y1, y2, y3, true)
        canvas.restore()
    }

    private fun viewExtChars(curkey: Key?, cv: Canvas, p: Paint, sh: Boolean, x1: Float, x2: Float, x3: Float, y1: Float, y2: Float, y3: Float, h: Boolean) {
        if (curkey == null || curkey.extCharsRaw == "") return
        val str = curkey.extChars
        val xi = floatArrayOf(x1, x2, x3, x1, x3, x1, x2, x3)
        val yi = floatArrayOf(y1, y1, y1, y2, y2, y3, y3, y3)
        for (i in 0..7) {
            p.color = getColor("previewSelected")
            if (h) {
                if (curkey.charPos == i + 1) cv.drawCircle((curkey.x + xi[i]),
                    (curkey.y + yi[i]), min(curkey.width, curkey.height) * 0.6f, p)
                p.color = getColor("previewText")
            } else {
                p.color = getColor("secondaryText")
            }
            viewChar(str, i, xi[i], yi[i], cv, curkey, p, sh)
        }
        if (!h) return
        if (curkey.charPos == 0) {
            p.color = getColor("previewSelected")
            cv.drawCircle(
                curkey.x + x2,
                curkey.y + y2,
                min(curkey.width, curkey.height) * 0.4f,
                p
            )
        }
        p.color = getColor("previewText")
        cv.drawText(
            shiftedLabel(sh, curkey),
            (curkey.x + x2),
            curkey.y + y2 + (p.textSize - p.descent()) / 2,
            p
        )
    }

    private fun shiftedLabel(sh: Boolean, curkey: Key) =
        if (sh && curkey.label!!.length < 2) c.getShifted(curkey.label!![0].code, true).toChar().toString()
        else curkey.label.toString()

    private fun viewChar(str: Array<CharSequence?>, pos: Int, ox: Float, oy: Float, canvas: Canvas, key: Key?, paint: Paint, sh: Boolean) {
        if (str.size <= pos || str[pos] == null || str[pos] == " ") return
        val lbl = if (str[pos]?.length == 1) {
            c.getShifted(str[pos]!![0].code, sh).toChar().toString()
        } else {
            str[pos]
        }
        canvas.drawText(
            lbl.toString(),
            (key!!.x + ox),
            key.y + oy + (paint.textSize - paint.descent()) / 2,
            paint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        invalidate()
        return c.onTouchEvent(me)
    }
}
