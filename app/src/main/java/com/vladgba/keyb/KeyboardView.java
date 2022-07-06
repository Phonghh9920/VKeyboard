package com.vladgba.keyb;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.vladgba.keyb.Keyboard.Key;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.PopupWindow;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardView extends View implements View.OnClickListener {
    private static final int NOT_A_KEY = -1;
    static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };
    private Keyboard keyb;
    private PopupWindow popupKeyboard;
    private boolean keybOnScreen;
    private Key[] keys;
    private VKeyboard keybActionListener;
    private static final int MSG_REPEAT = 3;
    private static final int MSG_LONGPRESS = 4;
    private static final int DEBOUNCE_TIME = 70;
    private int proximityThreshold;
    private int lastX;
    private int lastY;
    private Paint paint;
    private long downTime;
    private long lastMoveTime;
    private int lastKey;
    private int lastCodeX;
    private int lastCodeY;
    private int currentKey = NOT_A_KEY;
    private long lastKeyTime;
    private long currentKeyTime;
    private int[] keyIndices = new int[12];
    private GestureDetector gestureDetector;
    private int repeatKeyIndex = NOT_A_KEY;
    private boolean abortKey;
    private int oldPointerCount = 1;
    private float oldPointerX;
    private float oldPointerY;
    private Drawable keyBackground;
    private static final int REPEAT_INTERVAL = 50;
    private static final int REPEAT_START_DELAY = 400;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];
    private int lastSentIndex;
    private int tapCount;
    private long lastTapTime;
    private boolean inMultiTap;
    private static final int MULTITAP_INTERVAL = 800;
    Handler handler;

    public KeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        int keyTextSize = 0;
        keyBackground = context.getDrawable(R.drawable.keyboard_bg_color);
        popupKeyboard = new PopupWindow(context);
        popupKeyboard.setBackgroundDrawable(null);
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(keyTextSize);
        paint.setTextAlign(Align.CENTER);
        paint.setAlpha(255);
        keyBackground.getPadding(new Rect(0, 0, 0, 0));
        resetMultiTap();
    }
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        initGestureDetector();
        if (handler != null) return;
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_REPEAT:
                        if (repeatKey()) {
                            Message repeat = Message.obtain(this, MSG_REPEAT);
                            sendMessageDelayed(repeat, REPEAT_INTERVAL);
                        }
                        break;
                }
            }
        };
    }
    private void initGestureDetector() {
        if (gestureDetector == null) {
            gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
                    return false;
                }
            });
            gestureDetector.setIsLongpressEnabled(false);
        }
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
        this.keys = keys.toArray(new Key[keys.size()]);
        requestLayout();
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
        abortKey = true;
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
        int dimensionSum = 0;
        for (int i = 0; i < length; i++) {
            Key key = keys[i];
            dimensionSum += Math.min(key.width, key.height);
        }
        if (dimensionSum < 0 || length == 0) return;
        proximityThreshold = (int) (dimensionSum * 1.4f / length);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (keyb != null) keyb.resize(w, h);
    }
    int getKeyIndices(int x, int y, int[] allKeys) {
        final Key[] keys = this.keys;
        int primaryIndex = NOT_A_KEY;
        int closestKey = NOT_A_KEY;
        int closestKeyDist = proximityThreshold + 1;
        java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = keyb.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }
            if (isInside && key.codes[0] > 32) {
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }
                if (allKeys == null) continue;
                for (int j = 0; j < mDistances.length; j++) {
                    if (mDistances[j] > dist) {
                        System.arraycopy(mDistances, j, mDistances, j + nCodes,
                                mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - j - nCodes);
                        for (int c = 0; c < nCodes; c++) {
                            allKeys[j + c] = key.codes[c];
                            mDistances[j + c] = dist;
                        }
                        break;
                    }
                }
            }
        }
        return primaryIndex == NOT_A_KEY ? closestKey : primaryIndex;
    }
    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        if (index == NOT_A_KEY || index >= keys.length) return;
        final Key key = keys[index];
        if (key.text != null) {
            Log.d("Send", String.valueOf(key.text));
            keybActionListener.onText(key.text);
            keybActionListener.onRelease(NOT_A_KEY);
        } else {
            int code = key.codes[0];
            int[] codes = new int[MAX_NEARBY_KEYS];
            Arrays.fill(codes, NOT_A_KEY);
            getKeyIndices(x, y, codes);

            if (inMultiTap) {
                if (tapCount != -1) keybActionListener.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE);
                else tapCount = 0;
                code = key.codes[tapCount];
            }
            keybActionListener.onKey(code, codes);
            keybActionListener.onRelease(code);
        }
        lastSentIndex = index;
        lastTapTime = eventTime;
    }

    public void invalidateAllKeys() {
        invalidate();
    }

    public void invalidateKey(int keyIndex) {
        if (keys == null) return;
        if (keyIndex < 0 || keyIndex >= keys.length) {
            return;
        }
        final Key key = keys[keyIndex];

        invalidate(key.x, key.y,
                key.x + key.width, key.y + key.height);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return true;
    }
    @Override
    public boolean onTouchEvent(MotionEvent me) {
        final int pointerCount = me.getPointerCount();
        final int action = me.getAction();
        boolean result;
        final long now = me.getEventTime();
        if (pointerCount != oldPointerCount) {
            if (pointerCount == 1) {
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                        me.getX(), me.getY(), me.getMetaState());
                result = onModifiedTouchEvent(down, false);
                down.recycle();
                if (action == MotionEvent.ACTION_UP) {
                    result = onModifiedTouchEvent(me, true);
                }
            } else {
                MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                        oldPointerX, oldPointerY, me.getMetaState());
                result = onModifiedTouchEvent(up, true);
                up.recycle();
            }
        } else {
            if (pointerCount == 1) {
                result = onModifiedTouchEvent(me, false);
                oldPointerX = me.getX();
                oldPointerY = me.getY();
            } else {
                result = true;
            }
        }
        oldPointerCount = pointerCount;
        return result;
    }
    private boolean onModifiedTouchEvent(MotionEvent me, boolean possiblePoly) {
        int touchX = (int) me.getX();
        int touchY = (int) me.getY();
        final int action = me.getAction();
        final long eventTime = me.getEventTime();
        int keyIndex = getKeyIndices(touchX, touchY, null);
        // Ignore all motion events until a DOWN.
        if (abortKey && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }
        if (gestureDetector.onTouchEvent(me)) {
            handler.removeMessages(MSG_REPEAT);
            handler.removeMessages(MSG_LONGPRESS);
            return true;
        }

        if (keybOnScreen && action != MotionEvent.ACTION_CANCEL) return true;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                abortKey = false;
                lastCodeX = touchX;
                lastCodeY = touchY;
                lastKeyTime = 0;
                currentKeyTime = 0;
                lastKey = NOT_A_KEY;
                currentKey = keyIndex;
                downTime = me.getEventTime();
                lastMoveTime = downTime;
                checkMultiTap(eventTime, keyIndex);
                keybActionListener.onPress(keyIndex != NOT_A_KEY ?
                        keys[keyIndex].codes[0] : 0);
                if (currentKey != NOT_A_KEY) {
                    Message msg = handler.obtainMessage(MSG_LONGPRESS, me);
                    handler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                boolean continueLongPress = false;
                if (keyIndex != NOT_A_KEY) {
                    if (currentKey == NOT_A_KEY) {
                        currentKey = keyIndex;
                        currentKeyTime = eventTime - downTime;
                    } else {
                        if (keyIndex == currentKey) {
                            currentKeyTime += eventTime - lastMoveTime;
                            continueLongPress = true;
                        } else if (repeatKeyIndex == NOT_A_KEY) {
                            resetMultiTap();
                            lastKey = currentKey;
                            lastCodeX = lastX;
                            lastCodeY = lastY;
                            lastKeyTime = currentKeyTime + eventTime - lastMoveTime;
                            currentKey = keyIndex;
                            currentKeyTime = 0;
                        }
                    }
                }
                if (!continueLongPress) {
                    // Cancel old longpress
                    handler.removeMessages(MSG_LONGPRESS);
                    // Start new longpress if key has changed
                    if (keyIndex != NOT_A_KEY) {
                        Message msg = handler.obtainMessage(MSG_LONGPRESS, me);
                        handler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
                    }
                }
                lastMoveTime = eventTime;
                break;
            case MotionEvent.ACTION_UP:
                removeMessages();
                if (keyIndex == currentKey) {
                    currentKeyTime += eventTime - lastMoveTime;
                } else {
                    resetMultiTap();
                    lastKey = currentKey;
                    lastKeyTime = currentKeyTime + eventTime - lastMoveTime;
                    currentKey = keyIndex;
                    currentKeyTime = 0;
                }
                if (currentKeyTime < lastKeyTime && currentKeyTime < DEBOUNCE_TIME
                        && lastKey != NOT_A_KEY) {
                    currentKey = lastKey;
                    touchX = lastCodeX;
                    touchY = lastCodeY;
                }
                Arrays.fill(keyIndices, NOT_A_KEY);
                if (repeatKeyIndex == NOT_A_KEY && !keybOnScreen && !abortKey) {
                    detectAndSendKey(currentKey, touchX, touchY, eventTime);
                }
                invalidateKey(keyIndex);
                repeatKeyIndex = NOT_A_KEY;
                break;
            case MotionEvent.ACTION_CANCEL:
                removeMessages();
                dismissPopupKeyboard();
                abortKey = true;
                invalidateKey(currentKey);
                break;
        }
        lastX = touchX;
        lastY = touchY;
        return true;
    }

    private boolean repeatKey() {
        if (repeatKeyIndex == -1) return false;
        Key key = keys[repeatKeyIndex];
        detectAndSendKey(currentKey, key.x, key.y, lastTapTime);
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
        if (false && popupKeyboard.isShowing()) {
            popupKeyboard.dismiss();
            keybOnScreen = false;
            invalidateAllKeys();
        }
    }
    private void resetMultiTap() {
        lastSentIndex = NOT_A_KEY;
        tapCount = 0;
        lastTapTime = -1;
        inMultiTap = false;
    }
    private void checkMultiTap(long eventTime, int keyIndex) {
        if (keyIndex == NOT_A_KEY) return;
        Key key = keys[keyIndex];
        if (key.codes.length > 1) {
            inMultiTap = true;
            if (eventTime < lastTapTime + MULTITAP_INTERVAL && keyIndex == lastSentIndex) {
                tapCount = (tapCount + 1) % key.codes.length;
                return;
            } else {
                tapCount = -1;
                return;
            }
        }
        if (eventTime > lastTapTime + MULTITAP_INTERVAL || keyIndex != lastSentIndex) {
            resetMultiTap();
        }
    }
}