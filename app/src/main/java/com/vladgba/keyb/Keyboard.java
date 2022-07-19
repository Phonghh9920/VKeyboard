package com.vladgba.keyb;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class Keyboard {
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
    private static final float SEARCH_DISTANCE = 1.8f;
    private int dWidth;
    private int dHeight;
    public boolean shifted;
    private int totalHeight;
    private int totalWidth;
    private final List<Key> keys;
    private final int displayWidth;
    private final int displayHeight;
    private int cellWidth;
    private int cellHeight;
    private int[][] gridNeighbors;
    private int proximityThreshold;
    private final ArrayList<Row> rows = new ArrayList<>();

    private int loadx;
    private int loady;
    private Row loadcurrentRow;

    public static class Row {
        public int defaultWidth;
        public int defaultHeight;
        ArrayList<Key> keys = new ArrayList<>();

        public Row(Keyboard parent) {
            defaultWidth = parent.dWidth;
            defaultHeight = parent.dHeight;
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
        public boolean repeat = false;
        public CharSequence text;
        public CharSequence extChars;
        public int forward;
        public boolean cursor;
        public boolean modifier;
        public int popupResId;
        public int pos;

        public Key(Row parent) {
            height = parent.defaultHeight;
            width = parent.defaultWidth;
        }

        public Key(Row parent, int x, int y, JSONObject jdata, int pos) {
            this(parent);
            this.x = x;
            this.y = y;
            this.pos = pos;
            try {
                label = jdata.has("key") ? jdata.getString("key") : "";
                codes = new int[]{jdata.has("code") ? jdata.getInt("code") : 0};
                if (codes[0] == 0 && !TextUtils.isEmpty(label)) codes[0] = label.charAt(0);
                width = parent.defaultWidth * (jdata.has("size") ? jdata.getInt("size") : 1);
                Log.d("v", String.valueOf(width));
                extChars = jdata.has("ext") ? jdata.getString("ext") : "";
                if (extChars.length() > 0) extChars = padExtChars(extChars, pos);
                cursor = jdata.has("cur") && (jdata.getInt("cur") == 1);
                repeat = jdata.has("repeat") && (jdata.getInt("repeat") == 1);
                text = jdata.has("text") ? jdata.getString("text") : "";
                forward = jdata.has("forward") ? jdata.getInt("forward") : 0;
            } catch (JSONException e) {
                Log.d("Key", e.getMessage());
                return;
            }
            height = parent.defaultWidth;

            iconPreview = null;
            popupResId = 0;
            modifier = false;
            icon = null;
        }

        private CharSequence padExtChars(CharSequence chars, int pos) {
            chars += "        ";
            switch (pos) {
                case 1:
                    return "    " + chars.subSequence(0, 1) + " " + chars.subSequence(1, 3);
                case 2:
                    return "   " + chars.subSequence(0, 5);
                case 3:
                    return "   " + chars.subSequence(0, 1) + " " + chars.subSequence(1, 3) + " ";
                case 4:
                    return " " + chars.subSequence(0, 2) + " " + chars.subSequence(2, 3) + " " + chars.subSequence(3, 5);

                case 6:
                    return chars.subSequence(0, 2) + " " + chars.subSequence(2, 3) + " " + chars.subSequence(3, 5) + " ";
                case 7:
                    return " " + chars.subSequence(0, 2) + " " + chars.subSequence(2, 3) + "   ";
                case 8:
                    return chars.subSequence(0, 5) + "   ";
                case 9:
                    return chars.subSequence(0, 2) + " " + chars.subSequence(2, 3) + "    ";

                default:
                case 5:
                    return chars.subSequence(0, 8);
            }
        }

        public boolean isInside(int x, int y) {
            return x >= this.x && (x < this.x + this.width) && y >= this.y && (y < this.y + this.height);
        }

        public int squaredDistanceFrom(int x, int y) {
            int xDist = this.x + width / 2 - x;
            int yDist = this.y + height / 2 - y;
            return xDist * xDist + yDist * yDist;
        }
    }

    public Keyboard(@NotNull Context context, String jd, boolean portrait) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        displayWidth = dm.widthPixels;
        displayHeight = dm.heightPixels;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (portrait) {
            float size = Float.parseFloat(sp.getString("size", "10"));
            int lowerSize = Math.min(displayWidth, displayHeight);
            dWidth = (int) (lowerSize / size);
            dHeight = dWidth;
        } else {
            float size = Float.parseFloat(sp.getString("sizeland", "20"));
            int biggerSize = displayWidth > displayHeight ? displayWidth : displayHeight;
            dWidth = (int) Math.ceil(biggerSize / size);
            dHeight = dWidth;
        }
        keys = new ArrayList<>();
        loadKeyboard(jd);
    }

    final void resize(int newWidth, int newHeight) {
        int numRows = rows.size();
        for (int rowIndex = 0; rowIndex < numRows; ++rowIndex) {
            Row row = rows.get(rowIndex);
            int numKeys = row.keys.size();
            int totalWidth = 0;
            for (int keyIndex = 0; keyIndex < numKeys; ++keyIndex) {
                Key key = row.keys.get(keyIndex);
                totalWidth += key.width;
            }
            if (totalWidth > newWidth) {
                int x = 0;
                float scaleFactor = (float) newWidth / totalWidth;
                for (int keyIndex = 0; keyIndex < numKeys; ++keyIndex) {
                    Key key = row.keys.get(keyIndex);
                    key.width *= scaleFactor;
                    key.x = x;
                    x += key.width;
                }
            }
        }
        totalWidth = newWidth;
    }

    public List<Key> getKeys() {
        return keys;
    }

    public int getHeight() {
        return totalHeight;
    }

    public int getMinWidth() {
        return totalWidth;
    }

    public void setShifted(boolean shiftState) {
        shifted = shiftState;
    }

    private void computeNearestNeighbors() {
        cellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        cellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        gridNeighbors = new int[GRID_SIZE][];
        int[] indices = new int[keys.size()];
        final int gridWidth = GRID_WIDTH * cellWidth;
        final int gridHeight = GRID_HEIGHT * cellHeight;
        for (int x = 0; x < gridWidth; x += cellWidth) {
            for (int y = 0; y < gridHeight; y += cellHeight) {
                int count = 0;
                for (int i = 0; i < keys.size(); i++) {
                    final Key key = keys.get(i);
                    if (key.squaredDistanceFrom(x, y) < proximityThreshold ||
                            key.squaredDistanceFrom(x + cellWidth - 1, y) < proximityThreshold ||
                            key.squaredDistanceFrom(x + cellWidth - 1, y + cellHeight - 1)
                                    < proximityThreshold ||
                            key.squaredDistanceFrom(x, y + cellHeight - 1) < proximityThreshold) {
                        indices[count++] = i;
                    }
                }
                int[] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                gridNeighbors[(y / cellHeight) * GRID_WIDTH + (x / cellWidth)] = cell;
            }
        }
    }

    public int[] getNearestKeys(int x, int y) {
        if (gridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / cellHeight) * GRID_WIDTH + (x / cellWidth);
            if (index < GRID_SIZE) {
                return gridNeighbors[index];
            }
        }
        return new int[0];
    }

    private void loadKeyboard(String jd) {
        Log.d("json", jd);
        loadx = 0;
        loady = 0;
        try {
            JSONArray json = (new JSONObject(jd)).getJSONArray("keyb");
            parseKeyboardAttributes();

            for (int i = 0; i < json.length(); i++) {
                int pos = i == 0 ? 0 : i == json.length() - 1 ? 6 : 3;
                loadRow(json.getJSONArray(i), pos);
            }

            totalHeight = loady;
        } catch (JSONException e) {
            Log.d("PSR", e.getMessage());
        }
    }

    private void loadKey(JSONObject jdata, int pos) {
        Key loadkey = new Key(loadcurrentRow, loadx, loady, jdata, pos);
        if (loadkey.codes == null) return;
        keys.add(loadkey);
        loadcurrentRow.keys.add(loadkey);

        loadx += loadkey.width;
        if (loadx > totalWidth) totalWidth = loadx;
    }

    private void loadRow(JSONArray row, int pos) throws JSONException {
        loadx = 0;
        loadcurrentRow = new Row(this);
        rows.add(loadcurrentRow);
        for (int i = 0; i < row.length(); i++) {
            int keypos = pos + (i == 0 ? 1 : (i == row.length() - 1 ? 3 : 2));
            loadKey(row.getJSONObject(i), keypos);
        }
        loady += loadcurrentRow.defaultHeight;
    }

    private void parseKeyboardAttributes() {
        dWidth = Math.min(displayWidth, dHeight);
        dHeight = dWidth;
        proximityThreshold = (int) (dWidth * SEARCH_DISTANCE);
        proximityThreshold = proximityThreshold * proximityThreshold;
    }
}