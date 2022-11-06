package com.vladgba.keyb

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.inputmethodservice.InputMethodService
import android.os.*
import android.util.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.io.*
import java.util.*
import kotlin.math.*

class KeybController : InputMethodService() {
    val points = arrayOfNulls<Key>(10)
    var lastpid = 0
    private val handler = Handler(Looper.getMainLooper())

    private val angPos = intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)
    var volumeUpPress = false
    var volumeDownPress = false
    var primaryFont = 0f
    var secondaryFont = 0f
    private var horizontalTick = 0
    private var verticalTick = 0
    private var offset = 0 // extChars
    private var longPressTime = 0L
    private var vibtime: Long = 0
    var recKey: Key? = null
    var shiftModi = false
    private var keybView: KeybView? = null
    var dm: DisplayMetrics? = null
    var keybLayout: KeybModel? = null
    var isPortrait = true
    var defLayo: KeybModel? = null
    var night = false
    val defLayout = "latin"
    var currentLayout = "latin"
    var defLayoutJson = ""
    private val loadedLayouts: MutableMap<String, KeybModel> = mutableMapOf()
    var erro = ""
    var mod: Int = 0
    var sett = emptyMap<String, Any>()

    fun reload() {
        pickLayout(currentLayout, isPortrait)
        if (!keybLayout!!.loaded && !isPortrait) pickLayout(currentLayout, true)
        loadVars()
        keybView!!.reload()
        setKeyb()
    }

    fun pickLayout(layNm: String, pt: Boolean) {
        val lay = layNm + if (pt) "-portrait" else "-landscape"
        if (loadedLayouts.containsKey(lay) && !layoutFileChanged(lay)) {
            keybLayout = loadedLayouts.getValue(lay)
        } else {
            keybLayout = KeybModel(this, "vkeyb/" + lay, pt)
            keybLayout!!.lastdate = getLastModified(lay)
            if (keybLayout!!.loaded) loadedLayouts[lay] = keybLayout!!
        }
    }

    fun getVal(j: Map<String, Any>, s:String, d:String): String {
        if (!j.containsKey(s)) return d
        val r = j.getValue(s) as String
        return if (r.length > 0) r else d
    }

    fun getLastModified(path: String): Long {
        return File(Environment.getExternalStorageDirectory(), "vkeyb/" + path + ".json").lastModified()
    }

    fun loadKeybLayout(name: String): String {
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
    fun loadVars() {
        try {
            sett = JsonParse.map(loadKeybLayout("vkeyb/settings"))
        } catch (e: Exception) {
            prStack(e)
            sett = JsonParse.map(resources.openRawResource(R.raw.settings).bufferedReader().use { it.readText() })
        }
        primaryFont = getVal(sett, "sizePrimary", "2").toFloat()
        secondaryFont = getVal(sett, "sizeSecondary", "4.5").toFloat()
        horizontalTick = getVal(sett, "horizontalSense", "30").toInt()
        verticalTick = getVal(sett, "verticalSense", "50").toInt()
        offset = getVal(sett, "extendedSense", "70").toInt()
        longPressTime = getVal(sett, "longPressMs", "300").toLong()
    }

    private fun layoutFileChanged(s: String): Boolean {
        return getLastModified(s) == 0L || loadedLayouts.getValue(s).lastdate == 0L || (getLastModified(s) > loadedLayouts.getValue(s).lastdate)
    }

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

    private fun setKeyb() {
        if (keybLayout?.loaded != true) {
            keybLayout = defLayo
            loadVars()
            keybView!!.reload()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            return
        }
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

    private fun keyShiftable(ac: Int, ind: Int) {
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
        if (i == 0) return
        if (i in 97..122) { // find a-z for combination support
            clickShiftable(i - 68)
        } else if (i < 0) { // keycode
            clickShiftable(-i)
        } else {
            Log.d("key", i.toString())
            onText(getShifted(i, shiftPressed()).toChar().toString())
        }
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

    fun ctrlPressed(): Boolean {
        return mod and 28672 != 0
    }
    
    fun shiftPressed(): Boolean {
        return mod and 193 != 0
    }

    fun getShifted(code: Int, sh: Boolean): Int {
        return if (Character.isLetter(code) && sh) code.toChar().uppercaseChar().code else code
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
            val p = Paint()
            p.color = 0x0fff0000
            keybLayout!!.canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }

        val currentKey = keybLayout?.getKey(curX, curY)
        if (currentKey == null) return
        lastpid = pid
        points[pid] = currentKey
        if (modifierAction(currentKey)) return
        handler.postDelayed(currentKey.runnable, longPressTime)
        currentKey.longPressed = false
        vibrate(currentKey, "vibpress")
        currentKey.pressX = curX
        currentKey.pressY = curY
        currentKey.relX = -1
        currentKey.relY = -1
        currentKey.cursorMoved = false
        currentKey.charPos = 0
        if (currentKey.repeat || currentKey.extCharsRaw.isNotEmpty()) {
            currentKey.relX = curX
            currentKey.relY = curY
        }
    }

    private fun drag(curX: Int, curY: Int, pid: Int) {
        if (points[pid] == null) return
        val curkey = points[pid]!!
        if (!curkey.cursorMoved && (curX - horizontalTick > curkey.pressX || curX + horizontalTick < curkey.pressX || curY - verticalTick > curkey.pressY || curY + verticalTick < curkey.pressY)) {
            handler.removeCallbacks(curkey.runnable)
        }
        if (curkey.getBool("mod") == true) return
        if (curkey.getBool("clipboard")) {
            curkey.charPos = getExtPos(curX, curY, curkey)
            return
        }
        if (curkey.relX < 0) return // Not have alternative behavior
        if (curkey.repeat) {
            if (!curkey.cursorMoved && (curX - horizontalTick > curkey.pressX || curX + horizontalTick < curkey.pressX || curY - verticalTick > curkey.pressY || curY + verticalTick < curkey.pressY)) {
                curkey.cursorMoved = true
            }
            while (true) {
                if (curX - horizontalTick > curkey.relX) curkey.relX = swipeAction(curkey, curkey.relX, horizontalTick, "right", true)
                else if (curX + horizontalTick < curkey.relX) curkey.relX = swipeAction(curkey, curkey.relX, horizontalTick, "left", false)
                else break
            }
            while (true) {
                if (curY - verticalTick > curkey.relY) curkey.relY = swipeAction(curkey, curkey.relY, verticalTick, "bottom", true)
                else if (curY + verticalTick < curkey.relY) curkey.relY = swipeAction(curkey, curkey.relY, verticalTick, "top", false)
                else break
            }
        } else if (curkey.extCharsRaw.isNotEmpty()) {
            curkey.relX = curX
            curkey.relY = curY
            val tmpPos = curkey.charPos
            curkey.charPos = getExtPos(curX, curY, curkey)
            if (curkey.charPos != 0 && tmpPos != curkey.charPos) vibrate(curkey, "vibext")
        }
    }

    private fun swipeAction(curkey: Key, r: Int, t: Int, s: String, add: Boolean): Int {
        onKey(if (curkey.getInt(s) == 0) curkey.codes!![0] else curkey.getInt(s))
        vibrate(curkey, "vibtick")
        return if (add) r + t else r - t

    }

    private fun release(curX: Int, curY: Int, pid: Int) {
        if (points[pid] == null) return
        val curkey = points[pid]!!
        handler.removeCallbacks(curkey.runnable)
        if(curkey.longPressed) return
        if (curkey.getBool("mod")) {
            if (lastpid != pid) modifierAction(curkey)
            return
        }
        if (curkey.charPos == 0) vibrate(curkey, "vibrelease")
        if (curY == 0 || curkey.cursorMoved) return
        if (recordAction(curX, curY, curkey) || clipboardAction(curkey) || shiftAction(curkey)) return
        if (curkey.getBool("app")) this.startActivity(this.packageManager.getLaunchIntentForPackage(curkey.getStr("app")))

        val extSz = curkey.extCharsRaw.length
        if (extSz > 0 && extSz >= curkey.charPos && curkey.charPos > 0) {
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
            reload()
            return
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

    private fun shiftAction(curkey: Key): Boolean {
        if (!shiftPressed() || !curkey.getBool("shift")) return false
        try {
            val tx = currentInputConnection.getSelectedText(0).toString()
            val res = when (curkey.getStr("shift")) {
                "upperAll" -> tx.uppercase(Locale.ROOT)
                "lowerAll" -> tx.lowercase(Locale.ROOT)
                else -> ""
            }
            if (res != "") onText(res)
        } catch (_: Exception) {}
        return true
    }
    private fun clipboardAction(curkey: Key): Boolean {
        if (!curkey.getBool("clipboard")) return false
        try {
            if (ctrlPressed()) {
                val tx = currentInputConnection.getSelectedText(0).toString()
                if (shiftPressed()) {
                    for (i in 0 until tx.length) onText("\\u" + tx[i].code.toString(16).uppercase().padStart(4, '0'))
                } else {
                    if (tx.indexOf("u") >= 0) {
                        val arr = tx.split("\\u")
                        for (i in 1 until arr.size) onText(arr[i].toInt(16).toChar().toString())
                    } else if (tx.indexOf(" ") >= 0) {
                        val arr = tx.split(" ")
                        for (i in 1 until arr.size) onText(arr[i].toInt().toChar().toString())
                    } else {
                        onText(tx.toInt().toChar().toString())
                    }
                }
                return true
            }
            if (curkey.charPos < 1) return true
            if (shiftPressed()) {
                curkey.extChars[curkey.charPos - 1] = currentInputConnection.getSelectedText(0)
            } else {
                if (curkey.extChars[curkey.charPos - 1] == null) return true
                onText(curkey.extChars[curkey.charPos - 1].toString())
            }
        } catch (_: Exception) { }
        return true
    }

    private fun recordAction(curX: Int, curY: Int, currentKey: Key): Boolean {
        if (!currentKey.getBool("record")) return false
        if (ctrlPressed()) {
            if (recKey != null) recKey!!.record.clear()
        } else if (getExtPos(curX, curY, currentKey) > 0) {
            if (getExtPos(curX, curY, currentKey) % 2 != 0) {
                if (recKey == null) return true
                recKey!!.recording = false
            } else {
                recKey = currentKey
                recKey!!.recording = true
            }
        } else {
            if (recKey == null || recKey!!.record.size == 0) return true
            for (i in 0 until recKey!!.record.size) {
                recKey!!.record.get(i).replay(this)
            }
        }
        return true
    }

    private fun modifierAction(currentKey: Key): Boolean {
        if (currentKey.getBool("mod")) {
            if ((mod and currentKey.getInt("modmeta")) > 0) {
                mod = mod and currentKey.getInt("modmeta").inv()
                keyShiftable(KeyEvent.ACTION_UP, currentKey.getInt("modkey"))
                keybView!!.repMod()
            } else {
                mod = mod or currentKey.getInt("modmeta")
                keyShiftable(KeyEvent.ACTION_DOWN, currentKey.getInt("modkey"))
                keybView!!.repMod()
            }
            return true
        }
        return false
    }

    private fun getExtPos(x: Int, y: Int, curkey: Key): Int {
        if (abs(curkey.pressX - x) < offset && abs(curkey.pressY - y) < offset) return 0
        val angle = Math.toDegrees(atan2((curkey.pressY - y).toDouble(), (curkey.pressX - x).toDouble()))
        return angPos[ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
    }

    fun vibrate(curkey: Key, s: String) {
        val i = if (curkey.getInt(s) > 0) curkey.getInt(s).toLong() else 0L
        if (vibtime + i > System.currentTimeMillis()) return
        vibtime = System.currentTimeMillis()
        if (i < 10) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(i, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(i)
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in str.indices) out[j] = Character.getNumericValue(str[j])
        return out
    }

    fun prStack(e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        Toast.makeText(this, sw.toString(), Toast.LENGTH_LONG).show()
    }

    fun longPress(curkey: Key) {
        if (curkey.getInt("hold") == 0) return
        curkey.longPressed = true
        if (curkey.getStr("hold").length > 1) onText(curkey.getStr("hold"))
        else onKey(curkey.getInt("hold"))
    }
}