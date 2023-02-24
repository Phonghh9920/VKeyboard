package com.vladgba.keyb

import kotlin.collections.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.view.*
import java.lang.*
import java.util.*
import kotlin.math.*

@SuppressLint("ViewConstructor")
class KeybView(private val c: KeybCtl) :  View(c, null), View.OnClickListener {
    private var buffer: Bitmap? = null
    private var bufferSh: Bitmap? = null
    private var res: Resources? = null
    private var paint = Paint()
    private var repMods = false

    init {
        initResources(c)
    }

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
        repaintKeyb(w, h)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return true
    }

    private fun initResources(context: Context) {
        res = context.resources
    }
    
    fun repMod() {
        if (buffer == null || bufferSh == null) return
        repMods = true
        keybPaint(buffer!!.width, buffer!!.height, buffer!!, false)
        keybPaint(bufferSh!!.width, bufferSh!!.height, bufferSh!!, true)
        repMods = false
        invalidate()
    }


    private fun repaintKeyb(w: Int, h: Int) {
        bufferSh = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        buffer = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        keybPaint(w, h, buffer!!, false)
        keybPaint(w, h, bufferSh!!, true)

        c.keybLayout.also {
            if (it == null) return
            it.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
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
            val x1 = key.width / 5
            val x2 = key.width / 2
            val x3 = key.width - x1
            val y1 = key.height / 5
            val y2 = key.height / 2
            val y3 = key.height - y1

            val rect = Rect(key.x, key.y, key.x + key.width, key.y + key.height)
            canvas.clipRect(rect)
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.color = getColor("keyBorder")
            val padding = key.height / 16f
            val radius = key.height / 6f
            val shadow = key.height / 36

            val lpad = if (key.getStr("stylepos").contains("l")) -radius else 0f
            val rpad = if (key.getStr("stylepos").contains("r")) radius else 0f
            val tpad = if (key.getStr("stylepos").contains("t")) -radius else 0f
            val bpad = if (key.getStr("stylepos").contains("b")) radius else 0f

            var recty = RectF(
                (key.x + lpad + padding - shadow / 3),
                (key.y + tpad + padding),
                (key.x + rpad + key.width - padding + shadow / 3),
                (key.y + bpad + key.height - padding + shadow)
            )
            canvas.drawRoundRect(recty, radius, radius, paint)
            paint.color = if (key.getStr("bg").isEmpty()) getColor("keyBackground") else Color.parseColor("#" + key.getStr("bg"))
            if (key.getBool("mod") && (key.getInt("modmeta") and c.mod) > 0) paint.color = getColor("modBackground")
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
        val lbl = if (sh && key.label!!.length < 2) c.getShifted(key.label!![0].code, true).toChar()
            .toString() else key.label.toString()
        cv.drawText(lbl, key.x + key.width / 2f, key.y + (key.height + p.textSize - p.descent()) / 2, p)
    }

    private fun randColor(): Int {
        val a = arrayOf(0xffff0000, 0xffffff00, 0xffffffff, 0xff00ff00, 0xffff00ff, 0xff00ffff, 0xff0000ff, 0xff000000)
        return (a[Random().nextInt(8)]).toInt()
    }

    private fun getColor(styleName: String): Int {
        c.sett.also {
            if (it.count() == 0) return randColor()
            val curTheme = if (c.night && it.have("colorSwitch") && it["colorSwitch"].bool()) "colorNight" else "colorDay"
            if (!it.have(curTheme)) return randColor()
            val theme = it[curTheme]
            return if (theme.have(styleName)) theme[styleName].str().toLong(16).toInt() else randColor()
        }
    }

    public override fun onDraw(canvas: Canvas) {
        try {
            if (buffer == null) repaintKeyb(width, height)

            canvas.drawBitmap((if (c.shiftPressed()) bufferSh else buffer)!!, 0f, 0f, null)
            drawModifierCode(canvas)
            canvas.drawText(c.erro, 40f, 20f, paint)
            if (c.recKey != null) canvas.drawText(c.recKey!!.record.size.toString(), 20f, 20f, paint)

            if (c.points[c.lastpid] == null) return
            val curkey = c.points[c.lastpid]!!
            drawKey(canvas, curkey)
        } catch (e: Exception) {
            c.prStack(e)
        }
    }

    private fun drawModifierCode(canvas: Canvas) {
        if (c.getVal(c.sett, "debug", "") != "1") return
        if (c.keybLayout!!.bitmap != null) canvas.drawBitmap(c.keybLayout!!.bitmap!!, 0f, 0f, null)
        paint.color = (if (c.night) 0xffffffff else 0xff000000).toInt()
        paint.textSize = 20f
        canvas.drawText(c.mod.toString(2), 10f, 95f, paint)
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
        val x1 = curkey.width / 2 - curkey.width
        val x2 = curkey.width / 2
        val x3 = curkey.width * 2 - curkey.width / 2
        val y1 = curkey.height / 2 - curkey.height
        val y2 = curkey.height / 2
        val y3 = curkey.height * 2 - curkey.height / 2


        if (!curkey.getBool("clipboard") || curkey.charPos < 1 || curkey.extChars[curkey.charPos - 1] == null) {
            val rect = Rect(
                curkey.x - curkey.width,
                curkey.y - curkey.height, curkey.x + curkey.width * 2, curkey.y + curkey.height * 2)
            canvas.clipRect(rect)
        }
        val pd = curkey.height / 36
        paint.color = getColor("keyBorder")
        var recty = RectF((curkey.x + x1 * 2).toFloat(),
            (curkey.y + y1 * 2).toFloat(), (curkey.x - x1 + x3).toFloat(), (curkey.y - y1 + y3).toFloat())
        canvas.drawRoundRect(recty, 30f, 30f, paint)
        paint.color = getColor("keyBackground")
        recty = RectF((curkey.x + x1 * 2 + pd / 3).toFloat(),
            (curkey.y + y1 * 2).toFloat(), (curkey.x - x1 + x3 - pd / 3).toFloat(), (curkey.y - y1 + y3 - pd).toFloat())
        canvas.drawRoundRect(recty, 30f, 30f, paint)
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

    private fun viewExtChars(curkey: Key?, cv: Canvas, p: Paint, sh: Boolean, x1: Int, x2: Int, x3: Int, y1: Int, y2: Int, y3: Int, h: Boolean) {
        if (curkey == null || curkey.extCharsRaw == "") return
        val str = curkey.extChars
        val xi = intArrayOf(x1, x2, x3, x1, x3, x1, x2, x3)
        val yi = intArrayOf(y1, y1, y1, y2, y2, y3, y3, y3)
        for (i in 0..7) {
            p.color = getColor("previewSelected")
            if (h) {
                if (curkey.charPos == i + 1) cv.drawCircle((curkey.x + xi[i]).toFloat(), (curkey.y + yi[i]).toFloat(), min(curkey.width, curkey.height) * 0.6f, p)
                p.color = getColor("previewText")
            } else {
                p.color = getColor("secondaryText")
            }
            viewChar(str, i, xi[i], yi[i], cv, curkey, p, sh)
        }
        if (!h) return
        if (curkey.charPos == 0) {
            p.color = getColor("previewSelected")
            cv.drawCircle((curkey.x + x2).toFloat(), (curkey.y + y2).toFloat(), min(curkey.width, curkey.height) * 0.4f, p)
        }
        p.color = getColor("previewText")
        cv.drawText(
            if (sh && curkey.label!!.length < 2) c.getShifted(curkey.label!![0].code, true).toChar().toString() else curkey.label.toString(),
            (curkey.x + x2).toFloat(),
            curkey.y + y2 + (p.textSize - p.descent()) / 2,
            p
        )
    }

    private fun viewChar(str: Array<CharSequence?>, pos: Int, ox: Int, oy: Int, canvas: Canvas, key: Key?, paint: Paint, sh: Boolean) {
        if (str.size <= pos || str[pos] == null || str[pos] == " ") return
        val lbl = if (str[pos]?.length == 1) {
            c.getShifted(str[pos]!![0].code, sh).toChar().toString()
        } else {
            str[pos]
        }
        canvas.drawText(
            lbl.toString(),
            (key!!.x + ox).toFloat(),
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
