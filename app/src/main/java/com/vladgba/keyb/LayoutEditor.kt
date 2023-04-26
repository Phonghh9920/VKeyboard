package com.vladgba.keyb

import android.app.Activity
import android.content.Intent
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

        val custKeyb = findViewById<LinearLayout>(R.id.keyb_layout)
        val addRow = findViewById<Button>(R.id.add_row)
        val addKey = findViewById<Button>(R.id.add_key)
        val layoutSave = findViewById<Button>(R.id.layout_save)
        val layoutRaw = findViewById<Button>(R.id.layout_raw)
        ctrl = KeybCtl(this, null)

        addRow.setOnClickListener {
            val dn = Flexaml.FxmlNode()
            val newRow = KeybLayout.Row(ctrl!!.keybLayout!!, dn, 0)
            ctrl!!.keybLayout!!.rows.let {
                val rowCount = it.size
                it.add(0, newRow)

                for (i in 1 .. rowCount) {
                    it[i].moveVertical(it[i-1].height + it[i-1].y)
                }
            }
            ctrl!!.keybLayout!!.childs.add(0, dn)
            ctrl!!.keybLayout!!.height += newRow.height
            ctrl!!.view.repaintKeyb()
            custKeyb.removeAllViews()
            custKeyb.addView(ctrl!!.view)
        }

        addKey.setOnClickListener {
            ctrl!!.keybLayout!!.rows.let {
                val rowCount = it.size
                if (rowCount > 0) {
                    it[0].apply {
                        val fxn = Flexaml.FxmlNode(this)
                        keys.add(0, Key(ctrl!!, this, 0, this.y, fxn))
                        options.childs.add(0, fxn)
                        calcWidth()

                        ctrl!!.view.repaintKeyb()
                        ctrl!!.view.invalidate()
                    }
                }
            }

        }

        layoutSave.setOnClickListener {
            Log.d("file", name!!)
            Log.d("data", ctrl!!.keybLayout!!.toString())
            if (ctrl != null) PFile(this, name!!).write(ctrl!!.keybLayout!!.toString())
        }

        layoutRaw.setOnClickListener {
            startActivityForResult(Intent(this, KeybRawEditor::class.java).apply { putExtra("name", name) }, 0)
        }

        if (name != null && name.indexOf("settings") < 0) {
            ctrl!!.currentLayout = name
            ctrl!!.reloadLayout()
        } else {
            startActivityForResult(Intent(this, KeybRawEditor::class.java).apply { putExtra("name", name) }, 0)
            finish()
        }

        custKeyb.addView(ctrl!!.view)
    }

    override fun onResume() {
        super.onResume()
        if (ctrl != null) ctrl!!.reloadLayout()
    }
}
