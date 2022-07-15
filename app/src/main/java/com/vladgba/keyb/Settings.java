package com.vladgba.keyb;

import android.app.Activity;
import android.os.Bundle;

public class Settings extends Activity {
    public static boolean needReload = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_view);
        //getActionBar().setTitle("Settings");
        if (findViewById(R.id.idFrameLayout) != null) {
            if (savedInstanceState != null) return;
            getFragmentManager().beginTransaction().add(R.id.idFrameLayout, new SettingsFrag()).commit();
        }
    }

    protected void onPause() {
        super.onPause();
        needReload = true;
    }
}