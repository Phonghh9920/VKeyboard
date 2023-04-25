package com.vladgba.keyb

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.*
import android.util.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.io.*
import kotlin.math.min


class KeybCtl(val ctx: Context, val wrapper: KeybWrapper?) {
    var isPortrait = true
    var night = false
    var modifierState: Int = 0
    var vibtime: Long = 0

    val defLayout = "latin"
    var currentLayout = "latin"
    var defLayoutJson = ""
    var keybLayout: KeybModel? = null

    lateinit var dm: DisplayMetrics
    val points = arrayOfNulls<Key>(10)
    val handler = Handler(Looper.getMainLooper())
    var lastPointerId = 0
    var recKey: Key? = null
    var picture: KeybView? = null
    var defLayo: KeybModel? = null
    val loaded: MutableMap<String, KeybModel> = mutableMapOf()
    private var keyboardReceiver: KeybReceiver? = null

    override fun onCreate() {
        super.onCreate()

        updateNightState(this.resources.configuration)
        setOrientation()
        picture = KeybView(this)
        initDefLayout()
        refreshLayout()
        reloadLayout()
        loadVars()
        modifierState = 0
        keyboardReceiver = KeybReceiver()
        val filter = IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)
        registerReceiver(keyboardReceiver, filter)
    }

    override fun onCreateInputView(): View {
        if (picture?.parent != null) (picture!!.parent as ViewGroup).removeAllViews()
        return picture!!
    }

    private fun setOrientation() {
        dm = this.resources.displayMetrics
        val orientation = this.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun updateNightState(cfg: Configuration) {
        val tmpNight = night
        when (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> night = true
            Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_UNDEFINED -> night = false
        }
        if (tmpNight != night) picture?.repaintKeyb()
    }

    fun showMethodPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onConfigurationChanged(cfg: Configuration) {
        super.onConfigurationChanged(cfg)
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && isPortrait) updateOrientation(false)
        else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !isPortrait) updateOrientation(true)
        updateNightState(cfg)
    }

    private fun updateOrientation(b: Boolean) {
        isPortrait = b
        reloadLayout()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        picture?.invalidate()
        if (keyCode < 0) return true
        try {
            picture?.invalidate()
            if (picture?.isShown == true && inputKey(keyCode.toString(), KeyEvent.ACTION_DOWN, true)) return true
        } catch (e: Exception) {
            prStack(e)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        try {
            if (picture?.isShown == true && inputKey(keyCode.toString(), KeyEvent.ACTION_UP, false)) {
                picture?.invalidate()
                if (currentLayout != defLayout) reloadLayout()
                return true
            }
        } catch (e: Exception) {
            prStack(e)
        }
        return super.onKeyUp(keyCode, event)
    }

    fun inputKey(key: String, action: Int, pressModifier: Boolean): Boolean {
        if (Settings.str("redefineHardwareActions") != "1" || !Settings.has(key)) return false
        val keyData = Settings[key]
        val newModifierState = keyData.num("mod")
        modifierState =
            if (pressModifier) (modifierState or newModifierState) else modifierState and newModifierState.inv()
        keyShiftable(action, keyData.num("key"))
        picture?.repMod()

        if (currentLayout == defLayout || !pressModifier || !keyData.has("switchKeyb")) return true

        if (keyData.str("switchKeyb") == "1") {
            keybLayout = loaded[defLayout + (if (isPortrait) "-portrait" else "-landscape")]
            picture?.reload()
            loadVars()
            setKeyb()
        }
        return true
    }

    fun keyShiftable(keyAct: Int, key: Int, custMod: Int = modifierState) {
        val ic = currentInputConnection
        if (recKey != null && recKey!!.recording) recKey!!.record.add(Key.Record(key, custMod, keyAct))
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(
            KeyEvent(
                time,
                time,
                keyAct,
                key,
                0,
                custMod,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    fun clickShiftable(key: Int) {
        keyShiftable(KeyEvent.ACTION_DOWN, key)
        keyShiftable(KeyEvent.ACTION_UP, key)
    }

    fun onKey(i: Int) {
        if (i in LATIN_KEYS) clickShiftable(i - LATIN_OFFSET)// find a-z for combination support
        else if (i < 0) clickShiftable(-i) // keycode
        else onText(getShifted(i, shiftPressed()).toChar().toString())
    }

    fun setText(text: String, del: Int = 0) {
        currentInputConnection.apply {
            beginBatchEdit()
            if (del > 0) deleteSurroundingText(del, 0)
            commitText(text, 1)
            endBatchEdit()
        }
    }

    fun onText(chars: CharSequence) {
        if (recKey != null && recKey!!.recording) {
            if (recKey!!.record.size > 0) {
                val rc = recKey!!.record[recKey!!.record.size - 1]
                if (rc.keyText != "") rc.keyText = rc.keyText + chars.toString()
                else recKey!!.record.add(Key.Record(chars.toString()))
            } else {
                recKey!!.record.add(Key.Record(chars.toString()))
            }
        }
        setText(chars.toString())
    }

    override fun onUpdateSelection(oss: Int, ose: Int, nss: Int, nse: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(oss, ose, nss, nse, cs, ce)
    }

    fun onTouchEvent(me: MotionEvent): Boolean {
        val action = me.actionMasked
        val pointerIndex: Int = me.actionIndex
        val pid: Int = me.getPointerId(pointerIndex)
        val x = me.getX(pointerIndex).toInt()
        val y = me.getY(pointerIndex).toInt()

        val currentKey = if (points[pid] == null) keybLayout?.getKey(x, y) ?: return true else points[pid]

        try {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    lastPointerId = pid
                    points[pid] = currentKey
                    currentKey!!.press(x, y)
                }

                MotionEvent.ACTION_MOVE -> {
                    currentKey!!.drag(x, y, me, pid)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    points[pid] = null
                    handler.removeCallbacks(currentKey!!.longPressRunnable)
                    currentKey.release(x, y, pid)
                }
            }
        } catch (e: Exception) {
            prStack(e)
        }
        return true
    }

    fun getLastModified(path: String): Long {
        return File(Environment.getExternalStorageDirectory(), "vkeyb/$path.json").lastModified()
    }


    fun loadVars() {
        if (getLastModified("settings") == Settings.lastModified) return
        Settings.lastModified = getLastModified("settings")
        try {
            Settings += LayoutParse(loadFile("settings"), this).parse()
        } catch (e: Exception) {
            prStack(e)
            Settings += LayoutParse(resources.openRawResource(R.raw.settings).bufferedReader().use { it.readText() }, this).parse()
        }
        Settings.setDefaults()
    }

    fun prStack(e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        log(sw.toString())
    }
    fun log(sw: String) {
        Log.e("Log", sw)
        Toast.makeText(this, sw.subSequence(0, min(150, sw.length)), Toast.LENGTH_LONG).show()
    }

    fun loadFile(name: String): String {
        val sdcard = Environment.getExternalStorageDirectory()
        val text = StringBuilder()
        val br = BufferedReader(FileReader(File(sdcard, "vkeyb/$name.json")))
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
        val i = if (curkey.options.num(s) > 0) curkey.options.num(s).toLong() else 0L
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(
            VibrationEffect.createOneShot(
                i,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
        else vibrator.vibrate(i)
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in str.indices) out[j] = Character.getNumericValue(str[j])
        return out
    }

    fun ctrlPressed() = modifierState and LEFT_CTRL != 0

    fun shiftPressed() = modifierState and LEFT_SHIFT != 0

    fun getShifted(code: Int, sh: Boolean): Int {
        return if (Character.isLetter(code) && sh) code.toChar().uppercaseChar().code else code
    }

    private fun refreshLayout() {
        val layNm = defLayout + if (isPortrait) "-portrait" else "-landscape"
        //val layNm = defLayout + "-portrait"
        Log.d("layNM", layNm)
        if (!loaded.containsKey(layNm) || layoutFileChanged(layNm)) {
            loaded[layNm] = KeybModel(this, layNm, isPortrait)
        }
    }

    private fun initDefLayout() {
        defLayoutJson = resources.openRawResource(R.raw.latin).bufferedReader().use { it.readText() }
        defLayo = KeybModel(this, defLayoutJson, true, true)
    }

    fun reloadLayout() {
        pickLayout(currentLayout, isPortrait)
        if (!keybLayout!!.loaded && !isPortrait) pickLayout(currentLayout, true)

        loadVars()
        picture?.reload()
        setKeyb()
    }

    fun pickLayout(layNm: String?, pt: Boolean) {
        val lay = layNm + if (pt) "-portrait" else "-landscape"

        if (loaded.containsKey(lay) && !layoutFileChanged(lay)) {
            keybLayout = loaded.getValue(lay)
        } else {
            keybLayout = KeybModel(this, lay, pt)
            keybLayout!!.lastdate = getLastModified(lay)
            if (keybLayout!!.loaded) loaded[lay] = keybLayout!!
        }
    }

    fun layoutFileChanged(s: String): Boolean {
        return getLastModified(s) == 0L || loaded[s]!!.lastdate == 0L || (getLastModified(s) != loaded[s]!!.lastdate)
    }

    fun setKeyb() {
        if (keybLayout?.loaded == true) return
        keybLayout = defLayo
        loadVars()
        picture?.reload()
        showMethodPicker()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        //loaded.clear()
        picture?.clearBuffers()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            //loaded.clear() // TODO: clear = app crash
            picture?.clearBuffers()
        } else if (level == ComponentCallbacks2.TRIM_MEMORY_MODERATE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            picture?.clearBuffers()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (keyboardReceiver != null) unregisterReceiver(keyboardReceiver)
    }
}