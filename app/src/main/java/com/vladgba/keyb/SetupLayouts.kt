package com.vladgba.keyb

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView


class SetupLayouts : Activity() {
    var inflater: LayoutInflater? = null
    var parentLayout: LinearLayout? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_prebuilt_picker)

        title = getString(R.string.prebuilt_list)
        inflater = LayoutInflater.from(this)
        parentLayout = findViewById(R.id.picker_list)

        val settingsFile = PFile(this, SETTINGS_FILENAME)
        if (!settingsFile.exists()) {
            settingsFile.write(Settings.parent!!.toString())
            PFile(this, NUM_FILENAME).write(
                assets.open("$NUM_FILENAME.$LAYOUT_EXT").bufferedReader().use { it.readText() })
            PFile(this, NUM2_FILENAME).write(
                assets.open("$NUM2_FILENAME.$LAYOUT_EXT").bufferedReader().use { it.readText() })

            PFile(this, "baseLight", THEME_EXT).write(
                assets.open("baseLight.$THEME_EXT").bufferedReader().use { it.readText() })
            PFile(this, "baseDark", THEME_EXT).write(
                assets.open("baseDark.$THEME_EXT").bufferedReader().use { it.readText() })
        }

        layoutsList()
    }

    private fun layoutsList() {
        if (inflater == null || parentLayout == null) return
        val list: Array<String>? = assets.list("")
        parentLayout!!.removeAllViews()

        for (file in list!!) {
            var name = file
            val ext = name.lastIndexOf(".$LAYOUT_EXT")
            if (ext < 0) continue
            name = name.substring(0, ext)
            if (PFile(this, name).exists() || name == BLANK_LAYOUT) continue

            val fileView = inflater!!.inflate(R.layout.prebuilt_item, parentLayout, false)

            val textView = fileView.findViewById<TextView>(R.id.layout_name)

            fileView.findViewById<ImageButton>(R.id.layout_add_prebuilt).setOnClickListener {
                PFile(this, name).write(
                    assets.open("$name.$LAYOUT_EXT").bufferedReader().use { it.readText() })
                Settings.loadVars(this)
                if (!Settings.has(SETTING_DEF_LAYOUT)) {
                    Settings[SETTING_DEF_LAYOUT] = name
                    Settings.save(this)
                }
                Settings.restart = true
                parentLayout!!.removeView(fileView)
            }

            textView.text = name
            parentLayout!!.addView(fileView)
        }
    }

    override fun onResume() {
        super.onResume()
        layoutsList()
    }
}
