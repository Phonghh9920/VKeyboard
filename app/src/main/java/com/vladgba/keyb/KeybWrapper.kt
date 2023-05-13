package com.vladgba.keyb

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout


class KeybWrapper : InputMethodService() {
    private lateinit var ctrl: KeybCtl

    override fun onStartInput(attr: EditorInfo, restarting: Boolean) {
        super.onStartInput(attr, restarting)
        ctrl.setKeybType(attr.inputType)
    }

    override fun onCreate() {
        super.onCreate()
        ctrl = KeybCtl(this, this)
    }

    override fun onCreateInputView() = KeybContainerView(this).apply { addView(ctrl.getInputView()) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        ctrl.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = ctrl.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        ctrl.onConfigurationChanged(cfg)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ctrl.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ctrl.onTrimMemory(level)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ctrl.setKeybTheme()
        ctrl.reloadLayout()
    }

    override fun onEvaluateFullscreenMode() = false


    override fun setInputView(view: View) {
        super.setInputView(view)
        updateSoftInputWindowLayoutParams()
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        updateSoftInputWindowLayoutParams()
    }

    private fun updateSoftInputWindowLayoutParams() {
        val window: Window = window.window ?: return

        window.attributes.let {
            if (it != null && it.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                it.height = ViewGroup.LayoutParams.MATCH_PARENT
                window.attributes = it
            }
        }

        val view = window.findViewById<View>(android.R.id.inputArea).parent as View
        view.layoutParams.let {
            val height = if (isFullscreenMode) ViewGroup.LayoutParams.MATCH_PARENT
            else ViewGroup.LayoutParams.WRAP_CONTENT

            if (it != null && it.height != height) {
                it.height = height
                view.layoutParams = it
            }
        }

        val lp = view.layoutParams
        if (lp is LinearLayout.LayoutParams) {
            if (lp.gravity != Gravity.BOTTOM) {
                lp.gravity = Gravity.BOTTOM
                view.layoutParams = lp
            }
        } else if (lp is FrameLayout.LayoutParams) {
            if (lp.gravity != Gravity.BOTTOM) {
                lp.gravity = Gravity.BOTTOM
                view.layoutParams = lp
            }
        } else {
            throw IllegalArgumentException(
                "Layout parameter doesn't have gravity: " + lp.javaClass.name
            )
        }
    }
}