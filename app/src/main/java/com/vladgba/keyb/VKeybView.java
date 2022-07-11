package com.vladgba.keyb;

import java.util.List;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import com.vladgba.keyb.Keyboard.Key;


public class VKeybView extends KeyboardView {
    private Drawable keybgDrawable;
    private Drawable opkeybgDrawable;
    private Drawable curkeybgDrawable;
    private Resources res;
    private Context context;
    private int pressX = 0;
    private int pressY = 0;
    private int charPos = 0;
    private int relX = 0;
    private int relY = 0;

    /** Cursor **/
    private int delTick;
    private int horizontalTick;
    private int verticalTick;
    private int offset; // extChars
    private boolean cursorMoved = false;
    private int offset = 70; // extChars
    private Key currentKey;
    private boolean pressed = false;
    private boolean skip = false;

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
        keybgDrawable = res.getDrawable(R.drawable.btn_keyboard_key);
        opkeybgDrawable = res.getDrawable(R.drawable.btn_keyboard_opkey);
        curkeybgDrawable = res.getDrawable(R.drawable.btn_keyboard_curkey);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        delTick = Integer.parseInt(sp.getString("swipedel", "60"));
        horizontalTick = Integer.parseInt(sp.getString("swipehor", "30"));
        verticalTick = Integer.parseInt(sp.getString("swipever", "50"));
        offset = Integer.parseInt(sp.getString("swipeext", "70"));
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (pressed) {
            drawKey(canvas);
            return;
        }

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

            Rect rect = new Rect(
                    key.x + 1,
                    key.y + 1,
                    key.x + key.width - 2,
                    key.y + key.height - 2
            );

            canvas.clipRect(rect);

            int primaryCode = -1;
            if (null != key.codes && key.codes.length != 0) primaryCode = key.codes[0];

            Drawable dr = primaryCode < 0 || key.codes[0] < 0 ? opkeybgDrawable : keybgDrawable;
            if (key.cursor) dr = curkeybgDrawable;

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

            if (key.extChars != null) {
                String str = String.valueOf(key.extChars);
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

    private void drawKey(Canvas canvas) {
        canvas.save();
        Key key = currentKey;

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize((float) (key.height/2));
        paint.setColor(res.getColor(R.color.textColor));

        int x1 = key.width / 2 - key.width;
        int x2 = key.width / 2;
        int x3 = key.width * 2 - key.width / 2;

        int y1 = key.height / 2 - key.height;
        int y2 = key.height / 2;
        int y3 = key.height * 2 - key.height / 2;

        Rect rect = new Rect(
                key.x - key.width,
                key.y - key.height,
                key.x + key.width * 2,
                key.y + key.height * 2
        );

        canvas.clipRect(rect);

        paint.setColor(res.getColor(R.color.keyboard_divider));
        canvas.drawRect(key.x + x1 * 2, key.y + y1 * 2, key.x + x3 * 2, key.y + y3 * 2, paint);
        paint.setColor(res.getColor(R.color.black));
        canvas.drawRect(key.x, key.y, key.x + key.width, key.y + key.height, paint);
        paint.setColor(res.getColor(R.color.textColor));
        viewChar(String.valueOf(key.label), 0, x2, y2, canvas, key, paint);

        if (key.extChars != null) {
            String str = String.valueOf(key.extChars);
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

    private void viewChar(String str, int pos, int ox, int oy, Canvas canvas, Key key, Paint paint) {
        if (str.length() <= pos || str.charAt(pos) == ' ') return;
        canvas.drawText(
                String.valueOf(VKeyboard.getShiftable(str.charAt(pos))),
                key.x + ox,
                key.y + oy + (paint.getTextSize() - paint.descent()) / 2,
                paint
        );
    }

    private int getExtPos(int x, int y) {
        int matrixPos = (this.pressX - offset > x ? 1 : this.pressX + offset < x ? 3 : 2);
        matrixPos += (this.pressY - offset > y ? 0 : this.pressY + offset < y ? 6 : 3);
        return matrixPos == 5 ? 0 : matrixPos < 5 ? matrixPos : matrixPos - 1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {
        final int action = me.getAction();
        if (me.getPointerCount() > 1) return false;
        switch(action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                press((int) me.getX(0), (int) me.getY(0));
                invalidateAllKeys();
                break;
            case MotionEvent.ACTION_MOVE:
                drag((int) me.getX(0), (int) me.getY(0));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (skip) return false;
                release((int) me.getX(0), (int) me.getY(0));
                invalidateAllKeys();
                break;
        }

        return true;
    }

    public void press(int curX, int curY) {
        pressX = curX;
        pressY = curY;
        relX = -1;
        relY = -1;
        cursorMoved = false;
        charPos = 0;

        int currentKeyIndex =  getKeyIndices(curX, curY, null);
        if (currentKeyIndex == -1) return;
        currentKey = getKeyboard().getKeys().get(currentKeyIndex);

        if (currentKey.cursor || currentKey.codes[0] == -5 || currentKey.extChars.length() > 0) {
            if (currentKey.extChars.length() > 0) pressed = true;
            relX = curX;
            relY = curY;
        }
    }

    private void drag(int curX, int curY) {
        if (relX < 0) return; // Not have alternative behavior

        if (currentKey.codes[0] == -5) { // Delete
            if (!cursorMoved && (curX - delTick > pressX || curX + delTick < pressX)) {
                Log.d("Moved","");
                cursorMoved = true;
            }
            while(true) {
                if (curX - delTick > relX) {
                    relX += delTick;
                    super.getOnKeyboardActionListener().click(112);
                    continue;
                }

                if(curX + delTick < relX) {
                    relX -= delTick;
                    super.getOnKeyboardActionListener().onKey(-5, new int[]{-5});
                    continue;
                }
                break;
            }
        } else if (currentKey.cursor) {
            if (!cursorMoved && (curX - horizontalTick > pressX || curX + horizontalTick < pressX || curY - verticalTick > pressY || curY + verticalTick < pressY)) {
                cursorMoved = true;
            }
            while(true) {
                if(curX - horizontalTick > this.relX) {
                    this.relX += horizontalTick;
                    super.getOnKeyboardActionListener().swipeRight();
                    continue;
                }
                if(curX + horizontalTick < this.relX) {
                    this.relX -= horizontalTick;
                    super.getOnKeyboardActionListener().swipeLeft();
                    continue;
                }
                if(curY - verticalTick > this.relY) {
                    this.relY += verticalTick;
                    super.getOnKeyboardActionListener().swipeDown();
                    continue;
                }
                if(curY + verticalTick < this.relY) {
                    this.relY -= horizontalTick;
                    super.getOnKeyboardActionListener().swipeUp();
                    continue;
                }
                break;
            }
        } else if (currentKey.extChars.length() > 0) {
            this.relX = curX;
            this.relY = curY;
            charPos = getExtPos(curX, curY);
        }
    }

    private void release(int curX, int curY) {
        pressed = false;
        if (currentKey.cursor && cursorMoved) return;
        if (currentKey.codes[0] == -5) {
            if (!cursorMoved) super.getOnKeyboardActionListener().onKey(-5, new int[]{-5});
            return;
        }
        if (this.relX < 0) {
            createCustomKeyEvent(currentKey.codes);
            return; // Not have alternative behavior
        }
        if (charPos != 0) {
            int extSz = currentKey.extChars.length();
            if (extSz > 0 && extSz >= charPos) {
                String textIndex = String.valueOf(currentKey.extChars.charAt(charPos - 1));
                if (textIndex != null && textIndex.charAt(0) != ' ') {
                    createCustomKeyEvent(textIndex);
                    return;
                }
            }
        }
        createCustomKeyEvent(currentKey.codes);
    }

    public int[] getFromString(CharSequence str) {
        if (str.length() > 1) {
            int[] out = new int[str.length()];
            for(int j = 0; j < str.length(); j++)
                out[j] = Character.getNumericValue(str.charAt(j));
            return out; // FIXME: Is it fixes >1 length?
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

    private void createCustomKeyEvent(int[] data) {
        super.getOnKeyboardActionListener().onKey(data[0], data);
    }

    private void createCustomTouch(int action, int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        int metaState = 0;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, metaState);
        super.onTouchEvent(event);
    }

    private void customTap(int x, int y) {
        createCustomTouch(MotionEvent.ACTION_DOWN, x, y);
        createCustomTouch(MotionEvent.ACTION_UP, x, y);
    }
}
