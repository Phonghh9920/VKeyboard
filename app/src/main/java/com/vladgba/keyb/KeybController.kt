package com.vladgba.keyb

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class KeybController : InputMethodService() {
    private var keybView: KeybView? = null
    private var ctrlPressed = false
    var dm: DisplayMetrics? = null
    var shiftPressed = false
    private var keybLayout: KeybModel? = null
    private var isPortrait = true
    val defLayout = "latin"
    var currentLayout = "latin"
    private val loadedLayouts = HashMap<String, KeybModel>()
    fun reload() {
        keybLayout = KeybModel(
            this,
            "vkeyb/" + currentLayout + if (isPortrait) "-portrait" else "-landscape",
            true
        )
        keybView!!.loadVars(this)
        setKeyb()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (Settings.needReload || layoutFileChanged()) {
            reload()
            Settings.needReload = false
        }
    }

    private fun layoutFileChanged(): Boolean {
        return true
    }

    override fun onCreateInputView(): View {
        dm = this.resources.displayMetrics
        keybView = layoutInflater.inflate(R.layout.vkeybview, null, false) as KeybView
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        reload()
        keybView!!.keybCtl = this
        keybView!!.mod = 0
        return keybView!!
    }

    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && isPortrait) updateOrientation(false) else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !isPortrait) updateOrientation(
            true
        )
    }

    private fun updateOrientation(b: Boolean) {
        isPortrait = b
        setKeyb()
    }

    private fun setKeyb() {
        keybView?.keyboard = keybLayout
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        keybView!!.invalidate()
        if (keyCode < 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (keybView!!.isShown) {
                ctrlPressed = true
                if (keybView!!.ctrlModi) return true else if (keybView!!.havePoints) keybView!!.ctrlModi = true
                
                super.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, event)
                keybView!!.mod = keybView!!.mod or 12288
                if (currentLayout == defLayout) return true
                keybLayout = KeybModel(this, "vkeyb/" + defLayout + if (isPortrait) "-portrait" else "-landscape", true)
                keybView!!.loadVars(this)
                setKeyb()
                return true
            } else if (ctrlPressed) {
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (keybView!!.isShown) {
                shiftPressed = true
                if (keybView!!.shiftModi) return true else if (keybView!!.havePoints) keybView!!.shiftModi = true
                val now = System.currentTimeMillis()
                val ic = currentInputConnection
                ic?.sendKeyEvent(
                    KeyEvent(
                        now,
                        now,
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_SHIFT_LEFT,
                        0,
                        KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                    )
                )
                keybView!!.mod = keybView!!.mod or 129
                keybLayout!!.setShifted(shiftPressed)
                keybView!!.invalidate()
                return true
            } else if (shiftPressed) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        keybView!!.invalidate()
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                ctrlPressed = false
                if (keybView!!.isShown) {
                    super.onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, event)
                    keybView!!.mod = keybView!!.mod xor 12288
                    
                    if (currentLayout != defLayout) reload()
                    return true
                } else if (ctrlPressed) {
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                shiftPressed = false
                if (keybView!!.isShown) {
                    val now = System.currentTimeMillis()
                    val ic = currentInputConnection
                    ic?.sendKeyEvent(
                        KeyEvent(
                            now,
                            now,
                            KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_SHIFT_LEFT,
                            0,
                            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                        )
                    )
                    keybView!!.mod = keybView!!.mod xor 129
                    keybLayout!!.setShifted(shiftPressed)
                    keybView!!.invalidate()
                    return true
                } else if (shiftPressed) {
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun keyShiftable(keyAct: Int, key: Int) {
        val ic = currentInputConnection
        val time = System.currentTimeMillis()
        val ctrl = if (ctrlPressed || keybView!!.ctrlModi) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0
        ic.sendKeyEvent(
            KeyEvent(
                time, time,
                keyAct,
                key,
                0,
                keybView!!.mod //(if (shiftPressed || keybView!!.shiftModi) KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON else 0) or ctrl
            )
        )
    }

    private fun pressShiftable(key: Int) {
        keyShiftable(KeyEvent.ACTION_DOWN, key)
    }

    private fun releaseShiftable(key: Int) {
        keyShiftable(KeyEvent.ACTION_UP, key)
    }

    fun clickShiftable(key: Int) {
        pressShiftable(key)
        releaseShiftable(key)
    }

    fun onKey(i: Int) {
        if (i == 0) return
        val ic = currentInputConnection
        if (i in 97..122) { // a-z
            clickShiftable(i - 68)
        } else if (i < 0) {
            clickShiftable(-i)
        } else {
            Log.d("key", i.toString())
            val code = getShifted(i.toChar(), keybView!!.shiftModi || shiftPressed)
            ic.commitText(code.toString(), 1)
        }
    }

    fun onText(chars: CharSequence) {
        val ic = currentInputConnection
        ic.commitText(chars.toString(), 1)
    }

    fun getShifted(code: Char, sh: Boolean): Char {
        return if (Character.isLetter(code) && sh) code.uppercaseChar() else code
    }
}