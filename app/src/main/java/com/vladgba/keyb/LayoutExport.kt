package com.vladgba.keyb

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.io.IOException

class LayoutExport : Activity() {
    var name = ""

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        name = intent.getStringExtra("name")!!
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "$name.$LAYOUT_EXT")
            putExtra("name", name)
        }, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 1 && resultCode == RESULT_OK) {
                try {
                    this.contentResolver.openOutputStream(uri!!)!!.apply {
                        write(PFile(applicationContext, name).read().toByteArray())
                        flush()
                        close()
                    }
                } catch (_: IOException) { }
        }
        finish()
    }
}