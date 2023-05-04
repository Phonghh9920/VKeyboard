package com.vladgba.keyb

internal val LATIN_KEYS = 97..122
internal const val LATIN_OFFSET = 68
internal const val MULTIBYTE_UTF = 56320

internal const val SETTINGS_FILENAME = "settings"
internal const val NUM_FILENAME = "123"
internal const val NUM2_FILENAME = "$123e"
internal const val EMOJI_FILENAME = "emoji"

internal const val LAYOUT_EXT = "txt"
internal const val BLANK_LAYOUT = "blank"
internal const val THEME_EXT = "ini"
internal const val BLANK_THEME = "blank"
internal const val SETTING_DEBUG = "debug"
internal const val SETTING_INPUT_DEVICE = "inputDevice"

// Hardware Key
internal const val SETTING_REDEFINE_HW_ACTION = "redefineHardwareActions"
internal const val KEY_DO_IF_HIDDEN = "doIfHidden"

// Settings
internal const val SETTING_DEF_LAYOUT = "defaultLayout"

// Key
internal const val KEY_KEY = "key"
internal const val KEY_CODE = "code"
internal const val KEY_HOLD = "hold"
internal const val KEY_HARD_PRESS = "hard"
internal const val KEY_TOP_ACTION = "top"
internal const val KEY_BOTTOM_ACTION = "bottom"
internal const val KEY_LEFT_ACTION = "left"
internal const val KEY_RIGHT_ACTION = "right"
internal const val KEY_TEXT = "text"
internal const val KEY_TEXT_CURSOR_OFFSET = "textCurOffset"
internal const val KEY_LAYOUT = "layout"

// Modifier
internal const val KEY_MOD_META = "meta"

//Mode
internal const val KEY_MODE = "mode"
internal const val KEY_MODE_POPUP = "popup"
internal const val KEY_MODE_JOY = "joy"
internal const val KEY_MODE_META = "meta"
internal const val KEY_MODE_RANDOM = "random"

// Action
internal const val KEY_ACTION = "action"
internal const val KEY_RANDOM = "random"
internal const val KEY_RECORD = "record"
internal const val KEY_CLIPBOARD = "clipboard"
internal const val KEY_SWITCH_LAYOUT = "switchKeyb"
internal const val KEY_ACTION_ON_CTRL = "ctrl"
internal const val KEY_ACTION_ON_ALT = "alt"
internal const val KEY_ACTION_ON_SHIFT = "shift"
internal const val ACTION_APP = "app"
internal const val ACTION_SU = "sudo"
internal const val ACTION_UPPER_ALL = "upperAll"
internal const val ACTION_LOWER_ALL = "lowerAll"

// Sound
internal const val KEY_SOUND_TICK = "soundTick"
internal const val KEY_SOUND_PRESS = "soundPress"
internal const val KEY_SOUND_RELEASE = "soundRelease"
internal const val KEY_SOUND_ADDIT = "soundAdditional"

internal const val KEY_VIBRATE_TICK = "vibTick"
internal const val KEY_VIBRATE_PRESS = "vibPress"
internal const val KEY_VIBRATE_RELEASE = "vibRelease"
internal const val KEY_VIBRATE_ADDITIONAL = "soundAdditional"

// Theme
internal const val THEME_SWITCH = "themeSwitch"
internal const val THEME_DAY = "dayTheme"
internal const val THEME_NIGHT = "nightTheme"
internal const val COLOR_KEYBOARD_BACKGROUND = "keyboardBackgroundColor"

// Style
internal const val KEY_VISIBLE = "visible"
internal const val KEY_DO_NOT_SHOW = "doNotShow"
internal const val ROW_HEIGHT = "height"
internal const val KEY_WIDTH = "width"
internal const val KEY_TEXT_SIZE_PRIMARY = "sizePrimary"
internal const val KEY_TEXT_SIZE_SECONDARY = "sizeSecondary"
internal const val KEY_PADDING = "padding"
internal const val KEY_HIDE_BORDERS = "borderHide"
internal const val KEY_BORDER_RADIUS = "radius"
internal const val KEY_SHADOW = "shadow"

// Color
internal const val COLOR_TEXT_PRIMARY = "primaryTextColor"
internal const val COLOR_TEXT_PREVIEW = "previewTextColor"
internal const val COLOR_KEY_BORDER = "keyBorderColor"
internal const val COLOR_KEY_STATIC_BG = "bg"
internal const val COLOR_KEY_BACKGROUND = "keyBackgroundColor"
internal const val COLOR_KEY_MOD_BACKGROUND = "modBackgroundColor"
internal const val COLOR_KEY_SHADOW = "shadowColor"
internal const val COLOR_KEY_PREVIEW_SELECTED = "previewSelectedColor"
internal const val COLOR_KEY_SECONDARY = "secondaryTextColor"
internal val COLORS = listOf(
    COLOR_TEXT_PRIMARY, COLOR_TEXT_PREVIEW, COLOR_KEY_SECONDARY,
    COLOR_KEY_BORDER, COLOR_KEYBOARD_BACKGROUND, COLOR_KEY_BACKGROUND,
    COLOR_KEY_MOD_BACKGROUND, COLOR_KEY_SHADOW, COLOR_KEY_PREVIEW_SELECTED
)

// Sensitivity
internal const val SENSE_HARD_PRESS = "hardSense"
internal const val SENSE_HOLD_PRESS = "longPressTime"
internal const val SENSE_ADDITIONAL_CHARS = "extSense"
internal const val SENSE_HORIZONTAL_TICK = "horTick"
internal const val SENSE_VERTICAL_TICK = "verTick"

// Layout Switch
internal const val LAYOUT_PREV = "@PREV"
internal const val LAYOUT_NEXT = "@NEXT"
internal const val LAYOUT_NUM = "@NUM"
internal const val LAYOUT_TEXT = "@TEXT"
internal const val LAYOUT_EMOJI = "@EMOJI"

internal enum class KeybType {
    NORMAL, NUMBER, PHONE, URI, EMAIL, DATETIME, DATE, TIME
}