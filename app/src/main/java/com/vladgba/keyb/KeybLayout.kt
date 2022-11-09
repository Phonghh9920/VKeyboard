package com.vladgba.keyb

import android.view.inputmethod.InputMethodManager

abstract class KeybLayout() : KeybConf() {
    abstract val kbc: KeybController
    var keybView: KeybView? = null
    val defLayout = "latin"
    var currentLayout = "latin"
    var defLayoutJson = ""
    var keybLayout: KeybModel? = null
    val loadedLayouts: MutableMap<String, KeybModel> = mutableMapOf()

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
            keybLayout = KeybModel(kbc, "vkeyb/" + lay, pt)
            keybLayout!!.lastdate = getLastModified(lay)
            if (keybLayout!!.loaded) loadedLayouts[lay] = keybLayout!!
        }
    }

    fun layoutFileChanged(s: String): Boolean {
        return getLastModified(s) == 0L || loadedLayouts.getValue(s).lastdate == 0L || (getLastModified(s) > loadedLayouts.getValue(s).lastdate)
    }

    fun setKeyb() {
        if (keybLayout?.loaded != true) {
            keybLayout = defLayo
            loadVars()
            keybView!!.reload()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            return
        }
    }
}