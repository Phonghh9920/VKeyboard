package com.vladgba.keyb;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

public class Main extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        final Activity that = this;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        String html = getString(R.string.main_text);
        Spanned content = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) : Html.fromHtml(html);

        TextView description = findViewById(R.id.main_description);
        description.setMovementMethod(LinkMovementMethod.getInstance());
        description.setText(content, TextView.BufferType.SPANNABLE);

        final Button manageKeyb = findViewById(R.id.main_manage_onscreen_keyboards);
        manageKeyb.setOnClickListener(v -> startActivityForResult(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS), 0));

        final Button inputMethod = findViewById(R.id.main_choose_input_method_btn);
        inputMethod.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                mgr.showInputMethodPicker();
            } else {
                Toast.makeText(getApplicationContext(), "Need Android 6+", Toast.LENGTH_LONG).show();
            }
        });

        final Button grantPermissions = findViewById(R.id.main_grant_permission);
        grantPermissions.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        Intent getpermission = new Intent();
                        getpermission.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(getpermission);
                    }
                } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(that, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                }
            }
        });

        final Button settings = findViewById(R.id.main_settings_btn);
        settings.setOnClickListener(v -> startActivityForResult(new Intent(that, Settings.class), 0));
    }
}