package com.vladgba.keyb

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.*
import android.util.*
import android.view.*
import android.widget.Toast
import java.io.*

class KeybCtl : InputMethodService() {
    lateinit var manager: KeybModelMgr

    var dm: DisplayMetrics? = null
    var isPortrait = true
    var night = false

    val points = arrayOfNulls<Key>(10)
    val handler = Handler(Looper.getMainLooper())

    var mod: Int = 0

    var volumeUpPress = false
    var volumeDownPress = false
    var primaryFont = 0f
    var secondaryFont = 0f
    var horTick = 0
    var verTick = 0
    var offset = 0 // extChars
    var longPressTime = 0L
    var vibtime: Long = 0
    var sett = emptyMap<String, Any>()


    var lastpid = 0

    var recKey: Key? = null
    var erro = ""
    override fun onCreateInputView(): View {
        manager = KeybModelMgr(this)
        detectNightMode()
        updateNightState(this.resources.configuration)
        setOrientation()
        loadVars()
        mod = 0
        return manager.picture
    }

    private fun setOrientation() {
        dm = this.resources.displayMetrics
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun detectNightMode() {
        val isNightMode = false
        val uiManager = (getSystemService(UI_MODE_SERVICE) as UiModeManager)
        uiManager.nightMode = if (isNightMode) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
    }


    private fun updateNightState(cfg: Configuration) {
        when (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> night = true
            Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> night = false
        }
    }

    fun inpM(): String {
        return INPUT_METHOD_SERVICE
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && isPortrait) updateOrientation(false) 
        else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !isPortrait) updateOrientation(true)
        updateNightState(cfg)
    }

    private fun updateOrientation(b: Boolean) {
        isPortrait = b
        manager.reloadLayout()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        //manager.picture.invalidate()
        if (keyCode < 0) return true
        try {
            if ((sett.getValue("redefineHardwareActions") as String) == "1") {
                manager.picture.invalidate()
                if (manager.picture.isShown && inputKey(keyCode.toString(), KeyEvent.ACTION_DOWN, true)) return true
            }
        } catch (e: Exception) { prStack(e) }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        try {
            if ((sett.getValue("redefineHardwareActions") as String) == "1") {
                if (manager.picture.isShown && inputKey(keyCode.toString(), KeyEvent.ACTION_UP, false)) {
                    manager.picture.invalidate()
                    // TODO:
                    // if (currentLayout != defLayout) manager.reloadLayout()
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
        manager.picture.repMod()

        // TODO:
        // if (currentLayout == defLayout || !st || !vkey.containsKey("switchKeyb")) return true

        if ((vkey.getValue("switchKeyb") as String) == "1") {
            // TODO:
            // keybLayout = manager.loaded[defLayout + (if (isPortrait) "-portrait" else "-landscape")]
            manager.picture.reload()
            loadVars()
            manager.setKeyb()
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
            val datam = currentInputConnection.getTextBeforeCursor(32, 0).toString()
			
			for (i in manager.predict!!.keys) { // "teyo" "yo"
				if (datam.length < i.length) continue
				
				if (datam.subSequence(datam.length - i.length, datam.length) == i) {
					currentInputConnection.deleteSurroundingText(i.length, 0)
					currentInputConnection.commitText((manager.predict!!.get(i) as CharSequence), 1)
				}
			}

            // TODO:
			// if (predict == null) return
            // val last = datam.split(" ").last()
            // Log.d("Dict", last)
            // Log.d("Dict", (predict!!.get(last) ?: "").toString())
            // val repl = predict!![last] as CharSequence? ?: return

            // currentInputConnection.deleteSurroundingText(last.length, 0)
            // currentInputConnection.commitText(repl, 1)
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

        val currentKey = if (points[pid] == null) manager.keybLayout?.getKey(x, y) ?: return true else points[pid]

        try {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    lastpid = pid
                    points[pid] = currentKey
                    currentKey!!.press(x, y)
                }
                MotionEvent.ACTION_MOVE -> {
					currentKey!!.drag(x, y, me, pid)
				}
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    points[pid] = null
                    handler.removeCallbacks(currentKey!!.runnable)
                    currentKey!!.release(x, y, pid)
                }
            }
        } catch (e: Exception) { prStack(e) }
        return true
    }

    fun getLastModified(path: String): Long {
        return File(Environment.getExternalStorageDirectory(), "vkeyb/" + path + ".json").lastModified()
    }

    fun getVal(j: Map<String, Any>, s:String, d:String): String {
        if (!j.containsKey(s)) return d
        val r = j.getValue(s) as String
        return if (r.length > 0) r else d
    }

    fun loadVars() {
        try {
            sett = JsonParse.map(loadFile("vkeyb/settings"))
        } catch (e: Exception) {
            prStack(e)
            sett = JsonParse.map(resources.openRawResource(R.raw.settings).bufferedReader().use { it.readText() })
        }
        primaryFont = getVal(sett, "sizePrimary", "2").toFloat()
        secondaryFont = getVal(sett, "sizeSecondary", "4.5").toFloat()
        horTick = getVal(sett, "horizontalSense", "30").toInt()
        verTick = getVal(sett, "verticalSense", "50").toInt()
        offset = getVal(sett, "extendedSense", "70").toInt()
        longPressTime = getVal(sett, "longPressMs", "300").toLong()
    }

    fun prStack(e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        Toast.makeText(this, sw.toString(), Toast.LENGTH_LONG).show()
    }

    fun loadFile(name: String): String {
        val sdcard = Environment.getExternalStorageDirectory()
        val text = StringBuilder()
        val br = BufferedReader(FileReader(File(sdcard, "$name.json")))
        var line: String?
        while (br.readLine().also { line = it } != null) {
            text.append(line)
            text.append('\n')
        }
        br.close()
        Log.d("Keyb", "Done")
        return text.toString()
    }

    fun vibrate(curkey: Key, s: String) {
        val i = if (curkey.getInt(s) > 0) curkey.getInt(s).toLong() else 0L
        if (vibtime + i > System.currentTimeMillis()) return
        vibtime = System.currentTimeMillis()
        if (i < 10) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(i, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(i)
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in str.indices) out[j] = Character.getNumericValue(str[j])
        return out
    }

    fun ctrlPressed(): Boolean {
        return mod and 28672 != 0
    }

    fun shiftPressed(): Boolean {
        return mod and 193 != 0
    }

    fun getShifted(code: Int, sh: Boolean): Int {
        return if (Character.isLetter(code) && sh) code.toChar().uppercaseChar().code else code
    }
}