package com.vladgba.keyb

import java.io.*
import java.util.*
import android.os.*
import kotlin.math.*
import android.util.*
import android.view.*
import android.view.inputmethod.*
import android.inputmethodservice.InputMethodService
import android.content.res.Configuration
import android.app.UiModeManager
import android.content.Context
import android.graphics.Paint

class KeybController : InputMethodService() {
    val angPos = intArrayOf(4, 1, 2, 3, 5, 8, 7, 6, 4)
    var volumeUpPress = false
    var volumeDownPress = false
    var primaryFont = 0f
    var secondaryFont = 0f
    private var horizontalTick = 0
    private var verticalTick = 0
    private var offset = 0 // extChars
    private var cursorMoved = false
    private var vibtime: Long = 0
    private var pressX = 0
    private var pressY = 0
    var charPos = 0
    private var relX = 0
    private var relY = 0
    var currentKey: KeybModel.Key? = null
    var shiftModi = false
    private var keybView: KeybView? = null
    var dm: DisplayMetrics? = null
    var keybLayout: KeybModel? = null
    var isPortrait = true
    var night = true
    val defLayout = "latin"
    var currentLayout = "latin"
    private val loadedLayouts: MutableMap<String, KeybModel> = mutableMapOf()
    var erro = ""
    var mod: Int = 0
    public var sett = emptyMap<String, Any>()

    fun reload() {
        var layNm = currentLayout + if (isPortrait) "-portrait" else "-landscape"
        if (loadedLayouts.containsKey(layNm) && !layoutFileChanged()) {
            keybLayout = loadedLayouts.getValue(layNm)
        } else {
            keybLayout = KeybModel(this, "vkeyb/" + layNm, isPortrait)
            loadedLayouts[layNm] = keybLayout!!
        }
        if (!keybLayout!!.loaded) return
        loadVars()
        keybView!!.reload()
        setKeyb()
    }
    fun getVal(j: Map<String, Any>, s:String, d:String): String {
        if (!j.containsKey(s)) return d
        val r = j.getValue(s) as String
        if (r.length > 0) return r
        else return d
    }

    public fun getLastModified(path: String, p: Boolean): Long {
        val f = File(Environment.getExternalStorageDirectory(), "vkeyb/" + path + (if (p) "-portrait" else "-landscape") + ".json")
        return f.lastModified()
    }

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
        } catch (e: Exception) {
            Log.d("Keyb", e.message!!)
            return e.message!!
        }
    }
    fun loadVars() {
        sett = JsonParse.map(loadKeybLayout("vkeyb/settings"))
        primaryFont = getVal(sett, "sizePrimary", "2").toFloat()
        secondaryFont = getVal(sett, "sizeSecondary", "4.5").toFloat()
        horizontalTick = getVal(sett, "horizontalSense", "30").toInt()
        verticalTick = getVal(sett, "verticalSense", "50").toInt()
        offset = getVal(sett, "extendedSense", "70").toInt()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        reload()
    }

    private fun layoutFileChanged(): Boolean {
        return getLastModified(currentLayout, isPortrait) > loadedLayouts.getValue(currentLayout + (if (isPortrait) "-portrait" else "-landscape")).lastdate
        
    }

    override fun onCreateInputView(): View {
        loadedLayouts.clear()
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
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            return
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
        keybView!!.invalidate()
        if (keyCode < 0) return true
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
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keybView == null) return false
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
        return super.onKeyUp(keyCode, event)
    }

    fun inputKey(key: String, pr: Int, st: Boolean) {
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
    }

    private fun keyShiftable(ac: Int, ind: Int) {
        keyShiftable(ac, ind, mod)
    }

    public fun keyShiftable(keyAct: Int, key: Int, custMod: Int) {
        val ic = currentInputConnection
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(
            KeyEvent(
                time, time,
                keyAct,
                key,
                0,
                custMod
            )
        )
    }

    fun pressShiftable(key: Int) {
        keyShiftable(KeyEvent.ACTION_DOWN, key)
    }

    fun releaseShiftable(key: Int) {
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
            val code = getShifted(i.toChar(), shiftPressed())
            ic.commitText(code.toString(), 1)
        }
    }

    fun onText(chars: CharSequence) {
        val ic = currentInputConnection
        ic.commitText(chars.toString(), 1)
    }

    fun shiftPressed(): Boolean {
        return mod and 193 != 0
    }
    fun getShifted(code: Char, sh: Boolean): Char {
        return if (Character.isLetter(code) && sh) code.uppercaseChar() else code
    }

    fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.action
        if (me.pointerCount > 1) return false
        val x = me.getX(0).toInt()
        val y = me.getY(0).toInt()
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> press(x, y)
            MotionEvent.ACTION_MOVE -> drag(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                release(x, y)
                volumeUpPress = false
                currentKey = null
                shiftModi = false
                relX = -1
                relY = -1
            }
        }
        return true
    }

    fun press(curX: Int, curY: Int) {
        if (getVal(sett, "debug", "") == "1") {
            val p = Paint()
            p.color = 0x0fff0000
            keybLayout!!.canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }

        currentKey = keybLayout?.getKey(curX, curY)
        if (currentKey == null) return
        vibrate("vibtouch")
        pressX = curX
        pressY = curY
        relX = -1
        relY = -1
        cursorMoved = false
        charPos = 0
        if (currentKey!!.repeat || currentKey!!.extChars!!.isNotEmpty()) {
            relX = curX
            relY = curY
        }
    }

    private fun drag(curX: Int, curY: Int) {
        if (currentKey?.getBool("mod") == true) return
        if (currentKey!!.getBool("clipboard")) {
            charPos = getExtPos(curX, curY)
            return
        }
        if (relX < 0) return // Not have alternative behavior
        if (currentKey!!.repeat) {
            if (!cursorMoved && (curX - horizontalTick > pressX || curX + horizontalTick < pressX || curY - verticalTick > pressY || curY + verticalTick < pressY)) {
                cursorMoved = true
            }
            while (true) {
                if (curX - horizontalTick > relX) {
                    relX += horizontalTick
                    onKey(if (currentKey!!.getInt("right") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("right"))
                    vibrate("vibtick")
                    continue
                }
                if (curX + horizontalTick < relX) {
                    relX -= horizontalTick
                    onKey(if (currentKey!!.getInt("left") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("left"))
                    vibrate("vibtick")
                    continue
                }
                if (curY - verticalTick > relY) {
                    relY += verticalTick
                    onKey(if (currentKey!!.getInt("bottom") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("bottom"))
                    vibrate("vibtick")
                    continue
                }
                if (curY + verticalTick < relY) {
                    relY -= verticalTick
                    onKey(if (currentKey!!.getInt("top") == 0) currentKey!!.codes!![0] else currentKey!!.getInt("top"))
                    vibrate("vibtick")
                    continue
                }
                break
            }
        } else if (currentKey!!.extChars!!.isNotEmpty()) {
            relX = curX
            relY = curY
            charPos = getExtPos(curX, curY)
        }
    }

    private fun release(curX: Int, curY: Int) {
        if (currentKey == null) return
        if (currentKey!!.getBool("mod")) {
            if ((mod and currentKey!!.getInt("modmeta")) > 0) {
                mod = mod and currentKey!!.getInt("modmeta").inv()
                releaseShiftable(currentKey!!.getInt("modkey"))
                keybView!!.repMod()
            } else {
                mod = mod or currentKey!!.getInt("modmeta")
                pressShiftable(currentKey!!.getInt("modkey"))
                keybView!!.repMod()
            }
            return
        }
        if (curY == 0 || cursorMoved) return
        if (currentKey!!.getBool("clipboard")) {
            if (charPos < 1) return
            if (shiftPressed()) {
                currentKey!!.clipboard[charPos - 1] = currentInputConnection.getSelectedText(0)
            } else {
                if (currentKey!!.clipboard[charPos - 1] == null) return
                onText(currentKey!!.clipboard[charPos - 1].toString())
            }
            return
        }

        if (currentKey!!.rand != null && currentKey!!.rand!!.isNotEmpty()) {
            return onText(currentKey!!.rand!![Random().nextInt(currentKey!!.rand!!.size)]!!)
        }
        if (currentKey!!.getStr("lang").isNotEmpty() && charPos == 0) {
            currentLayout = currentKey!!.getStr("lang")
            reload()
            return
        }
        if (currentKey!!.text != null && currentKey!!.text!!.length > 0) {
            onText(currentKey!!.text!!)
            if (currentKey!!.getInt("pos") < 0) for (i in 1..-currentKey!!.getInt("pos")) onKey(-21)
            else if (currentKey!!.getInt("pos") > 0) for (i in 1..currentKey!!.text!!.length-currentKey!!.getInt("pos")) onKey(-21)
            else if (currentKey!!.getStr("pos") == "0") for (i in 1..currentKey!!.text!!.length) onKey(-21)
            return
        }
        if (currentKey!!.repeat && !cursorMoved) return onKey(currentKey!!.codes!![0])
        if (relX < 0 || charPos == 0) return onKey(currentKey?.codes?.get(0) ?: 0)

        val extSz = currentKey!!.extChars!!.length
        if (extSz > 0 && extSz >= charPos) {
            val textIndex = currentKey!!.extChars!![charPos - 1]
            if (textIndex == ' ') return
            textEvent(textIndex.toString())
            return
        }
    }

    private fun getExtPos(x: Int, y: Int): Int {
        if (abs(pressX - x) < offset && abs(pressY - y) < offset) return 0
        val angle = Math.toDegrees(atan2((pressY - y).toDouble(), (pressX - x).toDouble()))
        return angPos[ceil(((if (angle < 0) 360.0 else 0.0) + angle + 22.5) / 45.0).toInt() - 1]
    }

    fun vibrate(s: String) {
        val i = if (currentKey!!.getInt(s) > 0) currentKey!!.getInt(s).toLong() else 0
        if (vibtime + i > System.currentTimeMillis()) return
        vibtime = System.currentTimeMillis()
        if (i < 10) return

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(i, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(i)
    }


    private fun textEvent(data: String) {
        onKey(getFromString(data)[0])
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in str.indices) out[j] = Character.getNumericValue(str[j])
        return out // FIXME: Is it fixes >1 length?
    }
}