package com.vladgba.keyb

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class LayoutImporter : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_VIEW == action && type != null) {
            if (type.startsWith("text/")) {
                handleText(intent.data)
            }
        }
    }

    private fun handleText(uri: Uri?) {
        uri?.let {
            val name = File(uri.path!!).name
            var content = ""
            contentResolver.openInputStream(it)?.let { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String? = reader.readLine()
                val stringBuilder = StringBuilder()
                while (line != null) {
                    stringBuilder.append(line).append('\n')
                    line = reader.readLine()
                }
                content = stringBuilder.toString()
                inputStream.close()
            }
            val suffix = name.indexOf(".$LAYOUT_EXT")
            importLayout(if (suffix > 0) name.substring(0, suffix) else name, content)
        }
    }

    private fun importLayout(name: String, content: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.file_name)

        val input = EditText(this)
        input.text = Editable.Factory.getInstance().newEditable(name)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            Toast.makeText(this, R.string.file_importing, Toast.LENGTH_SHORT).show()
            Toast.makeText(this, if(PFile(this, input.text.toString()).write(content)) R.string.file_imported else R.string.file_import_fail, Toast.LENGTH_LONG).show()
            finish()
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
            finish()
        }

        builder.show()
    }
}