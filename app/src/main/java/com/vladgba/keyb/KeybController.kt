package com.vladgba.keyb

import java.io.*
import java.util.*
import android.os.*
import kotlin.math.*
import android.util.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.content.res.Configuration
import android.view.inputmethod.EditorInfo
import android.app.UiModeManager
import android.content.Context
import android.graphics.Paint

class KeybController : InputMethodService() {
    val DEBUG = true
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
    private var keybLayout: KeybModel? = null
    private var isPortrait = true
    var night = true
    val defLayout = "latin"
    var currentLayout = "latin"
    private val loadedLayouts: MutableMap<String, KeybModel> = mutableMapOf()
    var erro = ""
    var mod: Int = 0
    var sett = emptyMap<String, Any>()

    fun reload() {
        if (loadedLayouts.containsKey(currentLayout)) {
            keybLayout = loadedLayouts.getValue(currentLayout)
        } else {
            keybLayout = KeybModel(this, "vkeyb/" + currentLayout + if (isPortrait) "-portrait" else "-landscape", true)
            loadedLayouts[currentLayout] = keybLayout!!
        }
        loadVars()
        setKeyb()
    }
    fun getVal(j: Map<String, Any>, s:String, d:String): String {
        if (!j.containsKey(s)) return d
        val r = j.getValue(s) as String
        if (r.length > 0) return r
        else return d
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
        } catch (e: IOException) {
            Log.d("Keyb", "Error")
            Log.d("Keyb", e.message!!)
            return e.message!!
        }
    }
    fun loadVars() {
        val json = JsonParse.map(loadKeybLayout("vkeyb/settings"))
        sett = json
        primaryFont = getVal(json, "sizePrimary", "2").toFloat()
        secondaryFont = getVal(json, "sizeSecondary", "4.5").toFloat()
        horizontalTick = getVal(json, "horizontalSense", "30").toInt()
        verticalTick = getVal(json, "verticalSense", "50").toInt()
        offset = getVal(json, "extendedSense", "70").toInt()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (layoutFileChanged()) {
            reload()
        }
    }

    private fun layoutFileChanged(): Boolean {
        return true
    }

    override fun onCreateInputView(): View {
        val isNightMode = false
        val uiManager = (getSystemService(UI_MODE_SERVICE) as UiModeManager)
        uiManager.nightMode = if (isNightMode) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        when (this.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> night = true
            Configuration.UI_MODE_NIGHT_NO -> night = false
            Configuration.UI_MODE_NIGHT_UNDEFINED -> night = false
        }
        dm = this.resources.displayMetrics
        keybView = layoutInflater.inflate(R.layout.vkeybview, null, false) as KeybView
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        reload()
        keybView!!.keybCtl = this
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
        setKeyb()
    }

    private fun setKeyb() {
        if (!keybLayout!!.loaded) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            return
        }
        keybView?.keyboard = keybLayout
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
                if (currentLayout == defLayout) return true
                if (((sett.getValue("volumeUp") as Map<String, Any>).getValue("switchKeyb") as String) == "1") {
                    keybLayout = KeybModel(this, "vkeyb/" + defLayout + if (isPortrait) "-portrait" else "-landscape", true)
                    loadVars()
                    setKeyb()
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> if (keybView!!.isShown) {
                if (volumeDownPress) return true
                volumeDownPress = true

                inputKey("volumeDown", KeyEvent.ACTION_DOWN, true)
                if (currentLayout == defLayout) return true
                if (((sett.getValue("volumeDown") as Map<String, Any>).getValue("switchKeyb") as String) == "1") {
                    keybLayout = KeybModel(this, "vkeyb/" + defLayout + if (isPortrait) "-portrait" else "-landscape", true)
                    loadVars()
                    setKeyb()
                }
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

    fun inputKey(key: String, pr: Int, set: Boolean) {
        val newmod = ((sett.getValue(key) as Map<String, Any>).getValue("mod") as String).toInt()
        mod = if (set) (mod or newmod) else mod xor newmod
        val ic = currentInputConnection
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(
            KeyEvent(
                time, time,
                pr,
                ((sett.getValue(key) as Map<String, Any>).getValue("key") as String).toInt(),
                0,
                mod
            )
        )
    }
    private fun keyShiftable(keyAct: Int, key: Int) {
        val ic = currentInputConnection
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(
            KeyEvent(
                time, time,
                keyAct,
                key,
                0,
                mod
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
        if (DEBUG) {
            val p = Paint()
            p.color = 0x22ff0000
            keybView!!.keyb!!.canv?.drawCircle(curX.toFloat(), curY.toFloat(), 10f, p)
        }

        currentKey = keybView!!.keyb?.getKey(curX, curY)
        Log.d("keyIndex", currentKey.toString())
        if (currentKey == null) return

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
        if (currentKey!!.getBool("mod")) return
        if (currentKey!!.getBool("clipboard")) {
            charPos = getExtPos(curX, curY)
            return
        }
        if (relX < 0) return  // Not have alternative behavior
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
        Log.d("keyIndexRelease", currentKey.toString())
        if (currentKey == null) return
        if (currentKey!!.getBool("mod")) {
            if (currentKey!!.hold) {
                releaseShiftable(currentKey!!.getInt("modcode"))
                mod = mod xor currentKey!!.getInt("modi")
                currentKey!!.hold = false
            } else {
                pressShiftable(currentKey!!.getInt("modcode"))
                mod = mod or currentKey!!.getInt("modi")
                currentKey!!.hold = true
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
        if (currentKey!!.lang != null && charPos == 0) {
            currentLayout = currentKey!!.lang.toString()
            reload()
            return
        }
        if (currentKey!!.text!!.isNotEmpty()) {
            onText(currentKey!!.text!!)
            if (currentKey!!.getInt("pos") > 0) for (i in 1..currentKey!!.getInt("pos")) onKey(-21)
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