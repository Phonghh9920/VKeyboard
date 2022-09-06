package com.vladgba.keyb

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.vladgba.keyb.VKeyboard.Companion.getShiftable
import java.util.*

class VKeybView : View, View.OnClickListener {
    val angPos = intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)
    var onKeyboardActionListener: VKeyboard? = null
    var havePoints = false
    var buffer: Bitmap? = null
    var ctrlModi = false
    var shiftModi = false
    private var keyb: Keyboard? = null
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
    private var offset // extChars
            = 0
    private var cursorMoved = false
    private var currentKey: Keyboard.Key? = null
    private var bufferSh: Bitmap? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initResources(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initResources(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    var keyboard: Keyboard?
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

    fun getKey(x: Int, y: Int): Keyboard.Key? {
        var mr = 0
        for (i in keyb!!.rows.indices) {
            val row = keyb!!.rows[i]
            if (row.height + mr >= y) {
                var mk = 0
                for (j in row.keys.indices) {
                    val k = row.keys[j]
                    if (k.width + mk >= x) return k
                    mk += k.width
                }
                break
            }
            mr += row.height
        }
        return null
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
    }

    private fun keybPaint(w: Int, h: Int, sh: Boolean) {
        if (sh) bufferSh = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565) else buffer =
            Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val canvas = Canvas((if (sh) bufferSh else buffer)!!)
        var paint = Paint()
        paint.color = getColor(R.color.keyboardBackground)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        canvas.drawRect(r, paint)
        val keys = keyboard!!.getKeys()
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
            var recty = RectF(
                (key.x + bi - pd / 3).toFloat(),
                (key.y + bi).toFloat(),
                (key.x + key.width - bi + pd / 3).toFloat(),
                (key.y + key.height - bi + pd).toFloat()
            )
            canvas.drawRoundRect(recty, ki.toFloat(), ki.toFloat(), paint)
            paint.color = getColor(R.color.keyBackground)
            recty = RectF(
                (key.x + bi).toFloat(),
                (key.y + bi).toFloat(),
                (key.x + key.width - bi).toFloat(),
                (key.y + key.height - bi).toFloat()
            )
            canvas.drawRoundRect(recty, ki.toFloat(), ki.toFloat(), paint)
            paint.textSize = key.height / secondaryFont
            paint.color = getColor(R.color.primaryText)
            viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false)
            paint.color = getColor(R.color.primaryText)
            paint.textSize = key.height / primaryFont
            if (key.label != null) {
                val lbl = if (sh && key.label!!.length < 2) getShiftable(
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

    private fun getColor(textColor: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) res!!.getColor(
            textColor,
            onKeyboardActionListener!!.theme
        ) else res!!.getColor(textColor)
    }

    public override fun onDraw(canvas: Canvas) {
        if (buffer == null) repaintKeyb(width, height)
        /*Canvas c = new Canvas(buffer);
        Paint p = new Paint();
        p.setColor(0xffff0000);
        c.drawCircle(pressX, pressY, 2, p);*/canvas.drawBitmap(
            (if (keyboard!!.shifted) bufferSh else buffer)!!,
            0f,
            0f,
            null
        )
        if (havePoints) drawKey(canvas)
    }

    private fun drawKey(canvas: Canvas) {
        canvas.save()
        val key = currentKey
        if (key!!.cursor) return
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
        val sh = keyboard!!.shifted
        viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, true)
        canvas.restore()
    }

    private fun viewExtChars(
        key: Keyboard.Key?,
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
        if (h) {
            if (charPos == 0) {
                p.color = getColor(R.color.previewSelected)
                cv.drawCircle((key.x + x2).toFloat(), (key.y + y2).toFloat(), 60f, p)
            }
            p.color = getColor(R.color.previewText)
            cv.drawText(
                if (sh && key.label!!.length < 2) getShiftable(
                    key.label!![0],
                    true
                ).toString() else key.label.toString(),
                (
                        key.x + x2).toFloat(),
                key.y + y2 + (p.textSize - p.descent()) / 2,
                p
            )
        }
    }

    private fun viewChar(
        str: String,
        pos: Int,
        ox: Int,
        oy: Int,
        canvas: Canvas,
        key: Keyboard.Key?,
        paint: Paint,
        sh: Boolean
    ) {
        if (str.length <= pos || str[pos] == ' ') return
        canvas.drawText(
            getShiftable(str[pos], sh).toString(),
            (
                    key!!.x + ox).toFloat(),
            key.y + oy + (paint.textSize - paint.descent()) / 2,
            paint
        )
    }

    private fun getExtPos(x: Int, y: Int): Int {
        if (Math.abs(pressX - x) < offset && Math.abs(pressY - y) < offset) return 0
        val angle = Math.toDegrees(Math.atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        return angPos[Math.ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
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
        pressX = curX
        pressY = curY
        relX = -1
        relY = -1
        cursorMoved = false
        charPos = 0
        currentKey = getKey(curX, curY)
        Log.d("keyIndex", currentKey.toString())
        if (currentKey == null) return
        havePoints = true
        if (currentKey!!.cursor || currentKey!!.repeat || currentKey!!.extChars!!.length > 0) {
            relX = curX
            relY = curY
        }
    }

    private fun drag(curX: Int, curY: Int) {
        if (relX < 0) return  // Not have alternative behavior
        if (currentKey!!.repeat) {
            if (!cursorMoved && (curX - delTick > pressX || curX + delTick < pressX)) {
                cursorMoved = true
            }
            while (true) {
                if (curX - delTick > relX) {
                    relX += delTick
                    onKeyboardActionListener!!.onKey(if (currentKey!!.forward == 0) currentKey!!.codes!![0] else currentKey!!.forward)
                    continue
                }
                if (curX + delTick < relX) {
                    relX -= delTick
                    onKeyboardActionListener!!.onKey(if (currentKey!!.backward == 0) currentKey!!.codes!![0] else currentKey!!.backward)
                    continue
                }
                break
            }
        } else if (currentKey!!.cursor) {
            if (!cursorMoved && (curX - horizontalTick > pressX || curX + horizontalTick < pressX || curY - verticalTick > pressY || curY + verticalTick < pressY)) {
                cursorMoved = true
            }
            while (true) {
                if (curX - horizontalTick > relX) {
                    relX += horizontalTick
                    onKeyboardActionListener!!.swipeRight()
                    continue
                }
                if (curX + horizontalTick < relX) {
                    relX -= horizontalTick
                    onKeyboardActionListener!!.swipeLeft()
                    continue
                }
                if (curY - verticalTick > relY) {
                    relY += verticalTick
                    onKeyboardActionListener!!.swipeDown()
                    continue
                }
                if (curY + verticalTick < relY) {
                    relY -= horizontalTick
                    onKeyboardActionListener!!.swipeUp()
                    continue
                }
                break
            }
        } else if (currentKey!!.extChars!!.length > 0) {
            relX = curX
            relY = curY
            charPos = getExtPos(curX, curY)
        }
    }

    private fun release(curX: Int, curY: Int) {
        Log.d("keyIndexRelease", currentKey.toString())
        if (!havePoints) return
        if (currentKey == null) return
        havePoints = false
        if (curY == 0) return
        if (currentKey!!.cursor && cursorMoved) return
        if (currentKey!!.rand != null && currentKey!!.rand!!.size > 0) {
            onKeyboardActionListener!!.onText(currentKey!!.rand!![Random().nextInt(currentKey!!.rand!!.size)]!!)
            return
        }
        if (currentKey!!.lang != null && charPos == 0) {
            VKeyboard.currentLayout = currentKey!!.lang.toString()
            onKeyboardActionListener!!.onKey(0)
            return
        }
        if (currentKey!!.text!!.length > 0) {
            onKeyboardActionListener!!.onText(currentKey!!.text!!)
            return
        } else if (currentKey!!.repeat) {
            if (!cursorMoved) onKeyboardActionListener!!.onKey(currentKey!!.codes!![0])
            return
        }
        if (relX < 0) {
            createCustomKeyEvent(currentKey!!.codes)
            return  // Not have alternative behavior
        }
        if (charPos != 0) {
            val extSz = currentKey!!.extChars!!.length
            if (extSz > 0 && extSz >= charPos) {
                val textIndex = currentKey!!.extChars!![charPos - 1]
                if (textIndex == ' ') return
                createCustomKeyEvent(textIndex.toString())
                return
            }
        }
        createCustomKeyEvent(currentKey!!.codes)
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in 0 until str.length) out[j] = Character.getNumericValue(str[j])
        return out // FIXME: Is it fixes >1 length?
    }

    private fun createCustomKeyEvent(data: String) {
        onKeyboardActionListener!!.onKey(getFromString(data)[0])
    }

    private fun createCustomKeyEvent(data: IntArray?) {
        onKeyboardActionListener!!.onKey(data!![0])
    }
}