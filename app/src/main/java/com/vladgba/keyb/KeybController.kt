package com.vladgba.keyb

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Paint
import android.os.*
import android.util.*
import android.view.*
import java.util.*

class KeybController : KeybLayout() {
    override var kbc = this
    val points = arrayOfNulls<Key>(10)
    var lastpid = 0
    private val handler = Handler(Looper.getMainLooper())

    var recKey: Key? = null
    var shiftModi = false
    var erro = ""

    override fun onCreateInputView(): View {
        defLayoutJson = resources.openRawResource(R.raw.latin).bufferedReader().use { it.readText() }
        defLayo = KeybModel(this, defLayoutJson, true, true)
        val layNm = defLayout + if (isPortrait) "-portrait" else "-landscape"
        if (!loadedLayouts.containsKey(layNm) || layoutFileChanged(layNm)) {
            loadedLayouts[layNm] = KeybModel(this, "vkeyb/" + layNm, isPortrait)
        }
        val isNightMode = false
        val uiManager = (getSystemService(UI_MODE_SERVICE) as UiModeManager)
        uiManager.nightMode = if (isNightMode) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        when (this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> night = true
            Configuration.UI_MODE_NIGHT_NO -> night = false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> night = false
        }
        dm = this.resources.displayMetrics
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        keybView = KeybView(this)
        keybView!!.keybCtl = this
        reload()
        mod = 0
        return keybView!!
    }

    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && isPortrait) updateOrientation(false) 
        else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !isPortrait) updateOrientation(true)

        when (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> night = true
            Configuration.UI_MODE_NIGHT_NO -> night = false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> night = false
        }
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
            if ((sett.getValue("redefineVolumeActions") as String) == "1") {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> if (keybView!!.isShown) {
                        if (volumeUpPress) return true
                        volumeUpPress = true
                        inputKey("volumeUp", KeyEvent.ACTION_DOWN, true)
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> if (keybView!!.isShown) {
                        if (volumeDownPress) return true
                        volumeDownPress = true
                        inputKey("volumeDown", KeyEvent.ACTION_DOWN, true)
                        return true
                    }
                }
            }
        } catch (_: Exception) { }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        try {
            if ((sett.getValue("redefineVolumeActions") as String) == "1") {
                keybView!!.invalidate()
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        volumeUpPress = false
                        if (keybView!!.isShown) {
                            inputKey("volumeUp", KeyEvent.ACTION_UP, false)
                            if (currentLayout != defLayout) reload()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        volumeDownPress = false
                        if (keybView!!.isShown) {
                            inputKey("volumeDown", KeyEvent.ACTION_UP, false)
                            if (currentLayout != defLayout) reload()
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return super.onKeyUp(keyCode, event)
    }

    fun inputKey(key: String, pr: Int, st: Boolean) {
        try {
            val newmod = ((sett.getValue(key) as Map<String, Any>).getValue("mod") as String).toInt()
            mod = if (st) (mod or newmod) else mod and newmod.inv()
            keyShiftable(pr,((sett.getValue(key) as Map<String, Any>).getValue("key") as String).toInt())
            keybView!!.repMod()
            if (currentLayout == defLayout || !st) return
            if (!sett.containsKey(key)) return
            val vkey = sett.getValue(key) as Map<String, Any>
            if (!vkey.containsKey("switchKeyb")) return
            if ((vkey.getValue("switchKeyb") as String) == "1") {
                keybLayout = loadedLayouts[defLayout + (if (isPortrait) "-portrait" else "-landscape")]
                keybView!!.reload()
                loadVars()
                setKeyb()
            }
        } catch (e: Exception) { prStack(e) }
    }

    fun keyShiftable(ac: Int, ind: Int) {
        keyShiftable(ac, ind, mod)
    }

    fun keyShiftable(keyAct: Int, key: Int, custMod: Int) {
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
        if (i == 0) return

        if (i in 97..122) clickShiftable(i - 68) // find a-z for combination support
        else if (i < 0) clickShiftable(-i) // keycode
        else onText(getShifted(i, shiftPressed()).toChar().toString())
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

    fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.getActionMasked()
        val pointerIndex: Int = me.getActionIndex()
        val pid: Int = me.getPointerId(pointerIndex)
        val x = me.getX(pointerIndex).toInt()
        val y = me.getY(pointerIndex).toInt()
        try{
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> press(x, y, pid)
                MotionEvent.ACTION_MOVE -> drag(x, y, pid)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    release(x, y, pid)
                    volumeUpPress = false
                    shiftModi = false
                    points[pid] = null
                }
            }
        } catch (e: Exception) { prStack(e) }
        return true
    }

    fun press(curX: Int, curY: Int, pid: Int) {
        if (getVal(sett, "debug", "") == "1") {
            val p = Paint().also { it.color = 0x0fff0000}
            keybLayout!!.canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }

        val currentKey = keybLayout?.getKey(curX, curY) ?: return

        currentKey.also { ck ->
            lastpid = pid
            points[pid] = ck
            if (ck.modifierAction()) return
            handler.postDelayed(ck.runnable, longPressTime)
            ck.longPressed = false
            vibrate(ck, "vibpress")
            ck.pressX = curX
            ck.pressY = curY
            ck.relX = -1
            ck.relY = -1
            ck.cursorMoved = false
            ck.charPos = 0
            if (ck.repeat || ck.extCharsRaw.isNotEmpty()) {
                ck.relX = curX
                ck.relY = curY
            }
        }
    }

    private fun drag(curX: Int, curY: Int, pid: Int) {
        if (points[pid] == null) return
        val curkey = points[pid]!!
        if (!curkey.cursorMoved && (curX - horizontalTick > curkey.pressX || curX + horizontalTick < curkey.pressX || curY - verticalTick > curkey.pressY || curY + verticalTick < curkey.pressY)) {
            handler.removeCallbacks(curkey.runnable)
        }
        if (curkey.getBool("mod")) return
        if (curkey.getBool("clipboard")) {
            curkey.charPos = curkey.getExtPos(curX, curY)
            return
        }
        if (curkey.relX < 0) return // Not have alternative behavior
        curkey.repeating(curX, curY)
        curkey.procExtChars(curX, curY)
    }

    private fun release(curX: Int, curY: Int, pid: Int) {
        if (points[pid] == null) return
        val curkey = points[pid]!!
        handler.removeCallbacks(curkey.runnable)
        if(curkey.longPressed) return
        if (curkey.charPos == 0) vibrate(curkey, "vibrelease")
        if (curkey.getBool("mod")) {
            if (lastpid != pid) curkey.modifierAction()
            return
        }
        if (curY == 0 || curkey.cursorMoved) return
        if (curkey.recordAction(curX, curY) || curkey.clipboardAction() || curkey.shiftAction()) return
        if (curkey.getBool("app")) this.startActivity(this.packageManager.getLaunchIntentForPackage(curkey.getStr("app")))

        val extSz = curkey.extCharsRaw.length
        if (extSz > 0 && curkey.charPos > 0) {
            val textIndex = curkey.extChars[curkey.charPos - 1]
            if (textIndex.isNullOrEmpty() || textIndex == " ") return
            if (textIndex.length > 1) onText(textIndex)
            else onKey(getFromString(textIndex.toString())[0])
            return
        }
        if (curkey.rand != null && curkey.rand!!.isNotEmpty()) {
            return onText(curkey.rand!![Random().nextInt(curkey.rand!!.size)]!!)
        }
        if (curkey.getStr("lang").isNotEmpty() && curkey.charPos == 0) {
            currentLayout = curkey.getStr("lang")
            return reload()
        }
        if (curkey.text != null && curkey.text!!.length > 0) {
            if (curkey.text!!.length == 1) onText(getShifted(curkey.text!![0].code, shiftPressed()).toChar().toString())
            else onText(curkey.text!!)
            if (curkey.getInt("pos") < 0) for (i in 1..-curkey.getInt("pos")) onKey(-21)
            else if (curkey.getInt("pos") > 0) for (i in 1..curkey.text!!.length - curkey.getInt("pos")) onKey(-21)
            else if (curkey.getStr("pos") == "0") for (i in 1..curkey.text!!.length) onKey(-21)
            return
        }
        if (curkey.repeat && !curkey.cursorMoved) return onKey(curkey.codes!![0])
        if (curkey.relX < 0 || curkey.charPos == 0) return onKey(curkey.codes?.get(0) ?: 0)
    }
}