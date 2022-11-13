package com.vladgba.keyb

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.*
import android.util.*
import android.view.*

class KeybController : KeybLayout() {
    var predict: Map<String, Any>? = null
    override var kbc = this
    val points = arrayOfNulls<Key>(10)
    var lastpid = 0
    val handler = Handler(Looper.getMainLooper())

    var recKey: Key? = null
    var erro = ""
    override fun onCreateInputView(): View {
        predict = JsonParse.map(loadFile("vkeyb/dict"))
        defLayoutJson = resources.openRawResource(R.raw.latin).bufferedReader().use { it.readText() }
        defLayo = KeybModel(this, defLayoutJson, true, true)
        val layNm = defLayout + if (isPortrait) "-portrait" else "-landscape"
        if (!loaded.containsKey(layNm) || layoutFileChanged(layNm)) {
            loaded[layNm] = KeybModel(this, "vkeyb/" + layNm, isPortrait)
        }
        val isNightMode = false
        val uiManager = (getSystemService(UI_MODE_SERVICE) as UiModeManager)
        uiManager.nightMode = if (isNightMode) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        setUIMode(this.resources.configuration)
        dm = this.resources.displayMetrics
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        keybView = KeybView(this)
        keybView!!.keybCtl = this
        reload()
        mod = 0
        return keybView!!
    }

    private fun setUIMode(cfg: Configuration) {
        when (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> night = true
            Configuration.UI_MODE_NIGHT_NO -> night = false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> night = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && isPortrait) updateOrientation(false) 
        else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !isPortrait) updateOrientation(true)
        setUIMode(cfg)
    }

    private fun updateOrientation(b: Boolean) {
        isPortrait = b
        reload()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        keybView!!.invalidate()
        if (keyCode < 0) return true
        try {
            if ((sett.getValue("redefineHardwareActions") as String) == "1") {
                keybView!!.invalidate()
                if (keybView!!.isShown && inputKey(keyCode.toString(), KeyEvent.ACTION_DOWN, true)) return true
            }
        } catch (e: Exception) { prStack(e) }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        try {
            if ((sett.getValue("redefineHardwareActions") as String) == "1") {
                keybView!!.invalidate()
                if (keybView!!.isShown && inputKey(keyCode.toString(), KeyEvent.ACTION_UP, false)) {
                    if (currentLayout != defLayout) reload()
                    return true
                }
            }
        } catch (e: Exception) { prStack(e) }
        return super.onKeyUp(keyCode, event)
    }

    fun inputKey(key: String, pr: Int, st: Boolean): Boolean {
        if (!sett.containsKey(key)) return false
        val vkey = sett.getValue(key) as Map<String, Any>
        val newmod = (vkey.getValue("mod") as String).toInt()
        mod = if (st) (mod or newmod) else mod and newmod.inv()
        keyShiftable(pr,(vkey.getValue("key") as String).toInt())
        keybView!!.repMod()
        if (currentLayout == defLayout || !st || !vkey.containsKey("switchKeyb")) return true
        if ((vkey.getValue("switchKeyb") as String) == "1") {
            keybLayout = loaded[defLayout + (if (isPortrait) "-portrait" else "-landscape")]
            keybView!!.reload()
            loadVars()
            setKeyb()
        }
        return true
    }

    fun keyShiftable(keyAct: Int, key: Int, custMod: Int = mod) {
        val ic = currentInputConnection
        if (recKey != null && recKey!!.recording) recKey!!.record.add(Key.Record(key, custMod, keyAct))
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(time, time, keyAct, key, 0, custMod))
    }

    fun clickShiftable(key: Int) {
        keyShiftable(KeyEvent.ACTION_DOWN, key)
        keyShiftable(KeyEvent.ACTION_UP, key)
    }

    fun onKey(i: Int) {
        Log.d("key", i.toString())
        if (i != 0) {
            if (i in 97..122) clickShiftable(i - 68) // find a-z for combination support
            else if (i < 0) clickShiftable(-i) // keycode
            else onText(getShifted(i, shiftPressed()).toChar().toString())
        }
    }

    private fun predict() {
        try {
            val data = currentInputConnection.getTextBeforeCursor(100, 0).toString()
            val last = data.split(" ").last()
            Log.d("Dict", last)
            Log.d("Dict", (predict!!.get(last) ?: "").toString())
            val repl = predict!!.get(last) as CharSequence? ?: return

            currentInputConnection.deleteSurroundingText(last.length, 0)
            currentInputConnection.commitText(repl, 1)
        } catch (_: Exception) { }
    }

    fun onText(chars: CharSequence) {
        if (recKey != null && recKey!!.recording) {
            if (recKey!!.record.size > 0) {
                val rc = recKey!!.record.get(recKey!!.record.size - 1)
                if (rc.keyText != "") rc.keyText = rc.keyText + chars.toString()
                else recKey!!.record.add(Key.Record(chars.toString()))
            } else {
                recKey!!.record.add(Key.Record(chars.toString()))
            }
        }
        currentInputConnection.commitText(chars.toString(), 1)
    }

    override fun onUpdateSelection(oss: Int, ose: Int, nss: Int, nse: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(oss, ose, nss, nse, cs, ce)
        predict()
    }

    fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.getActionMasked()
        val pointerIndex: Int = me.getActionIndex()
        val pid: Int = me.getPointerId(pointerIndex)
        val x = me.getX(pointerIndex).toInt()
        val y = me.getY(pointerIndex).toInt()

        val currentKey = if (points[pid] == null) keybLayout?.getKey(x, y) ?: return true else points[pid]

        try {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    lastpid = pid
                    points[pid] = currentKey
                    currentKey!!.press(x, y)
                }
                MotionEvent.ACTION_MOVE -> currentKey!!.drag(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (points[pid] != null) {
                        points[pid] = null
                        handler.removeCallbacks(currentKey!!.runnable)
                        currentKey!!.release(x, y, pid)
                    }
                }
            }
        } catch (e: Exception) { prStack(e) }
        return true
    }
}