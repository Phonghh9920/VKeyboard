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
    var isLayouts = true
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_prebuilt_picker)

        isLayouts = intent.getBooleanExtra("layouts", true)
        title = getString(if (isLayouts) R.string.prebuilt_layouts else R.string.prebuilt_themes)

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

        prebuiltList()
    }

    private fun prebuiltList() {
        if (inflater == null || parentLayout == null) return
        val list: Array<String>? = assets.list("")
        parentLayout!!.removeAllViews()

        val fileExt = if (isLayouts) LAYOUT_EXT else THEME_EXT
        for (file in list!!) {
            var name = file
            val extIndex = name.lastIndexOf(".$fileExt")
            if (extIndex < 0) continue
            name = name.substring(0, extIndex)
            val blankFile = if (isLayouts) BLANK_LAYOUT else BLANK_THEME
            if (PFile(this, name, fileExt).exists() || (name == blankFile)) continue

            val fileView = inflater!!.inflate(R.layout.prebuilt_item, parentLayout, false)
            val textView = fileView.findViewById<TextView>(R.id.layout_name)

            fileView.findViewById<ImageButton>(R.id.layout_add_prebuilt).setOnClickListener {
                PFile(this, name, fileExt).write(
                    assets.open("$name.$fileExt").bufferedReader().use { it.readText() })
                Settings.loadVars(this)
                if (!Settings.has(SETTING_DEF_LAYOUT)) {
                    Settings[SETTING_DEF_LAYOUT] = name
                    Settings.save(this)
                }
                parentLayout!!.removeView(fileView)
            }

            textView.text = name
            parentLayout!!.addView(fileView)
        }
    }

    override fun onResume() {
        super.onResume()
        prebuiltList()
    }
}
