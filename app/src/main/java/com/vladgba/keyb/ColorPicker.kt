package com.vladgba.keyb

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlin.math.floor
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class ColorPicker(context: Context, inputColor: Int, adjustAlpha: Boolean, private val listener: ColorPickerListener) {
    interface ColorPickerListener {
        fun onCancel(dialog: ColorPicker)
        fun onOk(dialog: ColorPicker, color: Int)
    }

    private val view: View = LayoutInflater.from(context).inflate(R.layout.picker_dialog, null)
    private val dialog: AlertDialog
    private val viewHue: View = view.findViewById(R.id.picker_view_hue)
    private val viewSatValue: ColorPickerSquare = view.findViewById(R.id.picker_view_saturation_brightness)
    private val viewCursor: ImageView = view.findViewById(R.id.picker_cursor)
    private val viewAlphaCursor: ImageView = view.findViewById(R.id.picker_cursor_alpha)
    private val viewOldColor: View = view.findViewById(R.id.picker_color_old)
    private val viewNewColor: View = view.findViewById(R.id.picker_color_new)
    private val viewAlphaOverlay: View = view.findViewById(R.id.picker_view_alpha_overlay)
    private val viewTarget: ImageView = view.findViewById(R.id.picker_target)
    private val viewAlphaCheckered: ImageView = view.findViewById(R.id.picker_view_alpha)
    private val viewContainer: ViewGroup = view.findViewById(R.id.view_container)
    private val currentColorHsv = FloatArray(3)
    private var alpha: Int

    fun generateColor() = alpha shl 24 or (Color.HSVToColor(currentColorHsv) and 0x00ffffff)
    fun show() = dialog.show()

    init {
        var initialColor = inputColor
        if (!adjustAlpha) initialColor = initialColor or (0xFF shl 24)
        Color.colorToHSV(initialColor, currentColorHsv)
        alpha = Color.alpha(initialColor)

        viewAlphaOverlay.visibility = if (adjustAlpha) View.VISIBLE else View.GONE
        viewAlphaCursor.visibility = if (adjustAlpha) View.VISIBLE else View.GONE
        viewAlphaCheckered.visibility = if (adjustAlpha) View.VISIBLE else View.GONE

        viewSatValue.setHue(currentColorHsv[0])
        viewOldColor.setBackgroundColor(initialColor)
        viewNewColor.setBackgroundColor(initialColor)
        viewHue.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                var y = event.y
                if (y < 0f) y = 0f
                if (y > viewHue.measuredHeight) y = viewHue.measuredHeight - 0.001f

                var correctedHue = 360f - 360f / viewHue.measuredHeight * y
                if (correctedHue == 360f) correctedHue = 0f
                currentColorHsv[0] = correctedHue

                viewSatValue.setHue(correctedHue)
                moveCursor()
                if (adjustAlpha) updateAlphaView()
                viewNewColor.setBackgroundColor(generateColor())
                true
            } else false
        }

        if (adjustAlpha) viewAlphaCheckered.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                var y = event.y
                if (y < 0f) y = 0f
                if (y > viewAlphaCheckered.measuredHeight) y = viewAlphaCheckered.measuredHeight - 0.001f

                val a = (255f - 255f / viewAlphaCheckered.measuredHeight * y).roundToInt()
                alpha = a

                moveAlphaCursor()
                viewNewColor.setBackgroundColor(generateColor())
                true
            } else false
        }

        viewSatValue.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                var x = event.x
                var y = event.y
                if (x < 0f) x = 0f
                if (x > viewSatValue.measuredWidth) x = viewSatValue.measuredWidth.toFloat()
                if (y < 0f) y = 0f
                if (y > viewSatValue.measuredHeight) y = viewSatValue.measuredHeight.toFloat()
                currentColorHsv[1] = 1f / viewSatValue.measuredWidth * x
                currentColorHsv[2] = 1f - 1f / viewSatValue.measuredHeight * y

                moveTarget()
                if (adjustAlpha) updateAlphaView()
                viewNewColor.setBackgroundColor(generateColor())
                true
            } else false
        }

        dialog = AlertDialog.Builder(context).setPositiveButton(android.R.string.ok) { _, _ ->
            listener.onOk(this@ColorPicker, generateColor())
        }.setNegativeButton(android.R.string.cancel) { _, _ ->
            listener.onCancel(this@ColorPicker)
        }.setOnCancelListener {
            listener.onCancel(this@ColorPicker)
        }.create()

        dialog.setView(view, 0, 0, 0, 0)

        val vto = view.viewTreeObserver
        vto.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                moveCursor()
                moveTarget()
                if (adjustAlpha) {
                    moveAlphaCursor()
                    updateAlphaView()
                }
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun moveCursor() {
        var y = viewHue.measuredHeight - currentColorHsv[0] * viewHue.measuredHeight / 360f
        if (y == viewHue.measuredHeight.toFloat()) y = 0f
        val layoutParams = viewCursor.layoutParams as RelativeLayout.LayoutParams
        layoutParams.leftMargin = (viewHue.left - viewContainer.paddingLeft)
        layoutParams.topMargin =
            (viewHue.top + y - floor((viewCursor.measuredHeight / 2f).toDouble()) - viewContainer.paddingTop).toInt()
        viewCursor.layoutParams = layoutParams
    }

    private fun moveTarget() {
        val x = currentColorHsv[1] * viewSatValue.measuredWidth
        val y = (1f - currentColorHsv[2]) * viewSatValue.measuredHeight
        val layoutParams = viewTarget.layoutParams as RelativeLayout.LayoutParams
        layoutParams.leftMargin =
            (viewSatValue.left + x - floor((viewTarget.measuredWidth / 2f).toDouble()) - viewContainer.paddingLeft).toInt()
        layoutParams.topMargin =
            (viewSatValue.top + y - floor((viewTarget.measuredHeight / 2f).toDouble()) - viewContainer.paddingTop).toInt()
        viewTarget.layoutParams = layoutParams
    }

    private fun moveAlphaCursor() {
        val measuredHeight = viewAlphaCheckered.measuredHeight
        val y = measuredHeight - alpha * measuredHeight / 255f
        val layoutParams = viewAlphaCursor.layoutParams as RelativeLayout.LayoutParams
        layoutParams.leftMargin = (viewAlphaCheckered.left - viewContainer.paddingLeft)
        layoutParams.topMargin =
            (viewAlphaCheckered.top + y - floor((viewAlphaCursor.measuredHeight / 2f).toDouble()) - viewContainer.paddingTop).toInt()
        viewAlphaCursor.layoutParams = layoutParams
    }

    private fun updateAlphaView() {
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.HSVToColor(currentColorHsv), 0x0)
        )
        viewAlphaOverlay.background = gd
    }
}