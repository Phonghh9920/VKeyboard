package com.vladgba.keyb

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class LayoutsManage : Activity() {
    private var content: String = ""
    var inflater: LayoutInflater? = null
    var parentLayout: LinearLayout? = null


    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_picker)
        title = getString(R.string.settings)
        inflater = LayoutInflater.from(this)
        parentLayout = findViewById(R.id.picker_list)

        findViewById<Button>(R.id.picker_settings).setOnClickListener {
            startActivityForResult(Intent(this, KeybRawEditor::class.java).apply {
                putExtra("name", SETTINGS_FILENAME)
            }, 0)
        }

        findViewById<Button>(R.id.picker_themes).setOnClickListener {
            startActivityForResult(Intent(this, ThemeManage::class.java), 0)
        }

        findViewById<Button>(R.id.new_layout).setOnClickListener {
            startActivityForResult(Intent(this, KeybNewLayout::class.java), 0)
        }

        layoutsList()
    }

    private fun layoutsList() {
        if (inflater == null || parentLayout == null) return
        val fileList = filesDir.listFiles() ?: return
        parentLayout!!.removeAllViews()

        for (file in fileList) {
            var name = file.name
            val ext = name.lastIndexOf(".$LAYOUT_EXT")
            if (ext < 0 || name == "$SETTINGS_FILENAME.$LAYOUT_EXT") continue

            name = name.substring(0, ext)

            val fileView = inflater!!.inflate(R.layout.layout_item, parentLayout, false)

            val textView = fileView.findViewById<TextView>(R.id.layout_name)

            fileView.findViewById<ImageButton>(R.id.layout_backup).setOnClickListener {
                startActivityForResult(Intent(this, LayoutExport::class.java).apply { putExtra("name", name) }, 0)
            }

            fileView.findViewById<ImageButton>(R.id.layout_edit).setOnClickListener {
                startActivityForResult(Intent(this, LayoutEditor::class.java).apply { putExtra("name", name) }, 0)
            }

            fileView.findViewById<ImageButton>(R.id.layout_delete).setOnClickListener {
                var dialog: AlertDialog? = null
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Delete file")
                builder.setMessage("Are you sure you want to delete this file?")
                builder.setPositiveButton(R.string.delete) { _, _ ->
                    "$name.$LAYOUT_EXT".let { File(filesDir, it).apply { if (exists()) delete(); layoutsList() } }
                }
                builder.setNegativeButton(android.R.string.cancel) { _, _ ->
                    dialog?.cancel()
                }
                dialog = builder.create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
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
