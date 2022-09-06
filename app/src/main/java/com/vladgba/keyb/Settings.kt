package com.vladgba.keyb

import android.app.Activity
import android.os.Bundle
import android.view.View

class Settings : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_view)
        if (findViewById<View?>(R.id.idFrameLayout) != null) {
            if (savedInstanceState != null) return
            fragmentManager.beginTransaction().add(R.id.idFrameLayout, SettingsFrag()).commit()
        }
    }

    override fun onPause() {
        super.onPause()
        needReload = true
    }

    companion object {
        var needReload = false
    }
}