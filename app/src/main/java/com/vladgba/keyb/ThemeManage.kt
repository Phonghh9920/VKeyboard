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
    private var inflater: LayoutInflater? = null
    private var parentLayout: LinearLayout? = null


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

            colorSettings(fileView, name)

            fileView.findViewById<ImageButton>(R.id.layout_delete).setOnClickListener {
                var dialog: AlertDialog? = null
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.delete_file_title)
                builder.setMessage(R.string.delete_file_confirmation)
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

    private fun colorSettings(v: View, name: String) {
        val file = PFile(this, name, THEME_EXT)
        val node = Flexaml(file.read()).parse()
        val btn = v.findViewById<ImageButton>(R.id.layout_edit)
        btn.setOnClickListener {

            val dialogBuilder = AlertDialog.Builder(this)

            dialogBuilder.setTitle(String.format(getString(R.string.theme_editing_title), name))
            dialogBuilder.setView(R.layout.color_settings)
            dialogBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
                file.write(node.toString())
            }
            dialogBuilder.setNeutralButton(R.string.edit_raw) { _, _ ->
                startActivityForResult(Intent(this, KeybRawEditor::class.java).apply {
                    putExtra("name", name)
                    putExtra("ext", THEME_EXT)
                }, 0)
            }
            dialogBuilder.setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }

            val dialog = dialogBuilder.create()

            dialog.show()

            for (i in COLORS) {
                val item = View.inflate(this, R.layout.color_settings_item, null)
                item.findViewById<TextView>(R.id.color_name).text = i
                val currColorValue = node.str(i)
                val hexColor = try {
                    Color.parseColor("#$currColorValue")
                } catch (_: Exception) {
                    0
                }
                val itemPreview = item.findViewById<View>(R.id.color_preview).apply { setBackgroundColor(hexColor) }
                dialog.findViewById<LinearLayout>(R.id.scroll_colors).addView(item)

                bindColorPicker(item, i, hexColor, node, itemPreview)
            }
        }
    }

    private fun bindColorPicker(
        item: View,
        i: String,
        hexColor: Int,
        node: Flexaml.FxmlNode,
        preview: View
    ) {
        item.setOnClickListener {
            ColorPicker(this, hexColor, true, object : ColorPicker.ColorPickerListener {
                override fun onOk(dialog: ColorPicker, color: Int) {
                    val newColor = color.toUInt().toString(16)
                    node[i] = newColor
                    preview.setBackgroundColor(color)
                    bindColorPicker(item, i, color, node, preview)
                }

                override fun onCancel(dialog: ColorPicker) {}
            }).show()
        }
    }

    override fun onResume() {
        super.onResume()
        layoutsList()
    }
}
