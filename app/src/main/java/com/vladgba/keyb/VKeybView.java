package com.vladgba.keyb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.*;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
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
    final int[] angPos = new int[]{4, 1, 2, 3, 5, 8, 7, 6, 4};


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
        try {
            //JSONObject json = new JSONObject(VKeyboard.loadKeybLayout("settings"));
            //delTick = json.getInt("del");
            SharedPreferences sp = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
            delTick = Integer.parseInt(sp.getString("swipedel", "60"));
            primaryFont = Float.parseFloat(sp.getString("sizeprimary", "1.9"));
            secondaryFont = Float.parseFloat(sp.getString("sizesecondary", "4.5"));
            horizontalTick = Integer.parseInt(sp.getString("swipehor", "30"));
            verticalTick = Integer.parseInt(sp.getString("swipever", "50"));
            offset = Integer.parseInt(sp.getString("swipeext", "70"));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        paint.setColor(getColor(R.color.keyboardBackground));
        RectF r = new RectF(0, 0, w, h);
        canvas.drawRect(r, paint);

        List<Key> keys = getKeyboard().getKeys();
        for (Key key : keys) {
            canvas.save();

            // Positions for subsymbols
            int x1 = key.width / 5;
            int x2 = key.width / 2;
            int x3 = key.width - x1;

            int y1 = key.height / 5;
            int y2 = key.height / 2;
            int y3 = key.height - y1;

            Rect rect = new Rect(
                    key.x,
                    key.y,
                    key.x + key.width,
                    key.y + key.height
            );

            canvas.clipRect(rect);

            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(getColor(R.color.keyBorder));

            int bi = key.height / 16;
            int ki = key.height / 6;
            int pd = key.height / 36;

            RectF recty = new RectF(key.x + bi - pd / 3, key.y + bi, key.x + key.width - bi + pd / 3, key.y + key.height - bi + pd);
            canvas.drawRoundRect(recty, ki, ki, paint);

            paint.setColor(getColor(R.color.keyBackground));

            recty = new RectF(key.x + bi, key.y + bi, key.x + key.width - bi, key.y + key.height - bi);
            canvas.drawRoundRect(recty, ki, ki, paint);

            paint.setTextSize(key.height / secondaryFont);
            paint.setColor(getColor(R.color.primaryText));

            viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, false);

            paint.setColor(getColor(R.color.primaryText));
            paint.setTextSize(key.height / primaryFont);

            if (key.label != null) {
                String lbl = sh && key.label.length() < 2 ? String.valueOf(VKeyboard.getShiftable(key.label.charAt(0), true)) : key.label.toString();
                canvas.drawText(
                        lbl,
                        key.x + (key.width / 2f),
                        key.y + (key.height + paint.getTextSize() - paint.descent()) / 2,
                        paint);
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
        /*Canvas c = new Canvas(buffer);
        Paint p = new Paint();
        p.setColor(0xffff0000);
        c.drawCircle(pressX, pressY, 2, p);*/
        canvas.drawBitmap(getKeyboard().shifted ? bufferSh : buffer, 0, 0, null);
        if (pressed) drawKey(canvas);
    }

    private void drawKey(Canvas canvas) {
        canvas.save();
        Key key = currentKey;
        if (key.cursor) return;
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(key.height / primaryFont);
        paint.setColor(getColor(R.color.previewText));

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

        int pd = key.height / 36;
        paint.setColor(getColor(R.color.keyBorder));
        RectF recty = new RectF(key.x + x1 * 2, key.y + y1 * 2, key.x - x1 + x3, key.y - y1 + y3);
        canvas.drawRoundRect(recty, 30, 30, paint);

        paint.setColor(getColor(R.color.keyBackground));
        recty = new RectF(key.x + x1 * 2 + pd / 3, key.y + y1 * 2, key.x - x1 + x3 - pd / 3, key.y - y1 + y3 - pd);
        canvas.drawRoundRect(recty, 30, 30, paint);

        paint.setColor(getColor(R.color.primaryText));
        boolean sh = getKeyboard().shifted;

        viewExtChars(key, canvas, paint, sh, x1, x2, x3, y1, y2, y3, true);

        canvas.restore();
    }

    private void viewExtChars(Key key, Canvas cv, Paint p, boolean sh, int x1, int x2, int x3, int y1, int y2, int y3, boolean h) {
        if (key.extChars == null) return;
        String str = String.valueOf(key.extChars);
        final int[] xi = new int[]{x1, x2, x3, x1, x3, x1, x2, x3};
        final int[] yi = new int[]{y1, y1, y1, y2, y2, y3, y3, y3};
        for (int i = 0; i < 8; i++) {
            p.setColor(getColor(R.color.previewSelected));
            if (h) {
                if (charPos == i + 1) cv.drawCircle(key.x + xi[i], key.y + yi[i], 50, p);
                p.setColor(getColor(R.color.previewText));
            } else {
                p.setColor(getColor(R.color.secondaryText));
            }
            viewChar(str, i, xi[i], yi[i], cv, key, p, sh);
        }
        if (h) {
            if (charPos == 0) {
                p.setColor(getColor(R.color.previewSelected));
                cv.drawCircle(key.x + x2, key.y + y2, 60, p);
            }
            p.setColor(getColor(R.color.previewText));
            cv.drawText(
                    sh && key.label.length() < 2 ? String.valueOf(VKeyboard.getShiftable(key.label.charAt(0), true)) : key.label.toString(),
                    key.x + x2,
                    key.y + y2 + (p.getTextSize() - p.descent()) / 2,
                    p
            );
        }
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
        int x = (int) me.getX(0);
        int y = (int) me.getY(0);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                press(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                drag(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                release(x, y);
                ctrlModi = false;
                shiftModi = false;
                break;
        }
        invalidate();
        return true;
    }

    public void press(int curX, int curY) {
        pressX = curX;
        pressY = curY;
        relX = -1;
        relY = -1;
        cursorMoved = false;
        charPos = 0;

        currentKey = getKey(curX, curY);
        Log.d("keyIndex", String.valueOf(currentKey));
        if (currentKey == null) return;
        pressed = true;
        if (currentKey.cursor || currentKey.repeat || currentKey.extChars.length() > 0) {
            //if (currentKey.extChars.length() > 0) pressed = true;
            relX = curX;
            relY = curY;
        }
    }

    private void drag(int curX, int curY) {
        if (relX < 0) return; // Not have alternative behavior

        if (currentKey.repeat) {
            if (!cursorMoved && (curX - delTick > pressX || curX + delTick < pressX)) {
                cursorMoved = true;
            }
            while (true) {
                if (curX - delTick > relX) {
                    relX += delTick;
                    super.getOnKeyboardActionListener().onKey(currentKey.forward == 0 ? currentKey.codes[0] : currentKey.forward);
                    continue;
                }

                if (curX + delTick < relX) {
                    relX -= delTick;
                    super.getOnKeyboardActionListener().onKey(currentKey.backward == 0 ? currentKey.codes[0] : currentKey.backward);
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

    private void release(int curX, int curY) {
        Log.d("keyIndexRelease", String.valueOf(currentKey));
        if (!pressed) return;
        if (currentKey == null) return;
        pressed = false;
        if (curY == 0) return;
        if (currentKey.cursor && cursorMoved) return;

        Log.d("keyIndexRelease", String.valueOf(currentKey));
        if (currentKey.lang != null && charPos == 0) {
            VKeyboard.currentLayout = String.valueOf(currentKey.lang);
            getOnKeyboardActionListener().onKey(0);
            return;
        }
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
                char textIndex = currentKey.extChars.charAt(charPos - 1);
                if (textIndex == ' ') return;
                createCustomKeyEvent(String.valueOf(textIndex));
                return;
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
