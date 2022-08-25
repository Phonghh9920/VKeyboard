package com.vladgba.keyb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.*;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.vladgba.keyb.Keyboard.Key;

import java.util.List;


public class VKeybView extends KeyboardView {
    private Resources res;
    private int pressX = 0;
    private int pressY = 0;
    private int charPos = 0;
    private int relX = 0;
    private int relY = 0;

    /**
     * Cursor
     **/
    private int delTick;
    private float primaryFont;
    private float secondaryFont;
    private int horizontalTick;
    private int verticalTick;
    private int offset; // extChars
    private boolean cursorMoved = false;
    private Key currentKey;
    public boolean pressed = false;
    public Bitmap buffer;
    private Bitmap bufferSh;
    public boolean ctrlModi = false;
    public boolean shiftModi = false;
    final int[] angPos = new int[]{ 4, 1, 2, 3, 5, 8, 7, 6, 4 };


    public VKeybView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initResources(context);
    }

    public VKeybView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initResources(context);
    }

    private void initResources(Context context) {
        res = context.getResources();
        loadVars(context);
    }

    public void loadVars(Context context) {
        SharedPreferences sp = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        delTick = Integer.parseInt(sp.getString("swipedel", "60"));
        primaryFont = Float.parseFloat(sp.getString("sizeprimary", "1.9"));
        secondaryFont = Float.parseFloat(sp.getString("sizesecondary", "4.5"));
        horizontalTick = Integer.parseInt(sp.getString("swipehor", "30"));
        verticalTick = Integer.parseInt(sp.getString("swipever", "50"));
        offset = Integer.parseInt(sp.getString("swipeext", "70"));
    }

    @Override
    public void setKeyboard(Keyboard keyboard) {
        super.setKeyboard(keyboard);
        buffer = null;
    }

    private void repaintKeyb(int w, int h) {
        keybPaint(w, h, false);
        keybPaint(w, h, true);
    }

    private void keybPaint(int w, int h, boolean sh) {
        if (sh) bufferSh = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        else buffer = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(sh ? bufferSh : buffer);

        Paint paint = new Paint();
        paint.setColor(0xff000000);
        RectF r = new RectF(0, 0, w, h);
        canvas.drawRect(r, paint);

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

            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(key.height / primaryFont);
            paint.setColor(0xff484848);

            int bi = 3;
            RectF recty = new RectF(key.x + bi, key.y + bi, key.x + key.width - bi, key.y + key.height - bi);
            canvas.drawRoundRect(recty, 15, 15, paint);

            paint.setColor(0xff000000);

            bi = 4;
            recty = new RectF(key.x + bi, key.y + bi, key.x + key.width - bi, key.y + key.height - bi);
            canvas.drawRoundRect(recty, 15, 15, paint);

            paint.setColor(getColor(R.color.textColor));

            if (key.label != null) {
                String lbl = sh && key.label.length() < 2 ? String.valueOf(VKeyboard.getShiftable(key.label.charAt(0), true)) : key.label.toString();
                canvas.drawText(
                        lbl,
                        key.x + (key.width / 2f),
                        key.y + (key.height + paint.getTextSize() - paint.descent()) / 2,
                        paint);
            }

            paint.setTextSize(key.height / secondaryFont);
            paint.setColor(getColor(R.color.textColor));

            if (key.extChars != null) {
                String str = String.valueOf(key.extChars);
                viewChar(str, 0, x1, y1, canvas, key, paint, sh);
                viewChar(str, 1, x2, y1, canvas, key, paint, sh);
                viewChar(str, 2, x3, y1, canvas, key, paint, sh);

                viewChar(str, 3, x1, y2, canvas, key, paint, sh);
                viewChar(str, 4, x3, y2, canvas, key, paint, sh);

                viewChar(str, 5, x1, y3, canvas, key, paint, sh);
                viewChar(str, 6, x2, y3, canvas, key, paint, sh);
                viewChar(str, 7, x3, y3, canvas, key, paint, sh);
            }
            canvas.restore();
        }
    }

    private int getColor(int textColor) {
        //noinspection deprecation
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? res.getColor(textColor, getOnKeyboardActionListener().getTheme()) : res.getColor(textColor);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (buffer == null) repaintKeyb(getWidth(), getHeight());
        canvas.drawBitmap(getKeyboard().shifted ? bufferSh : buffer, 0, 0, null);
        if (pressed) drawKey(canvas);
    }

    private void drawKey(Canvas canvas) {
        canvas.save();
        Key key = currentKey;
        if (key.cursor) return;
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(key.height / primaryFont);
        paint.setColor(getColor(R.color.textColor));

        int x1 = key.width / 2 - key.width;
        int x2 = key.width / 2;
        int x3 = key.width * 2 - key.width / 2;

        int y1 = key.height / 2 - key.height;
        int y2 = key.height / 2;
        int y3 = key.height * 2 - key.height / 2;

        Rect rect = new Rect(
                key.x - key.width,
                key.y - key.height,
                key.x + key.width * 3,
                key.y + key.height * 2
        );

        canvas.clipRect(rect);

        paint.setColor(0xff696969);
        RectF recty = new RectF(key.x + x1 * 2, key.y + y1 * 2, key.x - x1 + x3, key.y - y1 + y3);
        canvas.drawRoundRect(recty, 30, 30, paint);

        paint.setColor(0xff000000);
        recty = new RectF(key.x + x1 * 2 + 1, key.y + y1 * 2 + 1, key.x - x1 + x3 - 1, key.y - y1 + y3 - 1);
        canvas.drawRoundRect(recty, 30, 30, paint);

        paint.setColor(0xff191919);
        recty = new RectF(key.x, key.y, key.x + key.width, key.y + key.height);
        canvas.drawRoundRect(recty, 15, 15, paint);

        paint.setColor(getColor(R.color.textColor));

        boolean sh = getKeyboard().shifted;
        viewChar(String.valueOf(key.label), 0, x2, y2, canvas, key, paint, sh);

        if (key.extChars != null) {
            String str = String.valueOf(key.extChars);
            viewChar(str, 0, x1, y1, canvas, key, paint, sh);
            viewChar(str, 1, x2, y1, canvas, key, paint, sh);
            viewChar(str, 2, x3, y1, canvas, key, paint, sh);

            viewChar(str, 3, x1, y2, canvas, key, paint, sh);
            viewChar(str, 4, x3, y2, canvas, key, paint, sh);

            viewChar(str, 5, x1, y3, canvas, key, paint, sh);
            viewChar(str, 6, x2, y3, canvas, key, paint, sh);
            viewChar(str, 7, x3, y3, canvas, key, paint, sh);
        }

        canvas.restore();
    }

    private void viewChar(String str, int pos, int ox, int oy, Canvas canvas, Key key, Paint paint, boolean sh) {
        if (str.length() <= pos || str.charAt(pos) == ' ') return;

        canvas.drawText(
                String.valueOf(VKeyboard.getShiftable(str.charAt(pos), sh)),
                key.x + ox,
                key.y + oy + (paint.getTextSize() - paint.descent()) / 2,
                paint
        );
    }

    private int getExtPos(int x, int y) {
        if (Math.abs(this.pressX - x) < this.offset && Math.abs(this.pressY - y) < this.offset) return 0;
        final double angle = Math.toDegrees(Math.atan2(this.pressY - y, this.pressX - x)); 
        return angPos[(int) Math.ceil(((angle < 0 ? 360d : 0d) + angle + 22.5d) / 45d) - 1];
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent me) {
        final int action = me.getAction();
        if (me.getPointerCount() > 1) return false;
        switch (action) {
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
                release();
                ctrlModi = false;
                shiftModi = false;
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

        int currentKeyIndex = getKeyIndices(curX, curY);
        if (currentKeyIndex == -1) return;
        currentKey = getKeyboard().getKeys().get(currentKeyIndex);

        if (currentKey.cursor || currentKey.repeat || currentKey.extChars.length() > 0) {
            if (currentKey.extChars.length() > 0) pressed = true;
            relX = curX;
            relY = curY;
        }
    }

    private void drag(int curX, int curY) {
        if (relX < 0) return; // Not have alternative behavior

        if (currentKey.repeat) { // Delete
            if (!cursorMoved && (curX - delTick > pressX || curX + delTick < pressX)) {
                cursorMoved = true;
            }
            while (true) {
                if (curX - delTick > relX) {
                    relX += delTick;
                    super.getOnKeyboardActionListener().click(currentKey.forward);
                    continue;
                }

                if (curX + delTick < relX) {
                    relX -= delTick;
                    super.getOnKeyboardActionListener().onKey(currentKey.codes[0]);
                    continue;
                }
                break;
            }
        } else if (currentKey.cursor) {
            if (!cursorMoved && (curX - horizontalTick > pressX || curX + horizontalTick < pressX || curY - verticalTick > pressY || curY + verticalTick < pressY)) {
                cursorMoved = true;
            }
            while (true) {
                if (curX - horizontalTick > this.relX) {
                    this.relX += horizontalTick;
                    super.getOnKeyboardActionListener().swipeRight();
                    continue;
                }
                if (curX + horizontalTick < this.relX) {
                    this.relX -= horizontalTick;
                    super.getOnKeyboardActionListener().swipeLeft();
                    continue;
                }
                if (curY - verticalTick > this.relY) {
                    this.relY += verticalTick;
                    super.getOnKeyboardActionListener().swipeDown();
                    continue;
                }
                if (curY + verticalTick < this.relY) {
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

    private void release() {
        pressed = false;
        if (currentKey.cursor && cursorMoved) return;
        if (currentKey.text.length() > 0) {
            keybActionListener.onText(currentKey.text);
            return;
        } else if (currentKey.repeat) {
            if (!cursorMoved) super.getOnKeyboardActionListener().onKey(currentKey.codes[0]);
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
                if (textIndex.charAt(0) != ' ') {
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
            for (int j = 0; j < str.length(); j++)
                out[j] = Character.getNumericValue(str.charAt(j));
            return out; // FIXME: Is it fixes >1 length?
        } else {
            return new int[]{str.charAt(0)};
        }
    }

    private void createCustomKeyEvent(String data) {
        super.getOnKeyboardActionListener().onKey(getFromString(data)[0]);
    }

    private void createCustomKeyEvent(int[] data) {
        super.getOnKeyboardActionListener().onKey(data[0]);
    }
}
