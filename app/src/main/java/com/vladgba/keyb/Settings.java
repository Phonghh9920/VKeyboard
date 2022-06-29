package com.vladgba.keyb;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class Settings extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_view);
        getSupportActionBar().setTitle("Settings");
        if (findViewById(R.id.idFrameLayout) != null) {
            if (savedInstanceState != null) return;
            getFragmentManager().beginTransaction().add(R.id.idFrameLayout, new SettingsFrag()).commit();
        }
    }
}