package com.vladgba.keyb

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button

class Vkeyboard : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.welcome)
        val manageKeyb = findViewById<Button>(R.id.main_manage_onscreen_keyboards)
        val inputMethod = findViewById<Button>(R.id.main_choose_input_method_btn)


        manageKeyb.setOnClickListener {
            startActivityForResult(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS), 0)
        }

        inputMethod.setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        val settings = findViewById<Button>(R.id.main_settings_btn)
        settings.setOnClickListener {
            startActivityForResult(Intent(this, LayoutSettings::class.java), 0)
        }

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val manageKeyb = findViewById<Button>(R.id.main_manage_onscreen_keyboards)
            val inputMethod = findViewById<Button>(R.id.main_choose_input_method_btn)
            val check_mark = getDrawable(android.R.drawable.checkbox_on_background)

            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            val inputMethodList = inputMethodManager.enabledInputMethodList

            var enabled = false
            for (inputMethodInfo in inputMethodList) {
                if (inputMethodInfo.packageName == this.packageName) {
                    enabled = true
                    break
                }
            }
            manageKeyb.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (enabled) check_mark else null,
                null,
                null,
                null
            )

            val mid = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )

            inputMethod.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (mid.contains(this.packageName)) check_mark else null,
                null,
                null,
                null
            )

        }
    }
}