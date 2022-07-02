package com.vladgba.keyb;

import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Environment;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputConnection;
import org.jetbrains.annotations.NotNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class VKeyboard extends InputMethodService implements VKeybView.OnKeyboardActionListener {

    private VKeybView keybViev;
    private boolean ctrlPressed = false;
    public static boolean shiftPressed = false;
    private Keyboard latinKeybPortrait;
    private Keyboard cyrillicKeybPortrait;
    private Keyboard latinKeybLandscape;
    private Keyboard cyrillicKeybLandscape;
    private boolean isLatin = true;
    private boolean isPortrait = true;

    @Override
    public View onCreateInputView() {
        try {
            keybViev = (VKeybView) getLayoutInflater().inflate(R.layout.vkeybview,null);
        } catch (Exception e) {
            Log.d("CreateInputView", e.getMessage());
        }

        latinKeybPortrait = new Keyboard(this,loadKeybLayout("vkeyb/latin-portrait"));
        cyrillicKeybPortrait = new Keyboard(this,loadKeybLayout("vkeyb/cyrillic-portrait"));
        latinKeybLandscape = new Keyboard(this,loadKeybLayout("vkeyb/latin-landscape"));
        cyrillicKeybLandscape = new Keyboard(this,loadKeybLayout("vkeyb/cyrillic-landscape"));

        setKeyb();
        keybViev.setOnKeyboardActionListener(this);
        return keybViev;
    }

    private String loadKeybLayout(String name) {
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
        } catch(IOException e) {
            Log.d("Keyb", "Error");
            Log.d("Keyb", e.getMessage());
        }
        return "";
    }

    public void changeKeyb() {
        isLatin = !isLatin;
        setKeyb();
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && this.isPortrait) updateOrientation(false);
        else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT && !this.isPortrait) updateOrientation(true);
    }

    private void updateOrientation(boolean b) {
        this.isPortrait = b;
        setKeyb();
    }

    private void setKeyb() {
        if (isLatin) keybViev.setKeyboard(this.isPortrait ? latinKeybPortrait : latinKeybLandscape);
        else keybViev.setKeyboard(this.isPortrait ? cyrillicKeybPortrait : cyrillicKeybLandscape);
    }

    @Override
    public void onPress(int i) {
    }

    @Override
    public void onRelease(int i) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode<0) return true;
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (keybViev.isShown()) {
                    ctrlPressed = true;
                    super.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, event);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (keybViev.isShown()) {
                    shiftPressed = true;
                    latinKeybPortrait.setShifted(shiftPressed);
                    cyrillicKeybPortrait.setShifted(shiftPressed);
                    keybViev.invalidateAllKeys();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (keybViev.isShown()) {
                    ctrlPressed = false;
                    super.onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, event);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (keybViev.isShown()) {
                    shiftPressed = false;
                    latinKeybPortrait.setShifted(shiftPressed);
                    cyrillicKeybPortrait.setShifted(shiftPressed);
                    keybViev.invalidateAllKeys();
                    return true;
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void keyShiftable(int keyAct, int key, InputConnection ic) {
        long evt = System.currentTimeMillis();
        int mod = ctrlPressed ? KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON : 0;
        ic.sendKeyEvent(new KeyEvent(
                evt, evt,
                keyAct,
                key,
                0,
                shiftPressed ? mod | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_ON : mod
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
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
    }
    public void release(int key, @NotNull InputConnection ic) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }
    public void click(int key, InputConnection ic) {
        press(key, ic);
        release(key, ic);
    }


    @Override
    public void onKey(int i, int[] ints) {
        long evt = System.currentTimeMillis();
        InputConnection ic = getCurrentInputConnection();
        int curr = 0;
        if(i > 96 && i < 123) { // idk where i got these numbers
            clickShiftable(i-68, ic);
        } else if (i >= -19 && i <= -22) { // DPAD
            clickShiftable(i * -1, ic);
        } else {
            switch (i) {
                case -112: // Forward DEL
                    click(i*-1, ic);
                    break;
                case -9:
                    clickShiftable(KeyEvent.KEYCODE_TAB, ic);
                    break;
                case -32:
                    click(KeyEvent.KEYCODE_SPACE, ic);
                    break;
                case Keyboard.KEYCODE_DELETE:
                    clickShiftable(KeyEvent.KEYCODE_DEL, ic);
                    break;
                case Keyboard.KEYCODE_SHIFT: // changed to lang switch
                    changeKeyb();
                    break;
                case Keyboard.KEYCODE_DONE:
                    click(KeyEvent.KEYCODE_ENTER, ic);
                    break;
                default:
                    char code = getShiftable((char)i);
                    ic.commitText(String.valueOf(code),1);
            }
        }
    }

    public static char getShiftable(char code) {
        return (Character.isLetter(code) && shiftPressed) ? Character.toUpperCase(code) : code;
    }

    @Override
    public void onText(CharSequence chars) {
        for(int i = 0; i < chars.length(); i++) onKey(chars.charAt(i), new int[] {chars.charAt(i)});
    }

    @Override
    public void swipeLeft() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_LEFT, getCurrentInputConnection());
    }

    @Override
    public void swipeRight() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_RIGHT, getCurrentInputConnection());
    }

    @Override
    public void swipeDown() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_DOWN, getCurrentInputConnection());
    }

    @Override
    public void swipeUp() {
        clickShiftable(KeyEvent.KEYCODE_DPAD_UP, getCurrentInputConnection());
    }
}
