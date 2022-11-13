package com.vladgba.keyb

import android.Manifest
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.inputmethod.InputMethodManager
import android.widget.*

class Vkeyboard : Activity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        val html = getString(R.string.main_text)
        val content = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(html)
        }
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
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri: Uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), 1)
                }
            }
        }
/*
        val settings = findViewById<Button>(R.id.main_settings_btn)
        settings.setOnClickListener { startActivityForResult(Intent(this, Settings::class.java), 0) }
 */
    }
}