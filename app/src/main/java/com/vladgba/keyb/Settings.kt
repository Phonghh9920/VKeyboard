package com.vladgba.keyb


object Settings : Flexaml.FxmlNode(Flexaml("""
{
   "$SETTING_DEBUG":"0",
   "$KEY_TEXT_SIZE_PRIMARY":"2.5",
   "$KEY_TEXT_SIZE_SECONDARY":"5",
   "$COLOR_DAY":"baseLight",
   "$COLOR_NIGHT":"baseDark",
   "baseLight":{
      "$COLOR_TEXT_PRIMARY":"ff000000",
      "$COLOR_KEY_SECONDARY":"48000000",
      "$COLOR_KEYBOARD_BACKGROUND":"ffeeeeee",
      "$COLOR_KEY_BORDER":"33000000",
      "$COLOR_KEY_BACKGROUND":"ffffffff",
      "$COLOR_KEY_PREVIEW_SELECTED":"22000000",
      "$COLOR_TEXT_PREVIEW":"ff222222",
      "$COLOR_KEY_SHADOW":"55000000",
      "$COLOR_KEY_MOD_BACKGROUND":"ff0099ff"
   },
   "baseDark":{
      "$COLOR_TEXT_PRIMARY":"ffffffff",
      "$COLOR_KEY_SECONDARY":"59ffffff",
      "$COLOR_KEYBOARD_BACKGROUND":"ff1a1a1a",
      "$COLOR_KEY_BORDER":"ff4d4d4d",
      "$COLOR_KEY_BACKGROUND":"ff333333",
      "$COLOR_KEY_PREVIEW_SELECTED":"ff000000",
      "$COLOR_TEXT_PREVIEW":"ffffffff",
      "$COLOR_KEY_SHADOW":"66000000",
      "$COLOR_KEY_MOD_BACKGROUND":"440099ff"
   },
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
}