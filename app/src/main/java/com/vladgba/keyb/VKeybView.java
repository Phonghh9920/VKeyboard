package com.vladgba.keyb;

import java.util.List;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.*;


public class VKeybView extends KeyboardView {
    private Drawable mKeybgDrawable;
    private Drawable mOpKeybgDrawable;
    private Resources res;
    private Context context;

    private int savedX = 0;
    private int savedY = 0;
    private int charPos = 0;
    private boolean keyWait = false;

    private int relx = 0;
    private int rely = 0;

    /** Cursor **/
    private int horizontalTick = 30;
    private int verticalTick = 50;
    private boolean cursorMoved = false;
    private int offset = 70;

    public VKeybView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initResources(context);
    }

    public VKeybView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initResources(context);
    }

    private void initResources(Context context) {
        this.context = context;
        res = context.getResources();
        mKeybgDrawable = res.getDrawable(R.drawable.btn_keyboard_key);
        mOpKeybgDrawable = res.getDrawable(R.drawable.btn_keyboard_opkey);
    }

    @Override
    public void onDraw(Canvas canvas) {
        List<Key> keys = getKeyboard().getKeys();
        for (Key key : keys) {
            canvas.save();

            // Positions for subsymbols
            int x1 = key.width / 6;
            int x2 = key.width / 2;
            int x3 = key.width - x1;

            int y1 = key.height / 6;
            int y2 = key.height / 2;
            int y3 = key.height - y1;

            int initdrawy = key.y;
            Rect rect = new Rect(
                    key.x + 1,
                    key.y + 1,
                    key.x + key.width - 2,
                    key.y + key.height - 2
            );

            canvas.clipRect(rect);

            int primaryCode = -1;
            if (null != key.codes && key.codes.length != 0) primaryCode = key.codes[0];

            Drawable dr = primaryCode < 0 || key.codes[0] < 0 ? mOpKeybgDrawable : mKeybgDrawable;

            if (null != dr) {
                int[] state = key.getCurrentDrawableState();
                dr.setState(state);
                dr.setBounds(rect);
                dr.draw(canvas);
            }

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize((float) (key.height/1.9));
            paint.setColor(res.getColor(R.color.textColor));

            if (key.label != null) {
                String lbl = VKeyboard.shiftPressed && key.label.length() < 2 ? String.valueOf(VKeyboard.getShiftable(key.label.charAt(0))) : key.label.toString();
                canvas.drawText(
                        lbl,
                        key.x + (key.width / 2),
                        key.y + (key.height + paint.getTextSize() - paint.descent()) / 2,
                        paint);
            }

            paint.setTextSize((float) (key.height/4.5));
            paint.setColor(res.getColor(R.color.textColor));

            if (key.popupCharacters != null) {
                String str = String.valueOf(key.popupCharacters);
                viewChar(str, 0, x1, y1, canvas, key, paint);
                viewChar(str, 1, x2, y1, canvas, key, paint);
                viewChar(str, 2, x3, y1, canvas, key, paint);

                viewChar(str, 3, x1, y2, canvas, key, paint);
                viewChar(str, 4, x3, y2, canvas, key, paint);

                viewChar(str, 5, x1, y3, canvas, key, paint);
                viewChar(str, 6, x2, y3, canvas, key, paint);
                viewChar(str, 7, x3, y3, canvas, key, paint);
            }
            canvas.restore();
        }
    }

    private void viewChar(String str, int pos, int ox, int oy, Canvas canvas, Key key, Paint paint) {
        if (str.length() <= pos || str.charAt(0) == 'H' || str.charAt(pos) == 'H' || str.charAt(pos) == ' ') return;
        canvas.drawText(
                String.valueOf(str.charAt(pos)),
                key.x + ox,
                key.y + oy + (paint.getTextSize() - paint.descent()) / 2,
                paint
        );
    }

    private int getKeyIndices(int x, int y, int[] allKeys) {
        int MAX_NEARBY_KEYS = 12;
        int[] mDistances = new int[MAX_NEARBY_KEYS];

        final Key[] keys = getKeyboard().getKeys().toArray(new Key[0]);
        int primaryIndex = -1;
        int closestKey = -1;
        int closestKeyDist = 2;
        java.util.Arrays.fill(mDistances, Integer.MAX_VALUE);
        int [] nearestKeyIndices = getKeyboard().getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x,y);

            if (isInside) primaryIndex = nearestKeyIndices[i];

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
                        System.arraycopy(mDistances, j, mDistances, j + nCodes, mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes, allKeys.length - j - nCodes);
                        for (int c = 0; c < nCodes; c++) {
                            allKeys[j + c] = key.codes[c];
                            mDistances[j + c] = dist;
                        }
                        break;
                    }
                }
            }
        }
        return primaryIndex == -1 ? closestKey : primaryIndex;
    }

    private int translatePos(int i, int j) {
        return i - offset > j ? 1 : i + offset < j ? 3 : 2;
    }

    private int detectPos(int x, int y) {
        int matrixPos = translatePos(this.savedX, x) * 10 + translatePos(this.savedY, y);
        /*
        11 21 31
        12 22 32
        13 23 33
         */
        switch (matrixPos) {
            case 11: return 1;
            case 21: return 2;
            case 31: return 3;

            case 12: return 4;
            case 22: return 0; // too little movement
            case 32: return 5;

            case 13: return 6;
            case 23: return 7;
            case 33: return 8;
        }
        return 0;
    }
    private void handleMove(int curX, int curY) {
        if (!keyWait) return;
        charPos = detectPos(curX, curY);

        if (charPos == 0 || !cursorMoved) {
            createCustomTouch(MotionEvent.ACTION_DOWN, curX, curY);
            createCustomTouch(MotionEvent.ACTION_UP, curX, curY);
            return;
        }

        if(this.relx >= 0) return;

        int currentKeyIndex =  getKeyIndices(this.savedX, this.savedY, null);
        Key currentKey = getKeyboard().getKeys().get(currentKeyIndex);
        if (currentKey.popupCharacters == null) return;
        int popSz = currentKey.popupCharacters.length();
        if (popSz > 0 && popSz >= charPos) {
            String textIndex = String.valueOf(currentKey.popupCharacters.charAt(charPos - 1));
            if (textIndex == null || textIndex.charAt(0) == ' ' || textIndex.charAt(0) == 'H') return;
            createCustomKeyEvent(textIndex);
        }

    }

    private void handleCursor(int curX, int curY) {
        if (!cursorMoved && (curX - horizontalTick > savedX || curX + horizontalTick < savedX || curY - verticalTick > savedY || curY + verticalTick < savedY)) {
            cursorMoved = true;
        }

        if (this.relx < 0) return;

        while(true) { //Horizontal
            if(curY > 0 && curX - horizontalTick > this.relx) {
                this.relx += horizontalTick;
                super.getOnKeyboardActionListener().swipeRight();
                continue;
            }
            if(curY > 0 && curX + horizontalTick < this.relx) {
                this.relx -= horizontalTick;
                super.getOnKeyboardActionListener().swipeLeft();
                continue;
            }
            break;
        }

        while(true) { // Vertical
            if(curY - verticalTick > this.rely) {
                this.rely += verticalTick;
                super.getOnKeyboardActionListener().swipeDown();
                continue;
            }
            if(curY + verticalTick < this.rely) {
                this.rely -= horizontalTick;
                super.getOnKeyboardActionListener().swipeUp();
                continue;
            }
            break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        final int pointerCount = me.getPointerCount();
        final int action = me.getAction();
        boolean result = false;
        final long now = me.getEventTime();

        switch(action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                handleMove((int) me.getX(0), (int) me.getY(0));
                this.relx = -1;
                this.rely = -1;
                cursorMoved = false;
                break;
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                rememberPos((int) me.getX(0), (int) me.getY(0));
                cursorMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                handleCursor((int) me.getX(0), (int) me.getY(0));
                return false;
                //break;
            default:
                break;
        }
        if (!keyWait) return super.onTouchEvent(me);

        return true;
    }

    /**
     * Saves initial position of pointer
     */
    public void rememberPos(int curX, int curY) {
        int currentKeyIndex =  getKeyIndices(curX, curY, null);
        Key currentKey = getKeyboard().getKeys().get(currentKeyIndex);


        if (currentKey.popupCharacters == null) {
            keyWait = false;
        } else if (currentKey.popupCharacters.charAt(0) == 'H') {
            keyWait = true;
            this.relx = curX;
            this.rely = curY;
        } else {
            this.relx = -1;
            this.rely = -1;
            keyWait = true;
            this.savedX = curX;
            this.savedY = curY;
        }

    }

    public int[] getFromString(CharSequence str) {
        if (str.length() > 1) {
            return new int[] { str.charAt(0), str.charAt(1) }; // FIXME: Is it fix 2 length?
        } else {
            return new int[] { str.charAt(0) };
        }
    }

    public void customClick(String ch) {
        createCustomKeyEvent(ch);
    }

    private void createCustomKeyEvent(String data) {
        super.getOnKeyboardActionListener().onKey(getFromString(data)[0], getFromString(data));
    }

    private void createCustomTouch(int action, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        int metaState = 0;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, metaState);
        super.onTouchEvent(event);
    }
}
