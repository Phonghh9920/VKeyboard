package com.vladgba.keyb

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class KeybWrapper : InputMethodService() {
    private lateinit var ctrl: KeybCtl
    override fun onStartInput(attr: EditorInfo, restarting:Boolean) {
        super.onStartInput(attr, restarting)
        ctrl.setKeybType(attr.inputType)
    }

    override fun onCreate() {
        super.onCreate()
        ctrl = KeybCtl(this, this)
    }

    override fun onCreateInputView(): View {
        return ctrl.getInputView()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) = ctrl.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

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
}