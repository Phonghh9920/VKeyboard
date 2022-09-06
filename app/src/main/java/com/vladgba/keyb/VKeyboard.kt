package com.vladgba.keyb

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class VKeyboard : InputMethodService() {
    private var keybView: VKeybView? = null
    private var ctrlPressed = false
    var dm: DisplayMetrics? = null
    private val loadedLayouts = HashMap<String, Keyboard>()
    fun reload() {
        keybLayout = Keyboard(
            this,
            loadKeybLayout("vkeyb/" + currentLayout + if (isPortrait) "-portrait" else "-landscape"),
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
        keybView = layoutInflater.inflate(R.layout.vkeybview, null, false) as VKeybView
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        reload()
        keybView!!.onKeyboardActionListener = this
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
        if (keybView == null) return
        keybView!!.keyboard = keybLayout
    }

    private fun forceLatin() {
        keybLayout =
            Keyboard(this, loadKeybLayout("vkeyb/" + defLayout + if (isPortrait) "-portrait" else "-landscape"), true)
        keybView!!.loadVars(this)
        setKeyb()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        if (keyCode < 0) return true
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (keybView!!.isShown) {
                ctrlPressed = true
                if (keybView!!.ctrlModi) return true else if (keybView!!.havePoints) keybView!!.ctrlModi = true
                forceLatin()
                super.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, event)
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
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                ctrlPressed = false
                if (keybView!!.isShown) {
                    reload()
                    super.onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, event)
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

    private fun keyShiftable(keyAct: Int, key: Int, ic: InputConnection) {
        val time = System.currentTimeMillis()
        val ctrl = if (ctrlPressed || keybView!!.ctrlModi) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0
        ic.sendKeyEvent(
            KeyEvent(
                time, time,
                keyAct,
                key,
                0,
                (if (shiftPressed || keybView!!.shiftModi) KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON else 0) or ctrl
            )
        )
    }

    private fun pressShiftable(key: Int, ic: InputConnection) {
        keyShiftable(KeyEvent.ACTION_DOWN, key, ic)
    }

    private fun releaseShiftable(key: Int, ic: InputConnection) {
        keyShiftable(KeyEvent.ACTION_UP, key, ic)
    }

    private fun clickShiftable(key: Int, ic: InputConnection) {
        pressShiftable(key, ic)
        releaseShiftable(key, ic)
    }

    fun press(key: Int, ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
    }

    fun release(key: Int, ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    fun click(key: Int, ic: InputConnection) {
        currentInputConnection
        press(key, ic)
        release(key, ic)
    }

    fun onKey(i: Int) {
        if (i == 0) {
            reload()
            return
        }
        val ic = currentInputConnection
        if (i > 96 && i < 123) { // a-z
            clickShiftable(i - 68, ic)
        } else if (i < 0) {
            clickShiftable(i * -1, ic)
        } else {
            Log.d("key", i.toString())
            val code = getShiftable(i.toChar(), keybView!!.shiftModi || shiftPressed)
            ic.commitText(code.toString(), 1)
        }
    }

    fun onText(chars: CharSequence) {
        val ic = currentInputConnection
        ic.commitText(chars.toString(), 1)
    }

    fun swipeLeft() {
        clickShiftable(21, currentInputConnection)
    }

    fun swipeRight() {
        clickShiftable(22, currentInputConnection)
    }

    fun swipeDown() {
        clickShiftable(20, currentInputConnection)
    }

    fun swipeUp() {
        clickShiftable(19, currentInputConnection)
    }

    companion object {
        var shiftPressed = false
        private var keybLayout: Keyboard? = null
        private var isPortrait = true
        const val defLayout = "latin"
        @JvmField
        var currentLayout = "latin"
        fun loadKeybLayout(name: String): String {
            val sdcard = Environment.getExternalStorageDirectory()
            val file = File(sdcard, "$name.json")
            val text = StringBuilder()
            try {
                val br = BufferedReader(FileReader(file))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    text.append(line)
                    text.append('\n')
                }
                br.close()
                Log.d("Keyb", "Done")
                return text.toString()
            } catch (e: IOException) {
                Log.d("Keyb", "Error")
                Log.d("Keyb", e.message!!)
            }
            return ""
        }

        @JvmStatic
        fun getShiftable(code: Char, sh: Boolean): Char {
            return if (Character.isLetter(code) && sh) code.uppercaseChar() else code
        }
    }
}