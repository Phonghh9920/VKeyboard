package com.vladgba.keyb

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button


class Vkeyboard : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.welcome)

        if (!PFile(this, SETTINGS_FILENAME).exists())
            startActivity(Intent(this, SetupLayouts::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

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
            startActivityForResult(Intent(this, LayoutsManage::class.java), 0)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.welcome_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {

                val dialog = Dialog(this)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(R.layout.about_app)
                dialog.show()
                true
            }

            R.id.action_prebuilt_add_layouts -> {
                startActivity(Intent(this, SetupLayouts::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val manageKeyb = findViewById<Button>(R.id.main_manage_onscreen_keyboards)
            val inputMethod = findViewById<Button>(R.id.main_choose_input_method_btn)
            val checkMark = getDrawable(android.R.drawable.checkbox_on_background)

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
                if (enabled) checkMark else null, null, null, null
            )

            val mid = android.provider.Settings.Secure.getString(
                contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )

            inputMethod.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (mid.contains(this.packageName)) checkMark else null, null, null, null
            )

        }
    }

    fun openMail(v: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:vladgba@gmail.com")))
    }

    fun openWiki(v: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vladgba/VKeyboard/wiki")))
    }

    fun openGithub(v: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vladgba/VKeyboard")))
    }

    fun openPrivacyPolicy(v: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://zcxv.icu/vkeyb/privacy.html")))
    }

    fun openTermsOfUse(v: View) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://zcxv.icu/vkeyb/terms.html")))
    }
}