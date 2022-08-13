package com.vladgba.keyb;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import com.vladgba.keyb.Keyboard.Key;
import java.util.List;

public class KeyboardView extends View implements View.OnClickListener {
    public static final int NOT_A_KEY = -1;
    private Keyboard keyb;
    private final PopupWindow popupKeyboard;
    private Key[] keys;
    public VKeyboard keybActionListener;
    private int proximityThreshold;

    public KeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Drawable keyBackground = context.getDrawable(R.drawable.keyboard_bg_color);
        popupKeyboard = new PopupWindow(context);
        popupKeyboard.setBackgroundDrawable(null);
        keyBackground.getPadding(new Rect(0, 0, 0, 0));
        resetMultiTap();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }


    public void setOnKeyboardActionListener(VKeyboard listener) {
        keybActionListener = listener;
    }

    protected VKeyboard getOnKeyboardActionListener() {
        return keybActionListener;
    }

    public void setKeyboard(Keyboard keyboard) {
        removeMessages();
        keyb = keyboard;
        List<Key> keys = keyb.getKeys();
        this.keys = keys.toArray(new Key[0]);
        requestLayout();
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
    }

    public Keyboard getKeyboard() {
        return keyb;
    }

    public void onClick(View v) {
        dismissPopupKeyboard();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (keyb == null) {
            setMeasuredDimension(0, 0);
        } else {
            int width = keyb.getMinWidth();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            }
            setMeasuredDimension(width, keyb.getHeight());
        }
    }

    private void computeProximityThreshold(Keyboard keyboard) {
        if (keyboard == null) return;
        final Key[] keys = this.keys;
        if (keys == null) return;
        int length = keys.length;

        if (length == 0) return;
        int dimensionSum = 0;
        for (Key key : keys) dimensionSum += Math.min(key.width, key.height);
        if (dimensionSum < 0) return;
        proximityThreshold = (int) (dimensionSum * 1.4f / length);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (keyb != null) keyb.resize(w);
    }

    int getKeyIndices(int x, int y) {
        final Key[] keys = this.keys;
        int primaryIndex = NOT_A_KEY;
        int closestKey = NOT_A_KEY;
        int closestKeyDist = proximityThreshold + 1;
        int[] nearestKeyIndices = keyb.getNearestKeys(x, y);
        for (int nearestKeyIndex : nearestKeyIndices) {
            final Key key = keys[nearestKeyIndex];
            int dist = 0;
            boolean isInside = key.isInside(x, y);
            if (isInside) {
                primaryIndex = nearestKeyIndex;
            }
            if (isInside && key.codes[0] > 32) {
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndex;
                }
            }
        }
        return primaryIndex == NOT_A_KEY ? closestKey : primaryIndex;
    }

    public void invalidateAllKeys() {
        invalidate();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return true;
    }

    public void closing() {
        removeMessages();
        dismissPopupKeyboard();
    }

    private void removeMessages() {
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }

    private void dismissPopupKeyboard() {
        if (popupKeyboard.isShowing()) {
            popupKeyboard.dismiss();
            invalidateAllKeys();
        }
    }

    private void resetMultiTap() {
    }
}