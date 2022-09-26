package com.vladgba.keyb

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView

class Vkeyboard : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        val html = getString(R.string.main_text)
        val content = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        val description = findViewById<TextView>(R.id.main_description)
        description.movementMethod = LinkMovementMethod.getInstance()
        description.setText(content, TextView.BufferType.SPANNABLE)

        val manageKeyb = findViewById<Button>(R.id.main_manage_onscreen_keyboards)
        manageKeyb.setOnClickListener {
            startActivityForResult(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS), 0)
        }

        val inputMethod = findViewById<Button>(R.id.main_choose_input_method_btn)
        inputMethod.setOnClickListener {(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()}

        val grantPermissions = findViewById<Button>(R.id.main_grant_permission)
        grantPermissions.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    val getpermission = Intent()
                    getpermission.action = android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(getpermission)
                }
            } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)
            }
        }
/*
        val settings = findViewById<Button>(R.id.main_settings_btn)
        settings.setOnClickListener { startActivityForResult(Intent(this, Settings::class.java), 0) }
 */
    }
}