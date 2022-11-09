package com.vladgba.keyb

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import java.io.*

open class KeybConf : InputMethodService() {
    var mod: Int = 0

    var volumeUpPress = false
    var volumeDownPress = false
    var primaryFont = 0f
    var secondaryFont = 0f
    var horizontalTick = 0
    var verticalTick = 0
    var offset = 0 // extChars
    var longPressTime = 0L
    var vibtime: Long = 0
    var sett = emptyMap<String, Any>()
    var dm: DisplayMetrics? = null
    var isPortrait = true
    var defLayo: KeybModel? = null
    var night = false

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
        horizontalTick = getVal(sett, "horizontalSense", "30").toInt()
        verticalTick = getVal(sett, "verticalSense", "50").toInt()
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
