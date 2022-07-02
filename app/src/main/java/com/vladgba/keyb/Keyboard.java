package com.vladgba.keyb;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/*
789
456
123
 */
public class Keyboard {
    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT = -6;
    private int mDefaultWidth;
    private int mDefaultHeight;
    private boolean mShifted;
    private Key[] mShiftKeys = {null, null};
    private int[] mShiftKeyIndices = {-1, -1};

    private int mTotalHeight;

    private int mTotalWidth;

    private List<Key> mKeys;

    private List<Key> mModifierKeys;

    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mKeyboardMode;

    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;
    private static float SEARCH_DISTANCE = 1.8f;
    private ArrayList<Row> rows = new ArrayList<Row>();

    private int loadx;
    private int loady;
    private int loadrow;
    private Resources res;
    private Row loadcurrentRow;

    private boolean loadskipRow;


    private boolean loadinKey;
    private boolean loadinRow;
    private Key loadkey;

    public static class Row {
        public int defaultWidth;
        public int defaultHeight;
        ArrayList<Key> mKeys = new ArrayList<Key>();

        public int mode;

        private Keyboard parent;

        public Row() {
            defaultWidth = parent.mDefaultWidth;
            defaultHeight = parent.mDefaultHeight;
            mode = 0;
        }
        public Row(Keyboard parent) {
            this.parent = parent;
        }

        public Row(Resources res, Keyboard parent) {
            this.parent = parent;
            defaultWidth = parent.mDefaultWidth;
            defaultHeight = parent.mDefaultHeight;
            mode = 0;
        }

        public Row(Keyboard parent, JSONArray json) {
            this.parent = parent;
            defaultWidth = parent.mDefaultWidth;
            defaultHeight = parent.mDefaultHeight;
            mode = 0;
        }
    }

    public static class Key {
        public int[] codes;
        public CharSequence label;
        public Drawable icon;
        public Drawable iconPreview;
        public int width;
        public int height;
        public int x;
        public int y;
        public boolean pressed;
        public boolean on;
        public CharSequence text;
        public CharSequence popupCharacters;
        public boolean cursor;
        public boolean modifier;
        private Keyboard keyboard;
        public int popupResId;
        public int pos;
        public boolean repeatable;

        private final static int[] KEY_STATE_NORMAL = {
        };

        private final static int[] KEY_STATE_PRESSED = {
                android.R.attr.state_pressed
        };

        public Key(Row parent) {
            keyboard = parent.parent;
            height = parent.defaultHeight;
            width = parent.defaultWidth;
        }

        public Key(Resources res, Row parent, int x, int y, JSONObject jdata, int pos) {
            this(parent);
            this.x = x;
            this.y = y;
            this.pos = pos;
            try {
                label = jdata.has("key") ? jdata.getString("key") : "";
                codes = new int[] {jdata.has("code") ? jdata.getInt("code") : 0};
                if(codes[0] == 0 && !TextUtils.isEmpty(label)) codes[0] = label.charAt(0);
                width = parent.defaultWidth * (jdata.has("size") ? jdata.getInt("size") : 1);
                Log.d("v", String.valueOf(width));
                popupCharacters = jdata.has("ext") ? jdata.getString("ext") : "";
                if (popupCharacters.length() > 0) popupCharacters = padExtChars(popupCharacters, pos);
                cursor = jdata.has("cur") && (jdata.getInt("cur") == 1);
                repeatable = jdata.has("repeat") && (jdata.getInt("repeat") == 1);
                text = jdata.has("text") ? jdata.getString("text") : "";
            } catch (JSONException e) {
                Log.d("Key", e.getMessage());
                return;
            }
            height = parent.defaultWidth;

            iconPreview = null;
            popupResId = 0;
            modifier = false;
            icon = null;
            text = null;

        }

        private CharSequence padExtChars(CharSequence chars, int pos) {
            chars += "        ";
            switch (pos) {
                case 1:
                    return "    " + chars.subSequence(0,1) + " " + chars.subSequence(1,3);
                case 2:
                    return "   " + chars.subSequence(0,5);
                case 3:
                    return "   " + chars.subSequence(0,1) + " " + chars.subSequence(1,3) + " ";
                case 4:
                    return " " + chars.subSequence(0,2) + " " + chars.subSequence(2,3) + " " + chars.subSequence(3,5);

                case 6:
                    return chars.subSequence(0,2) + " " + chars.subSequence(2,3) + " " + chars.subSequence(3,5) + " ";
                case 7:
                    return " " + chars.subSequence(0,2) + " " + chars.subSequence(2,3) + "   ";
                case 8:
                    return chars.subSequence(0,5) + "   ";
                case 9:
                    return chars.subSequence(0,2) + " " + chars.subSequence(2,3) + "    ";

                default:
                case 5:
                    return chars.subSequence(0,8);
            }
        }

        public Key(Row parent, int x, int y, int width, int height, CharSequence label, CharSequence popupCharacters, int[] codes, boolean repeatable, boolean modifier) {
            this(parent);
            this.x = x;
            this.y = y;
            Log.d("zxcx", String.valueOf(width));
            Log.d("zxc", String.valueOf(height));
            this.width = width > 0 ? width : parent.defaultWidth;
            this.height = height > 0 ? height : parent.defaultHeight;
            Log.d("zxc2", String.valueOf(this.height));
            this.codes = codes == null && !TextUtils.isEmpty(label) ? new int[]{label.charAt(0)} : codes;

            iconPreview = null;
            this.popupCharacters = popupCharacters;
            popupResId = 0;
            this.repeatable = repeatable;
            this.modifier = modifier;
            icon = null;
            this.label = label;
            text = null;
        }


        public void onPressed() {
            pressed = true;
        }

        public void onReleased(boolean inside) {
            pressed = false;
        }

        public boolean isInside(int x, int y) {
            if (x >= this.x && (x < this.x + this.width) && y >= this.y && (y < this.y + this.height)) {
                return true;
            } else {
                return false;
            }
        }

        public int squaredDistanceFrom(int x, int y) {
            int xDist = this.x + width / 2 - x;
            int yDist = this.y + height / 2 - y;
            return xDist * xDist + yDist * yDist;
        }

        public int[] getCurrentDrawableState() {
            return pressed ? KEY_STATE_PRESSED : KEY_STATE_NORMAL;
        }
    }

    public Keyboard(Context context, String jd) {
        this(context, jd, 0);
    }

    public Keyboard(@NotNull Context context, String jd, int modeId) { // start orig
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDisplayWidth = dm.widthPixels;
        mDisplayHeight = dm.heightPixels;

        int lowerSize = mDisplayWidth > mDisplayHeight ? mDisplayHeight : mDisplayWidth;
        mDefaultWidth = lowerSize / 10;
        mDefaultHeight = mDefaultWidth;
        mKeys = new ArrayList<Key>();
        mModifierKeys = new ArrayList<Key>();
        mKeyboardMode = modeId;
        loadKeyboard(context, jd);
    }

    final void resize(int newWidth, int newHeight) {
        int numRows = rows.size();
        for (int rowIndex = 0; rowIndex < numRows; ++rowIndex) {
            Row row = rows.get(rowIndex);
            int numKeys = row.mKeys.size();
            int totalWidth = 0;
            for (int keyIndex = 0; keyIndex < numKeys; ++keyIndex) {
                Key key = row.mKeys.get(keyIndex);
                totalWidth += key.width;
            }
            if (totalWidth > newWidth) {
                int x = 0;
                float scaleFactor = (float) newWidth / totalWidth;
                for (int keyIndex = 0; keyIndex < numKeys; ++keyIndex) {
                    Key key = row.mKeys.get(keyIndex);
                    key.width *= scaleFactor;
                    key.x = x;
                    x += key.width;
                }
            }
        }
        mTotalWidth = newWidth;
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    public int getHeight() {
        return mTotalHeight;
    }

    public int getMinWidth() {
        return mTotalWidth;
    }

    public boolean setShifted(boolean shiftState) {
        for (Key shiftKey : mShiftKeys) {
            if (shiftKey != null) {
                shiftKey.on = shiftState;
            }
        }
        if (mShifted != shiftState) {
            mShifted = shiftState;
            return true;
        }
        return false;
    }

    private void computeNearestNeighbors() {
        mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        mGridNeighbors = new int[GRID_SIZE][];
        int[] indices = new int[mKeys.size()];
        final int gridWidth = GRID_WIDTH * mCellWidth;
        final int gridHeight = GRID_HEIGHT * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                    < mProximityThreshold ||
                            key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold) {
                        indices[count++] = i;
                    }
                }
                int[] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
            }
        }
    }

    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
            if (index < GRID_SIZE) {
                return mGridNeighbors[index];
            }
        }
        return new int[0];
    }

    protected Row createRowFromXml(Resources res) {
        return new Row(res, this);
    }

    protected Key createKey(Resources res, Row parent, int x, int y, JSONObject jdata, int pos) {
        return new Key(res, parent, x, y, jdata, pos);
    }

    private void loadKeyboard(Context context, String jd) {
        Log.d("json", jd);
        loadrow = 0;
        loadx = 0;
        loady = 0;
        loadkey = null;
        res = context.getResources();
        loadskipRow = false;
        try {
            JSONArray json = (new JSONObject(jd)).getJSONArray("keyb");

            parseKeyboardAttributes();

            for(int i = 0; i < json.length(); i++) {
                int pos = i == 0 ? 0 : i == json.length() -1 ? 6 : 3;
                blopRow(json.getJSONArray(i), pos);
            }

            mTotalHeight = loady;
        } catch (JSONException e) {
            Log.d("PSR", e.getMessage());
        }
    }

    private void blopKey(JSONObject jdata, int pos) {
        loadinKey = true;
        loadkey = createKey(res, loadcurrentRow, loadx, loady, jdata, pos);
        if (loadkey.codes == null) return;
        mKeys.add(loadkey);
        if (loadkey.codes[0] == KEYCODE_SHIFT) {
            for (int i = 0; i < mShiftKeys.length; i++) {
                if (mShiftKeys[i] == null) {
                    mShiftKeys[i] = loadkey;
                    mShiftKeyIndices[i] = mKeys.size() - 1;
                    break;
                }
            }
            mModifierKeys.add(loadkey);
        } else if (loadkey.codes[0] == KEYCODE_ALT) {
            mModifierKeys.add(loadkey);
        }
        loadcurrentRow.mKeys.add(loadkey);

        loadinKey = false;
        loadx += loadkey.width;
        if (loadx > mTotalWidth) {
            mTotalWidth = loadx;
        }
    }

    private void blopRow(JSONArray row, int pos) throws JSONException {
        loadinRow = true;
        loadx = 0;
        loadcurrentRow = createRowFromXml(res);
        rows.add(loadcurrentRow);
        for(int i = 0; i < row.length(); i++) {
            int keypos = pos + (i == 0 ? 1 : (i == row.length() -1 ? 3 : 2));
            Log.d("json",row.toString());
            blopKey(row.getJSONObject(i), keypos);
        }
        loadinRow = false;
        loady += loadcurrentRow.defaultHeight;
        loadrow++;
    }

    /*public Keyboard(Context context, String s) {
        try {
            JSONObject json = new JSONObject(s);
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            mDisplayWidth = dm.widthPixels;
            mDisplayHeight = dm.heightPixels;

            int lowerSize = mDisplayWidth > mDisplayHeight ? mDisplayHeight : mDisplayWidth;
            mDefaultWidth = lowerSize / 10;
            mDefaultHeight = mDefaultWidth;
            mKeys = new ArrayList<Key>();
            mModifierKeys = new ArrayList<Key>();
            mKeyboardMode = 0;
            loadKeyboard(context, json);
        } catch (JSONException e) {
            Log.d("KeybLoad", e.getMessage());
        }
    }*/

    public Keyboard(@NotNull Context context, JSONObject json) throws JSONException { //new start
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDisplayWidth = dm.widthPixels;
        mDisplayHeight = dm.heightPixels;

        int lowerSize = mDisplayWidth > mDisplayHeight ? mDisplayHeight : mDisplayWidth;
        mDefaultWidth = lowerSize / 10;
        mDefaultHeight = mDefaultWidth;
        mKeys = new ArrayList<Key>();
        mModifierKeys = new ArrayList<Key>();
        mKeyboardMode = 0;
        loadKeyboard(context, json);
    }

    private void loadKeyboard(Context context, @NotNull JSONObject json) throws JSONException {
        JSONArray arr = json.getJSONArray("keyb");
        int size = arr.length();

        loadrow = 0;
        loadx = 0;
        loady = 0;

        for (int i = 0; i < size; i++) {
            loadRow(context, arr.getJSONArray(i));
        }
    }

    private void loadRow(Context context, JSONArray row) throws JSONException {
        int size = row.length();
        loadx = 0;
        Row currentRow = new Row(this);
        rows.add(currentRow);

        for (int i = 0; i < size; i++) {
            loadKey(context, row.getJSONObject(i), currentRow);
        }

        loady += currentRow.defaultHeight;
        loadrow++;
    }

    private void loadKey(Context context, @NotNull JSONObject key, Row row) {
        int[] codes = new int[]{0};
        try {
            codes = new int[]{key.getInt("codes")};
        } catch (JSONException e) {}

        CharSequence label = "";
        try {
            label = key.getString("key");
        } catch (JSONException e) {}

        CharSequence popupCharacters = "";
        try {
            popupCharacters = key.getString("ext");
        } catch (JSONException e) {}

        boolean repeatable = false;
        boolean modifier = false;
        Key newkey = new Key(row, loadx, loady, 108, 108, label, popupCharacters, codes, repeatable, modifier);
        mKeys.add(newkey);
        if (newkey.codes[0] == KEYCODE_SHIFT) {
            for (int i = 0; i < mShiftKeys.length; i++) {
                if (mShiftKeys[i] == null) {
                    mShiftKeys[i] = newkey;
                    mShiftKeyIndices[i] = mKeys.size()-1;
                    break;
                }
            }
            mModifierKeys.add(newkey);
        } else if (newkey.codes[0] == KEYCODE_ALT) {
            mModifierKeys.add(newkey);
        }
        row.mKeys.add(newkey);

        loadx += newkey.width;
        if (loadx > mTotalWidth) mTotalWidth = loadx;
    }

    private void parseKeyboardAttributes() {
        mDefaultWidth = (mDisplayWidth > mDefaultHeight ? mDefaultHeight : mDisplayWidth);
        mDefaultHeight = mDefaultWidth;
        mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
        mProximityThreshold = mProximityThreshold * mProximityThreshold;
    }
}