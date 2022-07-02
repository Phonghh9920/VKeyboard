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
    public interface OnKeyboardActionListener {
        void onPress(int primaryCode);
        void onRelease(int primaryCode);
        void onKey(int primaryCode, int[] keyCodes);
        void onText(CharSequence text);
        void swipeLeft();
        void swipeRight();
        void swipeDown();
        void swipeUp();
    }
    private static final int NOT_A_KEY = -1;
    private static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };
    private Keyboard keyb;
    private PopupWindow mPopupKeyboard;
    private boolean keybOnScreen;
    private Map<Key,View> mMiniKeyboardCache;
    private Key[] keys;
    private OnKeyboardActionListener keybActionListener;
    private static final int MSG_REPEAT = 3;
    private static final int MSG_LONGPRESS = 4;
    private static final int DEBOUNCE_TIME = 70;
    private int proximityThreshold;
    private int mLastX;
    private int mLastY;
    private Paint mPaint;
    private Rect mPadding;
    private long downTime;
    private long lastMoveTime;
    private int lastKey;
    private int lastCodeX;
    private int lastCodeY;
    private int currentKey = NOT_A_KEY;
    private int downKey = NOT_A_KEY;
    private long lastKeyTime;
    private long currentKeyTime;
    private int[] keyIndices = new int[12];
    private GestureDetector gestureDetector;
    private int repeatKeyIndex = NOT_A_KEY;
    private boolean abortKey;
    private int mOldPointerCount = 1;
    private float mOldPointerX;
    private float mOldPointerY;
    private Drawable mKeyBackground;
    private static final int REPEAT_INTERVAL = 50;
    private static final int REPEAT_START_DELAY = 400;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static int MAX_NEARBY_KEYS = 12;
    private int[] mDistances = new int[MAX_NEARBY_KEYS];
    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;
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
        mKeyBackground = context.getDrawable(R.drawable.keyboard_bg_color);
        mPopupKeyboard = new PopupWindow(context);
        mPopupKeyboard.setBackgroundDrawable(null);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(keyTextSize);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAlpha(255);
        mPadding = new Rect(0, 0, 0, 0);
        mMiniKeyboardCache = new HashMap<Key,View>();
        mKeyBackground.getPadding(mPadding);
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
    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        keybActionListener = listener;
    }

    protected OnKeyboardActionListener getOnKeyboardActionListener() {
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
        mMiniKeyboardCache.clear();
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
    private int getKeyIndices(int x, int y, int[] allKeys) {
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
                // Find insertion point
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }
                if (allKeys == null) continue;
                for (int j = 0; j < mDistances.length; j++) {
                    if (mDistances[j] > dist) {
                        // Make space for nCodes codes
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
            // Multi-tap
            if (mInMultiTap) {
                if (mTapCount != -1) {
                    keybActionListener.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE);
                } else {
                    mTapCount = 0;
                }
                code = key.codes[mTapCount];
            }
            keybActionListener.onKey(code, codes);
            keybActionListener.onRelease(code);
        }
        mLastSentIndex = index;
        mLastTapTime = eventTime;
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
        boolean result = false;
        final long now = me.getEventTime();
        if (pointerCount != mOldPointerCount) {
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
                        mOldPointerX, mOldPointerY, me.getMetaState());
                result = onModifiedTouchEvent(up, true);
                up.recycle();
            }
        } else {
            if (pointerCount == 1) {
                result = onModifiedTouchEvent(me, false);
                mOldPointerX = me.getX();
                mOldPointerY = me.getY();
            } else {
                result = true;
            }
        }
        mOldPointerCount = pointerCount;
        return result;
    }
    private boolean onModifiedTouchEvent(MotionEvent me, boolean possiblePoly) {
        int touchX = (int) me.getX();
        int touchY = (int) me.getY();
        final int action = me.getAction();
        final long eventTime = me.getEventTime();
        int keyIndex = getKeyIndices(touchX, touchY, null);
        // Ignore all motion events until a DOWN.
        if (abortKey
                && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
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
                downKey = keyIndex;
                downTime = me.getEventTime();
                lastMoveTime = downTime;
                checkMultiTap(eventTime, keyIndex);
                keybActionListener.onPress(keyIndex != NOT_A_KEY ?
                        keys[keyIndex].codes[0] : 0);
                if (currentKey >= 0 && keys[currentKey].repeatable) {
                    repeatKeyIndex = currentKey;
                    Message msg = handler.obtainMessage(MSG_REPEAT);
                    handler.sendMessageDelayed(msg, REPEAT_START_DELAY);
                    repeatKey();
                    // Delivering the key could have caused an abort
                    if (abortKey) {
                        repeatKeyIndex = NOT_A_KEY;
                        break;
                    }
                }
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
                            lastCodeX = mLastX;
                            lastCodeY = mLastY;
                            lastKeyTime =
                                    currentKeyTime + eventTime - lastMoveTime;
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
        mLastX = touchX;
        mLastY = touchY;
        return true;
    }

    private boolean repeatKey() {
        if (repeatKeyIndex == -1) return false;
        Key key = keys[repeatKeyIndex];
        detectAndSendKey(currentKey, key.x, key.y, mLastTapTime);
        return true;
    }
    public void closing() {
        removeMessages();
        dismissPopupKeyboard();
        mMiniKeyboardCache.clear();
    }
    private void removeMessages() {
    }
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }
    private void dismissPopupKeyboard() {
        if (false && mPopupKeyboard.isShowing()) {
            mPopupKeyboard.dismiss();
            keybOnScreen = false;
            invalidateAllKeys();
        }
    }
    private void resetMultiTap() {
        mLastSentIndex = NOT_A_KEY;
        mTapCount = 0;
        mLastTapTime = -1;
        mInMultiTap = false;
    }
    private void checkMultiTap(long eventTime, int keyIndex) {
        if (keyIndex == NOT_A_KEY) return;
        Key key = keys[keyIndex];
        if (key.codes.length > 1) {
            mInMultiTap = true;
            if (eventTime < mLastTapTime + MULTITAP_INTERVAL
                    && keyIndex == mLastSentIndex) {
                mTapCount = (mTapCount + 1) % key.codes.length;
                return;
            } else {
                mTapCount = -1;
                return;
            }
        }
        if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
            resetMultiTap();
        }
    }
    private static class SwipeTracker {
        static final int NUM_PAST = 4;
        static final int LONGEST_PAST_TIME = 200;
        final float mPastX[] = new float[NUM_PAST];
        final float mPastY[] = new float[NUM_PAST];
        final long mPastTime[] = new long[NUM_PAST];
        float mYVelocity;
        float mXVelocity;
        public void clear() {
            mPastTime[0] = 0;
        }
        public void addMovement(MotionEvent ev) {
            long time = ev.getEventTime();
            final int N = ev.getHistorySize();
            for (int i=0; i<N; i++) {
                addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i),
                        ev.getHistoricalEventTime(i));
            }
            addPoint(ev.getX(), ev.getY(), time);
        }
        private void addPoint(float x, float y, long time) {
            int drop = -1;
            int i;
            final long[] pastTime = mPastTime;
            for (i=0; i<NUM_PAST; i++) {
                if (pastTime[i] == 0) {
                    break;
                } else if (pastTime[i] < time-LONGEST_PAST_TIME) {
                    drop = i;
                }
            }
            if (i == NUM_PAST && drop < 0) {
                drop = 0;
            }
            if (drop == i) drop--;
            final float[] pastX = mPastX;
            final float[] pastY = mPastY;
            if (drop >= 0) {
                final int start = drop+1;
                final int count = NUM_PAST-drop-1;
                System.arraycopy(pastX, start, pastX, 0, count);
                System.arraycopy(pastY, start, pastY, 0, count);
                System.arraycopy(pastTime, start, pastTime, 0, count);
                i -= (drop+1);
            }
            pastX[i] = x;
            pastY[i] = y;
            pastTime[i] = time;
            i++;
            if (i < NUM_PAST) {
                pastTime[i] = 0;
            }
        }
        public void computeCurrentVelocity(int units) {
            computeCurrentVelocity(units, Float.MAX_VALUE);
        }
        public void computeCurrentVelocity(int units, float maxVelocity) {
            final float[] pastX = mPastX;
            final float[] pastY = mPastY;
            final long[] pastTime = mPastTime;
            final float oldestX = pastX[0];
            final float oldestY = pastY[0];
            final long oldestTime = pastTime[0];
            float accumX = 0;
            float accumY = 0;
            int N=0;
            while (N < NUM_PAST) {
                if (pastTime[N] == 0) {
                    break;
                }
                N++;
            }
            for (int i=1; i < N; i++) {
                final int dur = (int)(pastTime[i] - oldestTime);
                if (dur == 0) continue;
                float dist = pastX[i] - oldestX;
                float vel = (dist/dur) * units;   // pixels/frame.
                if (accumX == 0) accumX = vel;
                else accumX = (accumX + vel) * .5f;
                dist = pastY[i] - oldestY;
                vel = (dist/dur) * units;   // pixels/frame.
                if (accumY == 0) accumY = vel;
                else accumY = (accumY + vel) * .5f;
            }
            mXVelocity = accumX < 0.0f ? Math.max(accumX, -maxVelocity)
                    : Math.min(accumX, maxVelocity);
            mYVelocity = accumY < 0.0f ? Math.max(accumY, -maxVelocity)
                    : Math.min(accumY, maxVelocity);
        }
        public float getXVelocity() {
            return mXVelocity;
        }
        public float getYVelocity() {
            return mYVelocity;
        }
    }
}