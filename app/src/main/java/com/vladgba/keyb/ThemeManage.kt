package com.vladgba.keyb

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class ThemeManage : Activity() {
    var inflater: LayoutInflater? = null
    var parentLayout: LinearLayout? = null


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.theme_picker)
        title = getString(R.string.themes)
        inflater = LayoutInflater.from(this)
        parentLayout = findViewById(R.id.picker_list)

        findViewById<Button>(R.id.new_layout).setOnClickListener {
            startActivityForResult(Intent(this, KeybRawEditor::class.java).apply {
                putExtra("name", BLANK_THEME)
                putExtra("base", BLANK_THEME)
                putExtra("ext", THEME_EXT)
            }, 0)
        }

        layoutsList()
    }

    private fun layoutsList() {
        if (inflater == null || parentLayout == null) return
        val fileList = filesDir.listFiles() ?: return
        parentLayout!!.removeAllViews()

        for (file in fileList) {
            var name = file.name
            val ext = name.lastIndexOf(".$THEME_EXT")
            if (ext < 0) continue

            name = name.substring(0, ext)

            val fileView = inflater!!.inflate(R.layout.layout_item, parentLayout, false)

            val textView = fileView.findViewById<TextView>(R.id.layout_name)

            fileView.findViewById<ImageButton>(R.id.layout_backup).setOnClickListener {
                startActivityForResult(Intent(this, LayoutExport::class.java).apply {
                    putExtra("name", name)
                    putExtra("ext", THEME_EXT)
                }, 0)
            }

            fileView.findViewById<ImageButton>(R.id.layout_set_theme).apply {
                visibility = View.VISIBLE
            }.setOnClickListener {
                var dialog: AlertDialog? = null
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.theme_set_title)
                builder.setMessage(
                    String.format(
                        getString(R.string.themes_curently_in_use),
                        Settings.str(THEME_DAY),
                        Settings.str(THEME_NIGHT)
                    )
                )
                builder.setPositiveButton(R.string.theme_day) { _, _ ->
                    Settings[THEME_DAY] = name
                    Settings.save(this)
                }
                builder.setNeutralButton(android.R.string.cancel) { _, _ ->
                    dialog?.cancel()
                }
                builder.setNegativeButton(R.string.theme_night) { _, _ ->
                    Settings[THEME_NIGHT] = name
                    Settings.save(this)
                }
                dialog = builder.create()
                dialog.show()
            }

            fileView.findViewById<ImageButton>(R.id.layout_edit).setOnClickListener {
                startActivityForResult(Intent(this, KeybRawEditor::class.java).apply {
                    putExtra("name", name)
                    putExtra("ext", THEME_EXT)
                }, 0)
            }

            fileView.findViewById<ImageButton>(R.id.layout_delete).setOnClickListener {
                var dialog: AlertDialog? = null
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Delete file")
                builder.setMessage("Are you sure you want to delete this file?")
                builder.setPositiveButton(R.string.delete) { _, _ ->
                    "$name.$THEME_EXT".let { File(filesDir, it).apply { if (exists()) delete(); layoutsList() } }
                }
                builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                    dialog?.cancel()
                }
                dialog = builder.create()
                dialog!!.show()
                dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
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
