package com.vladgba.keyb

import kotlin.collections.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import java.lang.*
import java.util.*
import kotlin.math.*

class KeybView : View, View.OnClickListener {
    var keybCtl: KeybController? = null
    var buffer: Bitmap? = null
    private var bufferSh: Bitmap? = null
    private var res: Resources? = null
    var paint = Paint()
    var repMods = false

    constructor(c: KeybController) : super(c, null) {
        keybCtl = c
        initResources(c)
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun reload() {
        buffer = null
        requestLayout()
        invalidate()
    }

    override fun onClick(v: View) {}

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (keybCtl!!.keybLayout == null) return setMeasuredDimension(0, 0)
        var width = keybCtl!!.keybLayout!!.minWidth
        if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(width, keybCtl!!.keybLayout!!.height)
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
        if (keybCtl!!.keybLayout!!.bitmap != null) return
        keybCtl!!.keybLayout!!.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        keybCtl!!.keybLayout!!.bitmap?.eraseColor(Color.TRANSPARENT)
        keybCtl!!.keybLayout!!.canv = Canvas(keybCtl!!.keybLayout!!.bitmap!!)
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
        val keys = keybCtl!!.keybLayout!!.keys
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
            val padding = key.height / 16
            val radius = key.height / 6
            val shadow = key.height / 36

            val lpad = if (key.getStr("stylepos").contains("l")) -key.width else 0
            val rpad = if (key.getStr("stylepos").contains("r")) key.width else 0
            val tpad = if (key.getStr("stylepos").contains("t")) -key.height else 0
            val bpad = if (key.getStr("stylepos").contains("b")) key.height else 0

            var recty = RectF(
                (key.x + lpad + padding - shadow / 3).toFloat(),
                (key.y + tpad + padding).toFloat(),
                (key.x + rpad + key.width - padding + shadow / 3).toFloat(),
                (key.y + bpad + key.height - padding + shadow).toFloat()
            )
            canvas.drawRoundRect(recty, radius.toFloat(), radius.toFloat(), paint)
            paint.color = if (key.getStr("bg").isEmpty()) getColor("keyBackground") else Color.parseColor("#" + key.getStr("bg"))
            if (key.getBool("mod") && (key.getInt("modmeta") and keybCtl!!.mod) > 0) paint.color = getColor("modBackground")
            recty = RectF(
                (key.x + lpad + padding).toFloat(),
                (key.y + tpad + padding).toFloat(),
                (key.x + rpad + key.width - padding).toFloat(),
                (key.y + bpad + key.height - padding).toFloat()
            )
            canvas.drawRoundRect(recty, radius.toFloat(), radius.toFloat(), paint)
            paint.textSize = key.height / keybCtl!!.secondaryFont
            paint.color = getColor("primaryText")
            viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false)

            paint.color = getColor("primaryText")
            paint.textSize = key.height / keybCtl!!.primaryFont
            
            if (key.label != null) {
                val lbl = if (sh && key.label!!.length < 2) keybCtl?.getShifted(key.label!![0].code,true)!!.toChar().toString() else key.label.toString()
                canvas.drawText( lbl, key.x + key.width / 2f, key.y + (key.height + paint.textSize - paint.descent()) / 2, paint)
            }
            canvas.restore()
        }
    }

    private fun randColor(): Int {
        val a = arrayOf(0xffff0000, 0xffffff00, 0xffffffff, 0xff00ff00, 0xffff00ff, 0xff00ffff, 0xff0000ff, 0xff000000)
        return (a[Random().nextInt(8)]).toInt()
    }

    private fun getColor(n: String): Int {
        if (keybCtl!!.sett.isEmpty()) return randColor()
        val clrSw = keybCtl!!.sett.containsKey("colorSwitch") && keybCtl!!.sett.getValue("colorSwitch") == "1"
        val curTheme = if (keybCtl!!.night && clrSw) "colorNight" else "colorDay"
        if (!keybCtl!!.sett.containsKey(curTheme)) return randColor()
        val theme = keybCtl!!.sett.getValue(curTheme) as Map<String, Any>
        return if (theme.containsKey(n)) (theme.getValue(n) as String).toLong(16).toInt() else randColor()
    }

    public override fun onDraw(canvas: Canvas) {
        try {
            if (buffer == null) repaintKeyb(width, height)

            canvas.drawBitmap((if (keybCtl!!.shiftPressed()) bufferSh else buffer)!!, 0f, 0f, null)
            if (keybCtl!!.getVal(keybCtl!!.sett, "debug", "") == "1") {
                if (keybCtl!!.keybLayout!!.bitmap != null) canvas.drawBitmap(
                    keybCtl!!.keybLayout!!.bitmap!!,
                    0f,
                    0f,
                    null
                )
                paint.color = if (keybCtl!!.night) 0xffffffff.toInt() else 0xff000000.toInt()
                paint.textSize = 20.toFloat()
                canvas.drawText(keybCtl!!.mod.toString(2), 10f, 95f, paint)
            }
            canvas.drawText(keybCtl!!.erro, 40f, 20f, paint)
            if (keybCtl!!.recKey != null) canvas.drawText(keybCtl!!.recKey!!.record.size.toString(), 20f, 20f, paint)

            if (keybCtl!!.points[keybCtl!!.lastpid] == null) return
            val curkey = keybCtl!!.points[keybCtl!!.lastpid]!!
            drawKey(canvas, curkey)
        } catch (e: Exception) {
            keybCtl!!.prStack(e)
        }
    }

    fun drawKey(canvas: Canvas, curkey: Key) {
        canvas.save()

        if (!curkey.extCharsRaw.isNotEmpty() && !curkey.getBool("clipboard")) return
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = curkey.height / keybCtl!!.primaryFont
        
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
        val sh = keybCtl!!.shiftPressed()
        if (curkey.getBool("clipboard") && curkey.charPos > 0 && curkey.extChars[curkey.charPos - 1] != null) {
            paint.textSize = curkey.height / keybCtl!!.secondaryFont
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
            if (sh && curkey.label!!.length < 2) keybCtl?.getShifted(curkey.label!![0].code, true)!!.toChar().toString() else curkey.label.toString(),
            (curkey.x + x2).toFloat(),
            curkey.y + y2 + (p.textSize - p.descent()) / 2,
            p
        )
    }

    private fun viewChar(str: Array<CharSequence?>, pos: Int, ox: Int, oy: Int, canvas: Canvas, key: Key?, paint: Paint, sh: Boolean) {
        if (str.size <= pos || str[pos] == null || str[pos] == " ") return
        val lbl = if (str[pos]?.length == 1) {
            keybCtl?.getShifted(str[pos]!!.get(0).code, sh)!!.toChar().toString()
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
        return keybCtl!!.onTouchEvent(me)
    }
}
