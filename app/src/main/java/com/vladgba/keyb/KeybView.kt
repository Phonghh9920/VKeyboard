package com.vladgba.keyb

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.util.*
import kotlin.math.*
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;

class KeybView : View, View.OnClickListener {
    private val DEBUG: Boolean = false
    val angPos = intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)
    var keybCtl: KeybController? = null
    var havePoints = false
    var buffer: Bitmap? = null
    var ctrlModi = false
    var shiftModi = false
    private var keyb: KeybModel? = null
    private var res: Resources? = null
    private var pressX = 0
    private var pressY = 0
    private var charPos = 0
    private var relX = 0
    private var relY = 0

    /**
     * Cursor
     */
    private var delTick = 0
    private var primaryFont = 0f
    private var secondaryFont = 0f
    private var horizontalTick = 0
    private var verticalTick = 0
    private var offset = 0 // extChars
    private var cursorMoved = false
    private var currentKey: KeybModel.Key? = null
    private var bufferSh: Bitmap? = null
    private var bitmap: Bitmap? = null
    private var canv: Canvas? = null
    private var vibtime: Long = 0
    public var mod: Int = 0

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
        if (keyb == null) {
            setMeasuredDimension(0, 0)
        } else {
            var width = keyb!!.minWidth
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec)
            }
            setMeasuredDimension(width, keyb!!.height)
        }
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
        loadVars(context)
    }

    fun loadVars(context: Context) {
        try {
            //JSONObject json = new JSONObject(VKeyboard.loadKeybLayout("settings"));
            //delTick = json.getInt("del");
            val sp = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
            delTick = sp.getString("swipedel", "60")!!.toInt()
            primaryFont = sp.getString("sizeprimary", "1.9")!!.toFloat()
            secondaryFont = sp.getString("sizesecondary", "4.5")!!.toFloat()
            horizontalTick = sp.getString("swipehor", "30")!!.toInt()
            verticalTick = sp.getString("swipever", "50")!!.toInt()
            offset = sp.getString("swipeext", "70")!!.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun repaintKeyb(w: Int, h: Int) {
        keybPaint(w, h, false)
        keybPaint(w, h, true)
        if (bitmap != null) return
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap?.eraseColor(Color.TRANSPARENT)
        canv = Canvas(bitmap!!)
    }

    private fun keybPaint(w: Int, h: Int, sh: Boolean) {
        if (sh) bufferSh = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565) else buffer =
            Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas((if (sh) bufferSh else buffer)!!)
        var paint = Paint()
        paint.color = getColor(R.color.keyboardBackground)
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
            paint = Paint()
            paint.isAntiAlias = true
            paint.textAlign = Paint.Align.CENTER
            paint.color = getColor(R.color.keyBorder)
            val bi = key.height / 16
            val ki = key.height / 6
            val pd = key.height / 36

            val lpd = if (key.stylepos.contains("l")) -key.width else 0
            val rpd = if (key.stylepos.contains("r")) key.width else 0

            val tpd = if (key.stylepos.contains("t")) -key.height else 0
            val bpd = if (key.stylepos.contains("b")) key.height else 0

            var recty = RectF(
                (key.x + lpd + bi - pd / 3).toFloat(),
                (key.y + tpd + bi).toFloat(),
                (key.x + rpd + key.width - bi + pd / 3).toFloat(),
                (key.y + bpd + key.height - bi + pd).toFloat()
            )
            canvas.drawRoundRect(recty, ki.toFloat(), ki.toFloat(), paint)
            paint.color = if (key.bg.length < 1) getColor(R.color.keyBackground) else Color.parseColor("#" + key.bg)
            recty = RectF(
                (key.x + lpd + bi).toFloat(),
                (key.y + tpd + bi).toFloat(),
                (key.x + rpd + key.width - bi).toFloat(),
                (key.y + bpd + key.height - bi).toFloat()
            )
            canvas.drawRoundRect(recty, ki.toFloat(), ki.toFloat(), paint)
            paint.textSize = key.height / secondaryFont
            paint.color = getColor(R.color.primaryText)
            viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false)
            paint.color = getColor(R.color.primaryText)
            paint.textSize = key.height / primaryFont
            if (key.label != null) {
                val lbl = if (sh && key.label!!.length < 2) keybCtl?.getShifted(
                    key.label!![0],
                    true
                ).toString() else key.label.toString()
                canvas.drawText(
                    lbl,
                    key.x + key.width / 2f,
                    key.y + (key.height + paint.textSize - paint.descent()) / 2,
                    paint
                )
            }
            canvas.restore()
        }
    }

    fun vibrate(s: String) {
        val i = if (currentKey!!.getInt(s) > 0) currentKey!!.getInt(s).toLong() else 0
        if (vibtime + i > System.currentTimeMillis()) return
        vibtime = System.currentTimeMillis()
        if (i < 10) return

        val vibrator = keybCtl?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(i, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(i)
    }

    private fun getColor(textColor: Int): Int {
        return res!!.getColor(textColor, keybCtl!!.theme)
    }

    public override fun onDraw(canvas: Canvas) {
        if (buffer == null) repaintKeyb(width, height)
        canvas.drawBitmap(
            (if (keyboard!!.shifting) bufferSh else buffer)!!,
            0f,
            0f,
            null
        )
        if (bitmap != null) canvas.drawBitmap(
                bitmap!!,
                0f,
                0f,
                null
        )
        if (havePoints) drawKey(canvas)
        /*var paint = Paint()
        paint.color = 0xff000000.toInt()
        paint.textSize = 50.toFloat()
        canvas.drawText(mod.toString(2), 100f, 100f, paint)*/
    }

    private fun drawKey(canvas: Canvas) {
        canvas.save()
        val key = currentKey
        if (key!!.repeat) return
        val paint = Paint()
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = key.height / primaryFont
        paint.color = getColor(R.color.previewText)
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
        paint.color = getColor(R.color.keyBorder)
        var recty = RectF(
            (key.x + x1 * 2).toFloat(),
            (key.y + y1 * 2).toFloat(),
            (key.x - x1 + x3).toFloat(),
            (key.y - y1 + y3).toFloat()
        )
        canvas.drawRoundRect(recty, 30f, 30f, paint)
        paint.color = getColor(R.color.keyBackground)
        recty = RectF(
            (key.x + x1 * 2 + pd / 3).toFloat(),
            (key.y + y1 * 2).toFloat(),
            (key.x - x1 + x3 - pd / 3).toFloat(),
            (key.y - y1 + y3 - pd).toFloat()
        )
        canvas.drawRoundRect(recty, 30f, 30f, paint)
        paint.color = getColor(R.color.primaryText)
        val sh = keyboard!!.shifting
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
            p.color = getColor(R.color.previewSelected)
            if (h) {
                if (charPos == i + 1) cv.drawCircle((key.x + xi[i]).toFloat(), (key.y + yi[i]).toFloat(), 50f, p)
                p.color = getColor(R.color.previewText)
            } else {
                p.color = getColor(R.color.secondaryText)
            }
            viewChar(str, i, xi[i], yi[i], cv, key, p, sh)
        }
        if (!h) return
        if (charPos == 0) {
            p.color = getColor(R.color.previewSelected)
            cv.drawCircle((key.x + x2).toFloat(), (key.y + y2).toFloat(), 60f, p)
        }
        p.color = getColor(R.color.previewText)
        cv.drawText(
            if (sh && key.label!!.length < 2) keybCtl?.getShifted(key.label!![0], true).toString() else key.label.toString(),
            (key.x + x2).toFloat(),
            key.y + y2 + (p.textSize - p.descent()) / 2,
            p
        )
    }

    private fun viewChar(
        str: String,
        pos: Int,
        ox: Int,
        oy: Int,
        canvas: Canvas,
        key: KeybModel.Key?,
        paint: Paint,
        sh: Boolean
    ) {
        if (str.length <= pos || str[pos] == ' ') return
        canvas.drawText(
            keybCtl?.getShifted(str[pos], sh).toString(),
            (key!!.x + ox).toFloat(),
            key.y + oy + (paint.textSize - paint.descent()) / 2,
            paint
        )
    }

    private fun getExtPos(x: Int, y: Int): Int {
        if (abs(pressX - x) < offset && abs(pressY - y) < offset) return 0
        val angle = Math.toDegrees(atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        return angPos[ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.action
        if (me.pointerCount > 1) return false
        val x = me.getX(0).toInt()
        val y = me.getY(0).toInt()
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> press(x, y)
            MotionEvent.ACTION_MOVE -> drag(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                release(x, y)
                ctrlModi = false
                shiftModi = false
            }
        }
        invalidate()
        return true
    }

    fun press(curX: Int, curY: Int) {
        if (DEBUG) {
            val p = Paint()
            p.color = 0x22ff0000
            canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }
        
        currentKey = keyb?.getKey(curX, curY)
        Log.d("keyIndex", currentKey.toString())
        if (currentKey == null) return
        
        pressX = curX
        pressY = curY
        relX = -1
        relY = -1
        cursorMoved = false
        charPos = 0
        havePoints = true
        if (currentKey!!.repeat || currentKey!!.extChars!!.isNotEmpty()) {
            relX = curX
            relY = curY
        }
    }

    private fun drag(curX: Int, curY: Int) {
        if (currentKey!!.getBool("mod")) return
        if (currentKey!!.getBool("clipboard")) {
            charPos = getExtPos(curX, curY)
            return
        }
        if (relX < 0) return  // Not have alternative behavior
        if (currentKey!!.repeat) {
            if (!cursorMoved && (curX - horizontalTick > pressX || curX + horizontalTick < pressX || curY - verticalTick > pressY || curY + verticalTick < pressY)) {
                cursorMoved = true
            }
            while (true) {
                if (curX - horizontalTick > relX) {
                    relX += horizontalTick
                    keybCtl!!.onKey(if (currentKey!!.getInt("right") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("right"))
                    vibrate("vibtick")
                    continue
                }
                if (curX + horizontalTick < relX) {
                    relX -= horizontalTick
                    keybCtl!!.onKey(if (currentKey!!.getInt("left") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("left"))
                    vibrate("vibtick")
                    continue
                }
                if (curY - verticalTick > relY) {
                    relY += verticalTick
                    keybCtl!!.onKey(if (currentKey!!.getInt("bottom") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("bottom"))
                    vibrate("vibtick")
                    continue
                }
                if (curY + verticalTick < relY) {
                    relY -= verticalTick
                    keybCtl!!.onKey(if (currentKey!!.getInt("top") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("top"))
                    vibrate("vibtick")
                    continue
                }
                break
            }
        } else if (currentKey!!.extChars!!.isNotEmpty()) {
            relX = curX
            relY = curY
            charPos = getExtPos(curX, curY)
        }
    }

    private fun release(curX: Int, curY: Int) {
        Log.d("keyIndexRelease", currentKey.toString())
        if (!havePoints) return
        if (currentKey == null) return
        if (currentKey!!.getBool("mod")) {
            if (currentKey!!.hold) {
                keybCtl!!.releaseShiftable(currentKey!!.getInt("modcode"))
                mod = mod xor currentKey!!.getInt("modi")
                currentKey!!.hold = false
            } else {
                keybCtl!!.pressShiftable(currentKey!!.getInt("modcode"))
                mod = mod or currentKey!!.getInt("modi")
                currentKey!!.hold = true
            }
            
            return
        }
        havePoints = false
        if (curY == 0 || cursorMoved) return
        if (currentKey!!.getBool("clipboard")) {
            if (charPos < 1) return
            if (shiftModi) {
                currentKey!!.clipboard[charPos - 1] = keybCtl!!.currentInputConnection.getSelectedText(0)
            } else {
                if (currentKey!!.clipboard[charPos - 1] == null) return
                keybCtl!!.onText(currentKey!!.clipboard[charPos - 1].toString())
            }
            return
        }
        
        if (currentKey!!.rand != null && currentKey!!.rand!!.isNotEmpty()) {
            return keybCtl!!.onText(currentKey!!.rand!![Random().nextInt(currentKey!!.rand!!.size)]!!)
        }
        if (currentKey!!.lang != null && charPos == 0) {
            keybCtl?.currentLayout = currentKey!!.lang.toString()
            keybCtl!!.reload()
            return
        }
        if (currentKey!!.text!!.isNotEmpty()) {
            keybCtl!!.onText(currentKey!!.text!!)
            if (currentKey!!.getInt("pos") > 0) for (i in 1..currentKey!!.getInt("pos")) keybCtl!!.onKey(-21)
            return
        }
        if (currentKey!!.repeat && !cursorMoved) return keybCtl!!.onKey(currentKey!!.codes!![0])
        if (relX < 0 || charPos == 0) return keybCtl!!.onKey(currentKey?.codes?.get(0) ?: 0)

        val extSz = currentKey!!.extChars!!.length
        if (extSz > 0 && extSz >= charPos) {
            val textIndex = currentKey!!.extChars!![charPos - 1]
            if (textIndex == ' ') return
            textEvent(textIndex.toString())
            return
        }
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in str.indices) out[j] = Character.getNumericValue(str[j])
        return out // FIXME: Is it fixes >1 length?
    }

    private fun textEvent(data: String) {
        keybCtl!!.onKey(getFromString(data)[0])
    }
}
