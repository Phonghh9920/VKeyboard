package com.vladgba.keyb;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupWindow;
import com.vladgba.keyb.Keyboard.Key;

public class KeyboardView extends View implements View.OnClickListener {
    private Keyboard keyb;
    private final PopupWindow popupKeyboard;
    public VKeyboard keybActionListener;

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
        keyb = keyboard;
        requestLayout();
        invalidate();
    }

    public Keyboard getKeyboard() {
        return keyb;
    }

    public void onClick(View v) {
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


    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (keyb != null) keyb.resize(w);
    }

    Key getKey(int x, int y) {
        int mr = 0;
        for (int i = 0; i < keyb.rows.size(); i++) {
            Keyboard.Row row = keyb.rows.get(i);
            if (row.height + mr >= y) {
                int mk = 0;
                for (int j = 0; j < row.keys.size(); j++) {
                    Keyboard.Key k = row.keys.get(j);
                    if (k.width + mk >= x) return k;
                    mk += k.width;
                }
                break;
            }
            mr += row.height;
        }
        return null;
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
            invalidate();
        }
    }

    private void resetMultiTap() {
    }
}