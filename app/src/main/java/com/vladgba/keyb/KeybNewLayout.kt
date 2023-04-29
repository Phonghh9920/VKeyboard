package com.vladgba.keyb

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import java.io.File

class KeybNewLayout : Activity(),  AdapterView.OnItemSelectedListener {
    var base = ""
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_add)

        val text = findViewById<EditText>(R.id.new_text)
        val saveBtn = findViewById<Button>(R.id.new_save)
        val spinner = findViewById<Spinner>(R.id.new_layout_base)
        var aa = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("") +  assets.list("")!!.filter {
                it.indexOf(".$LAYOUT_EXT") >= 0
            }.map {
                val i = it.indexOf(".$LAYOUT_EXT")
                if (i >= 0) it.substring(0, i)
                else null
            }
        )
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = aa
        spinner.onItemSelectedListener = this
        base = spinner.selectedItem.toString()
        saveBtn.setOnClickListener {
            if (text.text.isBlank()) {
                val builder = AlertDialog.Builder(this)
                builder.setMessage("Name can not be empty!")
                builder.setPositiveButton("OK") { d, _ -> d.cancel() }
                builder.show()
            } else {
                startActivityForResult(Intent(this, KeybRawEditor::class.java).apply {
                    putExtra("name", text.text.trim().toString())
                    putExtra("base", base)
                    putExtra("new", true)
                }, 0)
                finish()
            }
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        base = findViewById<Spinner>(R.id.new_layout_base).selectedItem.toString()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }
}