package com.vladgba.keyb;

import android.content.Context;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import org.jetbrains.annotations.NotNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class VKeyboard extends InputMethodService {
    private VKeybView keybView;
    private boolean ctrlPressed = false;
    public static boolean shiftPressed = false;
    private static Keyboard latinKeybPortrait;
    private static Keyboard cyrillicKeybPortrait;
    private static Keyboard latinKeybLandscape;
    private static Keyboard cyrillicKeybLandscape;
    private static boolean isLatin = true;
    private static boolean isPortrait = true;
    private static Context that;

    public void reload() {
        if (that == null) return;

        latinKeybPortrait = new Keyboard(that, loadKeybLayout("vkeyb/latin-portrait"), true);
        cyrillicKeybPortrait = new Keyboard(that, loadKeybLayout("vkeyb/cyrillic-portrait"), true);
        latinKeybLandscape = new Keyboard(that, loadKeybLayout("vkeyb/latin-landscape"), false);
        cyrillicKeybLandscape = new Keyboard(that, loadKeybLayout("vkeyb/cyrillic-landscape"), false);
        keybView.loadVars(that);
        setKeyb();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        if (Settings.needReload) {
            reload();
            Settings.needReload = false;
        }

    }

    @Override
    public View onCreateInputView() {
        that = this;
        try {
            keybView = (VKeybView) getLayoutInflater().inflate(R.layout.vkeybview, null);
        } catch (Exception e) {
            Log.d("CreateInputView", e.getMessage());
        }
        int orientation = this.getResources().getConfiguration().orientation;
        isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT;
        reload();
        keybView.setOnKeyboardActionListener(this);
        return keybView;
    }

    private static String loadKeybLayout(String name) {
        File sdcard = Environment.getExternalStorageDirectory();

        File file = new File(sdcard, name + ".json");
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
            Log.d("Keyb", "Done");
            return String.valueOf(text);
        } catch (IOException e) {
            Log.d("Keyb", "Error");
            Log.d("Keyb", e.getMessage());
        }
        return "";
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && isPortrait) updateOrientation(false);
        else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !isPortrait) updateOrientation(true);
    }

    private void updateOrientation(boolean b) {
        isPortrait = b;
        setKeyb();
    }

    private void setKeyb() {
        if (isLatin) keybView.setKeyboard(isPortrait ? latinKeybPortrait : latinKeybLandscape);
        else keybView.setKeyboard(isPortrait ? cyrillicKeybPortrait : cyrillicKeybLandscape);
    }

    private void forceLatin() {
        keybView.setKeyboard(isPortrait ? latinKeybPortrait : latinKeybLandscape);
    }

    public void onPress(int i) {
    }

    public void onRelease(int i) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keybView == null) return false;
        if (keyCode < 0) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (keybView.isShown()) {
                    ctrlPressed = true;
                    if (keybView.ctrlModi) return true;
                    else if (keybView.pressed) keybView.ctrlModi = true;
                    forceLatin();
                    super.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, event);
                    return true;
                } else if (ctrlPressed) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (keybView.isShown()) {
                    shiftPressed = true;
                    if (keybView.shiftModi) return true;
                    else if (keybView.pressed) keybView.shiftModi = true;
                    long now = System.currentTimeMillis();
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.sendKeyEvent(new KeyEvent(
                            now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON));

                    latinKeybPortrait.setShifted(shiftPressed);
                    cyrillicKeybPortrait.setShifted(shiftPressed);
                    latinKeybLandscape.setShifted(shiftPressed);
                    cyrillicKeybLandscape.setShifted(shiftPressed);

                    keybView.invalidateAllKeys();
                    return true;
                } else if (shiftPressed) {
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keybView == null) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                ctrlPressed = false;
                if (keybView.isShown()) {
                    setKeyb();
                    super.onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, event);
                    return true;
                } else if (ctrlPressed) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                shiftPressed = false;
                if (keybView.isShown()) {

                    long now = System.currentTimeMillis();
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.sendKeyEvent(new KeyEvent(
                            now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON));

                    latinKeybPortrait.setShifted(shiftPressed);
                    cyrillicKeybPortrait.setShifted(shiftPressed);
                    latinKeybLandscape.setShifted(shiftPressed);
                    cyrillicKeybLandscape.setShifted(shiftPressed);
                    keybView.invalidateAllKeys();
                    return true;
                } else if (shiftPressed) {
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void keyShiftable(int keyAct, int key, InputConnection ic) {
        long time = System.currentTimeMillis();
        int ctrl = ctrlPressed || keybView.ctrlModi ? KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON : 0;
        ic.sendKeyEvent(new KeyEvent(
                time, time,
                keyAct,
                key,
                0,
                (shiftPressed || keybView.shiftModi ? KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON : 0) | ctrl
        ));
    }

    private void pressShiftable(int key, InputConnection ic) {
        keyShiftable(KeyEvent.ACTION_DOWN, key, ic);
    }

    private void releaseShiftable(int key, InputConnection ic) {
        keyShiftable(KeyEvent.ACTION_UP, key, ic);
    }

    private void clickShiftable(int key, InputConnection ic) {
        pressShiftable(key, ic);
        releaseShiftable(key, ic);
    }

    public void press(int key, @NotNull InputConnection ic) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
    }

    public void release(int key, @NotNull InputConnection ic) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }

    public void click(int key) {
        InputConnection ic = getCurrentInputConnection();
        click(key, ic);
    }

    public void click(int key, InputConnection ic) {
        getCurrentInputConnection();
        press(key, ic);
        release(key, ic);
    }

    public void onKey(int i, int[] ints) {
        InputConnection ic = getCurrentInputConnection();
        if (i > 96 && i < 123) { // a-z
            clickShiftable(i - 68, ic);
        } else if (i == -2) { // lang switch
            isLatin = !isLatin;
            setKeyb();
        } else if (i < 0) {
            clickShiftable(i * -1, ic);
        } else {
            Log.d("key", String.valueOf(i));
            char code = getShiftable((char) i, keybView.shiftModi || shiftPressed);
            ic.commitText(String.valueOf(code), 1);
        }
    }

    public static char getShiftable(char code, boolean sh) {
        return (Character.isLetter(code) && sh) ? Character.toUpperCase(code) : code;
    }

    public void onText(CharSequence chars) {
        InputConnection ic = getCurrentInputConnection();
        ic.commitText(String.valueOf(chars), 1);
    }

    public void swipeLeft() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_LEFT, getCurrentInputConnection());
    }

    public void swipeRight() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_RIGHT, getCurrentInputConnection());
    }

    public void swipeDown() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_DOWN, getCurrentInputConnection());
    }

    public void swipeUp() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_UP, getCurrentInputConnection());
    }
}
