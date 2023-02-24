package com.vladgba.keyb;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.inputmethod.InputMethodManager;

public class KeybReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent serviceIntent = new Intent(context, KeybCtl.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else if (Intent.ACTION_INPUT_METHOD_CHANGED.equals(intent.getAction())) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            String packageName = context.getPackageName();
            String inputMethodId = new ComponentName(context, KeybCtl.class).flattenToShortString();
            boolean enabled = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                enabled = imm.getEnabledInputMethodList().stream()
                        .anyMatch(inputMethodInfo -> inputMethodInfo.getPackageName().equals(packageName)
                                && inputMethodInfo.getId().equals(inputMethodId));
            }
            KeybCtl.setEnabled(enabled);
        }
    }
}