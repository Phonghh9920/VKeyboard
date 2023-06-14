package com.vladgba.keyb

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout

class LayoutEditor : Activity() {
    var ctrl: KeybCtl? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_edit)

        val name = intent.getStringExtra("name")
        title = name
        val custKeyb = findViewById<LinearLayout>(R.id.keyb_layout)
        val addRow = findViewById<Button>(R.id.add_row)
        val addKey = findViewById<Button>(R.id.add_key)
        val layoutSave = findViewById<Button>(R.id.layout_save)
        val layoutRaw = findViewById<Button>(R.id.layout_raw)
        ctrl = KeybCtl(this, null)

        addRow.setOnClickListener {
            val dn = Flexaml.FxmlNode()
            val newRow = KeybLayout.Row(ctrl!!.currentLayout!!, dn, 0)
            ctrl!!.currentLayout!!.rows.let {
                val rowCount = it.size
                it.add(0, newRow)

                for (i in 1 .. rowCount) {
                    it[i].moveVertical(it[i-1].height + it[i-1].y)
                }
            }
            ctrl!!.currentLayout!!.childs.add(0, dn)
            ctrl!!.currentLayout!!.height += newRow.height
            ctrl!!.requestLayout()
            custKeyb.removeAllViews()
            custKeyb.addView(ctrl!!)
        }

        addKey.setOnClickListener {
            ctrl!!.currentLayout!!.rows.let {
                val rowCount = it.size
                if (rowCount > 0) {
                    it[0].apply {
                        val fxn = Flexaml.FxmlNode(this)
                        keys.add(0, Key(ctrl!!, this, 0, this.y, fxn))
                        options.childs.add(0, fxn)
                        calcWidth()

                        ctrl!!.invalidate()
                    }
                }
            }

        }

        layoutSave.setOnClickListener {
            Log.d("file", name!!)
            Log.d("data", ctrl!!.currentLayout!!.toString())
            if (ctrl != null) PFile(this, name).write(ctrl!!.currentLayout!!.toString())
        }

        layoutRaw.setOnClickListener {
            startActivityForResult(Intent(this, KeybRawEditor::class.java).apply { putExtra("name", name) }, 0)
        }

        if (name != null && name.indexOf("settings") < 0) {
            ctrl!!.currentLayoutName = name
            ctrl!!.reloadLayout()
        } else {
            startActivityForResult(Intent(this, KeybRawEditor::class.java).apply { putExtra("name", name) }, 0)
            finish()
        }

        custKeyb.addView(ctrl!!)
    }

    override fun onBackPressed() {
        var dialog: AlertDialog? = null
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Confirm")
        builder.setMessage("All unsaved changes will be lost. Are you sure?")
        builder.setPositiveButton(R.string.yes) { _, _ -> finish() }
        builder.setNegativeButton(R.string.no) { _, _ -> dialog?.cancel() }
        dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
    }

    override fun onResume() {
        super.onResume()
        if (ctrl != null) ctrl!!.reloadLayout()
    }
}
