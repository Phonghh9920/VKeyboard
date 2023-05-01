package com.vladgba.keyb

import android.content.Context


object Settings : Flexaml.FxmlNode(Flexaml("""
{
    "$SETTING_DEBUG":"0",
    "$KEY_TEXT_SIZE_PRIMARY":"2.5",
    "$KEY_TEXT_SIZE_SECONDARY":"5",
    "$THEME_DAY":"baseLight",
    "$THEME_NIGHT":"baseDark",
    
    "$COLOR_TEXT_PRIMARY":"ff000000",
    "$COLOR_KEY_SECONDARY":"48000000",
    "$COLOR_KEYBOARD_BACKGROUND":"ffeeeeee",
    "$COLOR_KEY_BORDER":"33000000",
    "$COLOR_KEY_BACKGROUND":"ffffffff",
    "$COLOR_KEY_PREVIEW_SELECTED":"22000000",
    "$COLOR_TEXT_PREVIEW":"ff222222",
    "$COLOR_KEY_SHADOW":"55000000",
    "$COLOR_KEY_MOD_BACKGROUND":"ff0099ff"
    
    "$KEY_BORDER_RADIUS":10,
    "$KEY_PADDING":10,
    "$SENSE_HARD_PRESS":400,
    "$SENSE_ADDITIONAL_CHARS":70,
    "$SENSE_HORIZONTAL_TICK":70,
    "$SENSE_VERTICAL_TICK":60,
    "$SENSE_HOLD_PRESS":200,
    "$KEY_VISIBLE":1
    "$SETTING_INPUT_DEVICE":"/dev/input/event4"
}
""").parse()) {
    var lastModified = 0L
    fun loadVars(ctx: Context) {
        val setFile = PFile(ctx, SETTINGS_FILENAME)
        if (setFile.lastModified() == lastModified) return
        lastModified = setFile.lastModified()
        this.append(Flexaml(setFile.read()).parse())
    }

    fun save(ctx: Context) {
        PFile(ctx, SETTINGS_FILENAME).write(toString())
    }
}