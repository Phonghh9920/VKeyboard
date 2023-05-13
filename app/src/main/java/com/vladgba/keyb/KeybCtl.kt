package com.vladgba.keyb

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.*
import android.text.InputType
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.KeyEvent.*
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.min


class KeybCtl(val ctx: Context, val wrapper: KeybWrapper?) {
    var isPortrait = true
    var isNight = false
    var metaState: Int = 0
    var hardMetaState: Int = 0
    var vibrationMs: Long = 0

    var currentLayout: String
    var lastTextLayout: String
    val loaded: MutableMap<String, KeybLayout> = mutableMapOf()
    var keybLayout: KeybLayout? = null
    var view: KeybView
    val layouts:List<String>
        get() = layoutList()

    val pointers = mutableMapOf<Int, Key>()
    val handler = Handler(Looper.getMainLooper())
    var lastPointerId = 0
    var recKey: Key? = null

    internal var keybType = KeybType.NORMAL

    var editInterface: KeybEditInterface? = null

    init {
        if (!PFile(ctx, SETTINGS_FILENAME).exists())
            ctx.startActivity(Intent(ctx, SetupLayouts::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        updateNightState()
        setOrientation()
        Settings.loadVars(ctx)
        InjectedEvent.DEV = Settings.str(SETTING_INPUT_DEVICE)
        setKeybTheme()
        view = KeybView(this)
        currentLayout = Settings.str(SETTING_DEF_LAYOUT)
        lastTextLayout = currentLayout
        refreshLayout()
        reloadLayout()
        metaState = 0
        if (wrapper == null) editInterface = KeybEditInterface(this)
    }

    fun getInputView() = view.apply { if (parent != null) (parent as ViewGroup).removeAllViews() }

    private fun setOrientation() {
        val orientation = ctx.resources.configuration.orientation
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun updateNightState(cfg: Configuration = ctx.resources.configuration) {
        isNight = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Log.d("night", isNight.toString())
    }

    fun onConfigurationChanged(cfg: Configuration) {
        if (changeOrientation(cfg)) reloadLayout()

        if (Settings.bool(THEME_SWITCH)) {
            val prevNightState = isNight
            updateNightState(cfg)
            setKeybTheme()
            if (prevNightState != isNight) view.repaintKeyb()
        }
    }

    fun setKeybTheme() {
        val themeName = Settings.str(if (isNight) THEME_NIGHT else THEME_DAY)
        val theme = Flexaml(PFile(ctx, themeName, THEME_EXT).read()).parse()
        Settings.params.also {
            for ((i, color) in theme.params.entries) it[i] = color
        }
    }

    /**
     * Checks if the orientation has changed and updates the value of 'isPortrait' accordingly.
     *
     * @param cfg Configuration object after orientation change
     * @return true if the orientation has changed, false otherwise
     */
    private fun changeOrientation(cfg: Configuration): Boolean {
        (cfg.orientation == Configuration.ORIENTATION_PORTRAIT).also {
            return if (it != isPortrait) {
                isPortrait = it
                true
            } else false
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (wrapper == null) return true
        if (keyCode < 0) return true
        try {
            if (Settings.bool(SETTING_DEBUG)) {
                log("Down:$keyCode;${event.device.name}/${event.deviceId};S${event.scanCode};M${event.metaState};'${event.unicodeChar}'")
            }
            if (inputKey(keyCode.toString(), ACTION_DOWN, true)) {
                view.invalidate()
                return true
            } else {

                // TODO: refactor to function
                when (keyCode) {
                    in KEYCODE_F1..KEYCODE_F12 -> {
                        // TODO: F1 - F12
                    }

                    KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT,
                    KEYCODE_META_LEFT, KEYCODE_META_RIGHT,
                    KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_RIGHT,
                    KEYCODE_ALT_LEFT, KEYCODE_ALT_RIGHT -> {
                        hardMetaState = event.metaState
                        metaState = (metaState or hardMetaState)
                        keyShifted(event.action, keyCode, metaState)
                        view.repMod()
                    }

                }
                view.invalidate()
            }
        } catch (e: Exception) {
            prStack(e)
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (wrapper == null) return true
        try {

            if (Settings.bool(SETTING_DEBUG)) {
                log("Up:$keyCode;${event.device.name}/${event.deviceId};S${event.scanCode};M${event.metaState};'${event.unicodeChar}'")
            }
            if (inputKey(keyCode.toString(), ACTION_UP, false)) {
                if (currentLayout != Settings.str(SETTING_DEF_LAYOUT)) reloadLayout()
                view.invalidate()
                return true
            } else {

                // TODO: refactor to function
                when (keyCode) {
                    in KEYCODE_F1..KEYCODE_F12 -> {
                        // TODO: F1 - F12
                    }

                    KEYCODE_CAPS_LOCK,
                    KEYCODE_NUM, KEYCODE_SCROLL_LOCK,
                    KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT,
                    KEYCODE_META_LEFT, KEYCODE_META_RIGHT,
                    KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_RIGHT,
                    KEYCODE_ALT_LEFT, KEYCODE_ALT_RIGHT -> {
                        metaState = (metaState and hardMetaState.inv())
                        hardMetaState = event.metaState
                        metaState = (metaState or hardMetaState)
                        keyShifted(event.action, keyCode, metaState)
                        view.repMod()
                    }

                }
                view.invalidate()
            }
        } catch (e: Exception) {
            prStack(e)
        }
        return false
    }

    fun inputKey(key: String, action: Int, pressing: Boolean): Boolean {
        if (keybLayout == null || !keybLayout!!.bool(SETTING_REDEFINE_HW_ACTION) || !keybLayout!!.has(key)) return false
        val keyData = keybLayout!![key]

        if (!keyData.bool(KEY_DO_IF_HIDDEN) && !view.isShown) return false

        if (injectedEvent(keyData, pressing)) return true

        if (keyData.has(KEY_TEXT) && pressing) {
            val txt = keyData.str(KEY_TEXT)
            if (txt.isNotEmpty()) {
                setText(txt)
                return true
            }
        }

        val newModifierState = keyData.num(KEY_MOD_META)
        metaState = if (pressing) (metaState or newModifierState) else metaState and newModifierState.inv()
        keyShifted(action, keyData.num(KEY_CODE))
        view.repMod()

        if (currentLayout == Settings.str(SETTING_DEF_LAYOUT) || !pressing || !keyData.has(KEY_SWITCH_LAYOUT)) return true

        currentLayout = if (keyData.bool(KEY_SWITCH_LAYOUT)) Settings.str(SETTING_DEF_LAYOUT) else keyData.str(KEY_SWITCH_LAYOUT)

        keybLayout = loaded[currentLayout]
        Settings.loadVars(ctx)
        view.reload()
        return true
    }

    private fun injectedEvent(kd: Flexaml.FxmlNode, pressing: Boolean): Boolean {
        if (!kd.has("inject")) return false
        if (kd.str(ACTION_SU).isNotBlank()) KeyAction(this).suExec(kd.str(ACTION_SU))

        if (kd.bool("inject")) {
            if (pressing) {
                InjectedEvent.press(kd.num("x"), kd.num("y"), 1024, 1, 4)
                if (kd.has("mx") || kd.has("my")) InjectedEvent.move(kd.num("mx", -1), kd.num("my", -1), 4)
                Thread.sleep(20L)
                InjectedEvent.release(0, 4)
            }
        }

        return true
    }

    fun keyShifted(keyAct: Int, key: Int, meta: Int = metaState) {
        val ic = wrapper?.currentInputConnection ?: return
        if (recKey?.recording == true) recKey!!.record.add(Key.Record(key, meta, keyAct))
        val time = System.currentTimeMillis()
        ic.sendKeyEvent(
            KeyEvent(
                time,
                time,
                keyAct,
                key,
                0,
                meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                FLAG_SOFT_KEYBOARD or FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    /**
     * Performs a "shifted" click for the specified key by sending both an ACTION_DOWN and an ACTION_UP event,
     * taking into account the key's current meta state or the specified meta state if provided.
     *
     * @param key the key to be clicked
     * @param meta the meta state to be used for the click (defaults to current meta state if not provided)
     */
    fun clickShifted(key: Int, meta: Int = metaState) {
        keyShifted(ACTION_DOWN, key, meta)
        keyShifted(ACTION_UP, key, meta)
    }

    fun onKey(i: Int) {
        if (Settings.bool(SETTING_DEBUG)) log("key: $i")
        if (wrapper == null || i == 0) return
        if (i in LATIN_KEYS) clickShifted(i - LATIN_OFFSET)
        else if (i < 0) clickShifted(-i) // keycode
        else onText(getShifted(i, shiftPressed()).toChar().toString())
    }

    fun setText(text: String, del: Int = 0) = wrapper?.currentInputConnection?.apply {
        beginBatchEdit()
        if (del > 0) deleteSurroundingText(del, 0)
        commitText(text, 1)
        endBatchEdit()
    }

    fun onText(chars: CharSequence) {
        if (wrapper == null) return
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

    fun onTouchEvent(me: MotionEvent) {
        val action = me.actionMasked
        val pointerIndex: Int = me.actionIndex
        val pid: Int = me.getPointerId(pointerIndex)
        val x = me.getX(pointerIndex).toInt()
        val y = me.getY(pointerIndex).toInt()

        //val currentKey = pointers[pid] ?: keybLayout?.getKey(x, y) ?: return

        try {
            when (action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    pointers[pid] = keybLayout?.getKey(x, y) ?: return
                    if (wrapper == null) {
                        editInterface?.onPress(pointers[pid]!!, x, y)
                    } else {
                        lastPointerId = pid
                        pointers[pid] = pointers[pid]!!.apply { press(x, y) }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (wrapper != null) {
                        for (i in 0 until me.pointerCount) {
                            val id = me.getPointerId(i)
                            pointers[id]?.drag(me.getX(i).toInt(), me.getY(i).toInt(), me, id)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (wrapper == null) {
                        editInterface?.onRelease(x, y)
                    } else {
                        if (pointers[pid] == null) return
                        handler.removeCallbacks(pointers[pid]!!.longPressRunnable)
                        pointers[pid]!!.release(x, y, pid)
                        pointers.remove(pid)
                    }
                }
            }
        } catch (e: Exception) {
            prStack(e)
        }
    }

    fun prStack(e: Throwable) {
        log(StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString())
    }

    fun log(sw: String) {
        Log.e("Log", sw)
        Toast.makeText(ctx, sw.subSequence(0, min(150, sw.length)), Toast.LENGTH_LONG).show()
    }

    fun vibrate(curkey: Key, s: String) {
        val i = if (curkey.num(s) > 0) curkey.num(s).toLong() else 0L
        if (vibrationMs + i > System.currentTimeMillis()) return
        vibrationMs = System.currentTimeMillis()
        if (i < 10) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(i, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(i)
    }

    fun getFromString(str: CharSequence): IntArray {
        if (str.length < 2) return intArrayOf(str[0].code)
        val out = IntArray(str.length)
        for (j in str.indices) out[j] = Character.getNumericValue(str[j])
        return out
    }

    fun ctrlPressed() = metaState and META_CTRL_MASK != 0

    fun shiftPressed() = metaState and META_SHIFT_MASK != 0

    fun getShifted(code: Int, sh: Boolean) =
        if (Character.isLetter(code) && sh) code.toChar().uppercaseChar().code else code

    private fun refreshLayout() {
        val layNm = Settings.str(SETTING_DEF_LAYOUT)
        if (!loaded.containsKey(layNm) || layoutFileChanged(layNm)) {
            loaded[layNm] = loadLayout(layNm)
        }
    }

    fun loadLayout(name: String): KeybLayout {
        val file = PFile(ctx, name)
        val layout = KeybLayout(this, Flexaml(file.read()).parse())
        layout.lastdate = file.lastModified()
        return layout
    }

    fun reloadLayout() {
        Settings.loadVars(ctx)
        setKeybTheme()
        pickLayout(currentLayout)
        if (!keybLayout!!.loaded) {
            if (currentLayout.isBlank()) {
                log("Current layout name is blank")
            } else {
                ctx.startActivity(Intent(ctx, KeybRawEditor::class.java).apply {
                    putExtra("name", currentLayout)
                })
            }
        }
        view.reload()
    }

    fun pickLayout(layNm: String) {
        if (loaded.containsKey(layNm) && !layoutFileChanged(layNm)) {
            keybLayout = loaded.getValue(layNm)
            keybLayout!!.parent = Settings
        } else {
            keybLayout = loadLayout(layNm)
            keybLayout!!.parent = Settings
            if (keybLayout!!.loaded) loaded[layNm] = keybLayout!!
        }
    }

    fun layoutFileChanged(s: String) =
        loaded[s]?.lastdate == 0L || PFile(ctx, s).lastModified().let { it == 0L || it != loaded[s]!!.lastdate }

    fun onLowMemory() {
        //loaded.clear() // TODO: clear = app crash
        view.clearBuffers()
    }

    fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            onLowMemory()
        } else if (level == ComponentCallbacks2.TRIM_MEMORY_MODERATE || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            view.clearBuffers()
        }
    }

    fun setKeybType(inputType: Int) {
        val inputTypeClass = inputType and InputType.TYPE_MASK_CLASS
        val inputTypeVariation = inputType and InputType.TYPE_MASK_VARIATION

        keybType = when (inputTypeClass) {
            InputType.TYPE_CLASS_NUMBER -> KeybType.NUMBER
            InputType.TYPE_CLASS_PHONE -> KeybType.PHONE
            InputType.TYPE_CLASS_TEXT -> when (inputTypeVariation) {
                InputType.TYPE_TEXT_VARIATION_URI -> KeybType.URI
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> KeybType.EMAIL
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> KeybType.NORMAL
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> KeybType.EMAIL

                else -> KeybType.NORMAL
            }

            InputType.TYPE_CLASS_DATETIME -> when (inputTypeVariation) {
                InputType.TYPE_DATETIME_VARIATION_NORMAL -> KeybType.DATETIME
                InputType.TYPE_DATETIME_VARIATION_DATE -> KeybType.DATE
                InputType.TYPE_DATETIME_VARIATION_TIME -> KeybType.TIME
                else -> KeybType.DATETIME
            }

            else -> KeybType.NORMAL
        }
    }

    fun layoutList(): List<String> {
        val files = mutableListOf<String>()
        val fileList = ctx.filesDir.listFiles()
        for (file in fileList!!) {
            var name = file.name
            val ext = name.lastIndexOf(".$LAYOUT_EXT")
            if (ext < 0) continue
            name = name.substring(0, ext)
            if (listOf(SETTINGS_FILENAME, NUM_FILENAME, EMOJI_FILENAME).contains(name)) continue
            files.add(name)
        }
        return files
    }

    fun prevLayout() {
        val currPos = layouts.indexOf(lastTextLayout)
        if (currPos < 0 || layouts.size < 2) return
        var pos = currPos - 1

        while (true) {
            if (pos < 0) pos += layouts.size - 1
            if (pos == currPos) return
            currentLayout = layouts[pos]

            if (currentLayout.indexOf("$") == 0) pos++
            else break
        }
        lastTextLayout = currentLayout
        reloadLayout()
    }

    fun nextLayout() {
        val currPos = layouts.indexOf(lastTextLayout)
        if (currPos < 0 || layouts.size < 2) return
        var pos = currPos + 1
        while (true) {
            if (pos >= layouts.size) pos -= layouts.size
            if (pos == currPos) return
            currentLayout = layouts[pos]

            if (currentLayout.indexOf("$") == 0) pos++
            else break
        }
        lastTextLayout = currentLayout
        reloadLayout()
    }

    fun numLayout() {
        currentLayout = NUM_FILENAME
        reloadLayout()
    }

    fun textLayout() {
        currentLayout = lastTextLayout
        reloadLayout()
    }

    fun emojiLayout() {
        currentLayout = EMOJI_FILENAME
        reloadLayout()
    }
}
