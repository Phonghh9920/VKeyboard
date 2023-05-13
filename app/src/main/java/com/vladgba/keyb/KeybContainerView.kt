package com.vladgba.keyb

import android.content.Context
import android.view.ViewGroup
import kotlin.math.max

class KeybContainerView(context: Context) : ViewGroup(context) {

    init {
        setWillNotDraw(false)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount
        var currentTop = t + paddingTop
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            child.layout(l, currentTop, r, currentTop + child.measuredHeight)
            currentTop += child.measuredHeight
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var totalWidth = 0
        var totalHeight = 0
        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            totalWidth = max(totalWidth, child.measuredWidth)
            totalHeight += child.measuredHeight
        }
        setMeasuredDimension(totalWidth, totalHeight)
    }
}
