package com.vladgba.keyb

import android.os.Bundle
import android.preference.PreferenceFragment

class SettingsFrag : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings)
    }
}