package com.vladgba.keyb

import java.util.*
import android.view.*
import android.graphics.*
import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.annotation.SuppressLint

class KeybView : View, View.OnClickListener {
    var keybCtl: KeybController? = null
    var buffer: Bitmap? = null
    var keyb: KeybModel? = null
    private var res: Resources? = null
    var paint = Paint()
    private var bufferSh: Bitmap? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initResources(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initResources(context)
    }

    var keyboard: KeybModel?
        get() = keyb
        set(keyboard) {
            buffer = null
            keyb = keyboard
            requestLayout()
            invalidate()
        }

    override fun onClick(v: View) {}

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (keyb == null) return setMeasuredDimension(0, 0)
        
        var width = keyb!!.minWidth
        if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
            width = MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(width, keyb!!.height)
    }

    public override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (keyb != null) keyb!!.resize(w)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return true
    }

    private fun initResources(context: Context) {
        res = context.resources
    }


    private fun repaintKeyb(w: Int, h: Int) {
        keybPaint(w, h, false)
        keybPaint(w, h, true)
        if (keyb!!.bitmap != null) return
        keyb!!.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        keyb!!.bitmap?.eraseColor(Color.TRANSPARENT)
        keyb!!.canv = Canvas(keyb!!.bitmap!!)
    }

    private fun keybPaint(w: Int, h: Int, sh: Boolean) {
        if (sh) bufferSh = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        else buffer = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        
        val canvas = Canvas((if (sh) bufferSh else buffer)!!)
        var paint = Paint()
        paint.color = getColor("keyboardBackground")
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        canvas.drawRect(r, paint)
        val keys = keyboard!!.keys
        for (key in keys) {
            canvas.save()

            // Positions for subsymbols
            val x1 = key.width / 5
            val x2 = key.width / 2
            val x3 = key.width - x1
            val y1 = key.height / 5
            val y2 = key.height / 2
            val y3 = key.height - y1

            val rect = Rect(
                key.x,
                key.y,
                key.x + key.width,
                key.y + key.height
            )
            canvas.clipRect(rect)
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.color = getColor("keyBorder")
            val bi = key.height / 16
            val ki = key.height / 6
            val pd = key.height / 36

            val lpd = if (key!!.getStr("stylepos")!!.contains("l")) -key.width else 0
            val rpd = if (key.getStr("stylepos").contains("r")) key.width else 0
            val tpd = if (key.getStr("stylepos").contains("t")) -key.height else 0
            val bpd = if (key.getStr("stylepos").contains("b")) key.height else 0

            var recty = RectF(
                (key.x + lpd + bi - pd / 3).toFloat(),
                (key.y + tpd + bi).toFloat(),
                (key.x + rpd + key.width - bi + pd / 3).toFloat(),
                (key.y + bpd + key.height - bi + pd).toFloat()
            )
            canvas.drawRoundRect(recty, ki.toFloat(), ki.toFloat(), paint)
            paint.color = if (key.getStr("bg").length < 1) getColor("keyBackground") else Color.parseColor("#" + key.getStr("bg"))
            recty = RectF(
                (key.x + lpd + bi).toFloat(),
                (key.y + tpd + bi).toFloat(),
                (key.x + rpd + key.width - bi).toFloat(),
                (key.y + bpd + key.height - bi).toFloat()
            )
            canvas.drawRoundRect(recty, ki.toFloat(), ki.toFloat(), paint)
            paint.textSize = key.height / keybCtl!!.secondaryFont
            paint.color = getColor("primaryText")
            viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false)
            paint.color = getColor("primaryText")
            paint.textSize = key.height / keybCtl!!.primaryFont
            
            if (key.label != null) {
                val lbl = if (sh && key.label!!.length < 2) keybCtl?.getShifted(key.label!![0],true).toString() else key.label.toString()
                canvas.drawText( lbl, key.x + key.width / 2f, key.y + (key.height + paint.textSize - paint.descent()) / 2, paint)
            }
            canvas.restore()
        }
    }

    private fun randColor(): Int {
        var a = arrayOf(0xffff0000, 0xffffff00, 0xffffffff, 0xff00ff00, 0xffff00ff, 0xff00ffff, 0xff0000ff, 0xff000000)
        return (a[Random().nextInt(8)]!!)!!.toInt()
    }

    private fun getColor(n: String): Int {
        if (keybCtl!!.sett.size < 1) return randColor()
        var curTheme = if (keybCtl!!.night) "colorNight" else "colorDay"
        if (!keybCtl!!.sett.containsKey(curTheme)) return randColor()
        var theme = keybCtl!!.sett.getValue(curTheme) as Map<String, Any>
        return if (theme.containsKey(n)) (theme.getValue(n) as String).toInt(16) or 0xff000000.toInt() else randColor()
    }

    public override fun onDraw(canvas: Canvas) {
        if (buffer == null) repaintKeyb(width, height)
        canvas.drawBitmap(
            (if ((keybCtl!!.mod and 193) != 0) bufferSh else buffer)!!,
            0f,
            0f,
            null
        )
        if (keyb!!.bitmap != null) canvas.drawBitmap(keyb!!.bitmap!!, 0f, 0f, null)
        if (keybCtl!!.currentKey != null) drawKey(canvas)
        if (keybCtl!!.getVal(keybCtl!!.sett, "debug", "") == "1") {
            if (keybCtl!!.night) paint.color = 0xffffffff.toInt()
            else paint.color = 0xff000000.toInt()
            paint.textSize = 20.toFloat()
            canvas.drawText(keybCtl!!.mod.toString(2), 10f, 95f, paint)
            canvas.drawText(keybCtl!!.erro, 20f, 20f, paint)
        }
    }

    private fun drawKey(canvas: Canvas) {
        canvas.save()
        val key = keybCtl!!.currentKey
        if (!key!!.extChars!!.isNotEmpty()) return
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = key.height / keybCtl!!.primaryFont
        paint.color = getColor("previewText")
        val x1 = key.width / 2 - key.width
        val x2 = key.width / 2
        val x3 = key.width * 2 - key.width / 2
        val y1 = key.height / 2 - key.height
        val y2 = key.height / 2
        val y3 = key.height * 2 - key.height / 2
        val rect = Rect(
            key.x - key.width,
            key.y - key.height,
            key.x + key.width * 2,
            key.y + key.height * 2
        )
        canvas.clipRect(rect)
        val pd = key.height / 36
        paint.color = getColor("keyBorder")
        var recty = RectF(
            (key.x + x1 * 2).toFloat(),
            (key.y + y1 * 2).toFloat(),
            (key.x - x1 + x3).toFloat(),
            (key.y - y1 + y3).toFloat()
        )
        canvas.drawRoundRect(recty, 30f, 30f, paint)
        paint.color = getColor("keyBackground")
        recty = RectF(
            (key.x + x1 * 2 + pd / 3).toFloat(),
            (key.y + y1 * 2).toFloat(),
            (key.x - x1 + x3 - pd / 3).toFloat(),
            (key.y - y1 + y3 - pd).toFloat()
        )
        canvas.drawRoundRect(recty, 30f, 30f, paint)
        paint.color = getColor("primaryText")
        val sh = keybCtl!!.shiftPressed()
        viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, true)
        canvas.restore()
    }

    private fun viewExtChars(
        key: KeybModel.Key?,
        cv: Canvas,
        p: Paint,
        sh: Boolean,
        x1: Int,
        x2: Int,
        x3: Int,
        y1: Int,
        y2: Int,
        y3: Int,
        h: Boolean
    ) {
        if (key!!.extChars == null) return
        val str = key.extChars.toString()
        val xi = intArrayOf(x1, x2, x3, x1, x3, x1, x2, x3)
        val yi = intArrayOf(y1, y1, y1, y2, y2, y3, y3, y3)
        for (i in 0..7) {
            p.color = getColor("previewSelected")
            if (h) {
                if (keybCtl!!.charPos == i + 1) cv.drawCircle((key.x + xi[i]).toFloat(), (key.y + yi[i]).toFloat(), 50f, p)
                p.color = getColor("previewText")
            } else {
                p.color = getColor("secondaryText")
            }
            viewChar(str, i, xi[i], yi[i], cv, key, p, sh)
        }
        if (!h) return
        if (keybCtl!!.charPos == 0) {
            p.color = getColor("previewSelected")
            cv.drawCircle((key.x + x2).toFloat(), (key.y + y2).toFloat(), 60f, p)
        }
        p.color = getColor("previewText")
        cv.drawText(
            if (sh && key.label!!.length < 2) keybCtl?.getShifted(key.label!![0], true).toString() else key.label.toString(),
            (key.x + x2).toFloat(),
            key.y + y2 + (p.textSize - p.descent()) / 2,
            p
        )
    }

    private fun viewChar(str: String, pos: Int, ox: Int, oy: Int, canvas: Canvas, key: KeybModel.Key?, paint: Paint, sh: Boolean) {
        if (str.length <= pos || str[pos] == ' ') return
        canvas.drawText(
            keybCtl?.getShifted(str[pos], sh).toString(),
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
