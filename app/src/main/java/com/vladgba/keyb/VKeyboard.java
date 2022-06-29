package com.vladgba.keyb;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.IBinder;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputConnection;

public class VKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener {
    private KeyboardView keybViev;
    private boolean ctrlPressed = false;
    public static boolean shiftPressed = false;
    private Keyboard latinKeybPortrait;
    private Keyboard cyrillicKeybPortrait;
    private Keyboard latinKeybLandscape;
    private Keyboard cyrillicKeybLandscape;
    private boolean isLatin = true;
    private boolean isPortrait = false;
    IBinder mToken;
    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
        return new MyInputMethodImpl();
    }

    public class MyInputMethodImpl extends InputMethodImpl {
        @Override
        public void attachToken(IBinder token) {
            super.attachToken(token);
            Log.i("VKey", "attachToken " + token);
            if (mToken == null) mToken = token;
        }
    }
    @Override
    public View onCreateInputView() {
        keybViev = (VKeybView)getLayoutInflater().inflate(R.layout.vkeybview,null);

        latinKeybPortrait = new Keyboard(this,R.xml.latin_port);
        cyrillicKeybPortrait = new Keyboard(this,R.xml.cyrillic_port);
        latinKeybLandscape = new Keyboard(this,R.xml.latin_land);
        cyrillicKeybLandscape = new Keyboard(this,R.xml.cyrillic_port);

        setKeyb();
        keybViev.setOnKeyboardActionListener(this);
        return keybViev;
    }

    public void changeKeyb() {
        isLatin = !isLatin;
        setKeyb();
    }
    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (this.isPortrait) updateOrientation(false);
        } else if (cfg.orientation == Configuration.ORIENTATION_PORTRAIT){
            if (!this.isPortrait) updateOrientation(true);
        }
    }
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        Resources res = getResources();

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
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (keybViev.isShown()) {
                    shiftPressed = true;
                    latinKeybPortrait.setShifted(shiftPressed);
                    cyrillicKeybPortrait.setShifted(shiftPressed);
                    keybViev.invalidateAllKeys();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (keybViev.isShown()) {
                    ctrlPressed = true;
                    super.onKeyDown(KeyEvent.KEYCODE_CTRL_LEFT, event);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (keybViev.isShown()) {
                    shiftPressed = false;
                    latinKeybPortrait.setShifted(shiftPressed);
                    cyrillicKeybPortrait.setShifted(shiftPressed);
                    keybViev.invalidateAllKeys();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (keybViev.isShown()) {
                    ctrlPressed = false;
                    super.onKeyUp(KeyEvent.KEYCODE_CTRL_LEFT, event);
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
    public void press(int key, InputConnection ic) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));
    }
    public void release(int key, InputConnection ic) {
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
            return;
        }
        switch (i) {
            case -112: // Forward DEL
                click(i*-1, ic);
                break;
            case -9:
                clickShiftable(KeyEvent.KEYCODE_TAB, ic);
                break;
            case -19://DPAD
            case -20:
            case -21:
            case -22:
                clickShiftable(i*-1, ic);
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

    public static char getShiftable(char code) {
        return (Character.isLetter(code) && shiftPressed) ? Character.toUpperCase(code) : code;
    }

    @Override
    public void onText(CharSequence charSequence) {

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
