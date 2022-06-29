package com.vladgba.keyb;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class Main extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        final Activity that = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        String html = getString(R.string.main_text);
        Spanned content = Html.fromHtml(html);
        TextView description = (TextView) findViewById(R.id.main_description);
        description.setMovementMethod(LinkMovementMethod.getInstance());
        description.setText(content, TextView.BufferType.SPANNABLE);

        final Button manageKeyb = (Button) findViewById(R.id.main_manage_onscreen_keyboards);
        manageKeyb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS), 0);
            }
        });

        final Button inputMethod = (Button) findViewById(R.id.main_choose_input_method_btn);
        inputMethod.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    mgr.showInputMethodPicker();
                } else {
                    Toast.makeText(getApplicationContext(), "Need Android 6+", Toast.LENGTH_LONG).show();
                }
            }
        });

        final Button settings = (Button) findViewById(R.id.main_settings_btn);
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivityForResult(new Intent(that, Settings.class), 0);
            }
        });
    }
}