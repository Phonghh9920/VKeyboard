package com.vladgba.keyb

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast


class KeybRawEditor : Activity() {
    private var editText: EditText? = null
    private var editName: EditText? = null
    lateinit var name: String
    var base: String = ""
    var ext: String = LAYOUT_EXT
    lateinit var file: PFile
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.raw_edit)
        name = intent.getStringExtra("name") ?: return finish()
        base = intent.getStringExtra("base") ?: ""
        ext = intent.getStringExtra("ext") ?: ext
        title = name
        file = PFile(this, name, ext)
        editText = findViewById(R.id.edit_text)

        editName = findViewById<EditText>(R.id.edit_name).apply {
            text = Editable.Factory.getInstance().newEditable(name)
        }
        editText!!.width = windowManager.defaultDisplay.width // TODO: deprecated "defaultDisplay"
        editText!!.text = Editable.Factory.getInstance()
            .newEditable(if (base.isNotEmpty()) assets.open("$base.$ext").bufferedReader().use { it.readText() }
            else if (file.exists()) file.read() else loadFromAssets() ?: "")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.raw_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                Toast.makeText(this, R.string.file_saving, Toast.LENGTH_SHORT).show()
                Toast.makeText(
                    this,
                    if (PFile(
                            this,
                            editName!!.text.toString(),
                            ext
                        ).write(editText!!.editableText.toString())
                    ) R.string.file_saved
                    else R.string.file_save_fail,
                    Toast.LENGTH_LONG
                ).show()
                true
            }

            R.id.action_reformat -> {
                editText!!.text = Editable.Factory.getInstance().newEditable(
                    Flexaml(editText!!.text.toString()).parse().toString()
                )
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadFromAssets(): String? {
        return try {
            assets.open("$name.$ext").bufferedReader().use { it.readText() }
        } catch (ex: Exception) {
            null
        }
    }

    override fun onBackPressed() {
        var dialog: AlertDialog? = null
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.confirm_title)
        builder.setMessage(R.string.confirm_unsaved)
        builder.setPositiveButton(R.string.yes) { _, _ -> finish() }
        builder.setNegativeButton(R.string.no) { _, _ -> dialog?.cancel() }
        dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
    }

}