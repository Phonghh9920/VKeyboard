package com.vladgba.keyb;

import android.content.Context;
import android.content.SharedPreferences;
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
    public final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<Key> keys;
    public boolean shifted;
    private final int dWidth;
    private final int dHeight;
    private int totalHeight;
    private int totalWidth;
    private int loadx;
    private int loady;
    private Row loadcurrentRow;
    public Keyboard(@NotNull Context context, String jd, boolean portrait) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int displayWidth = dm.widthPixels;
        int displayHeight = dm.heightPixels;

        SharedPreferences sp = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
        if (portrait) {
            float size = Float.parseFloat(sp.getString("size", "10"));
            int lowerSize = Math.min(displayWidth, displayHeight);
            dWidth = (int) (lowerSize / size);
            dHeight = dWidth;
        } else {
            float size = Float.parseFloat(sp.getString("sizeland", "20"));
            int biggerSize = Math.max(displayWidth, displayHeight);
            dWidth = (int) Math.ceil(biggerSize / size);
            dHeight = dWidth;
        }
        keys = new ArrayList<>();
        loadKeyboard(jd);
    }

    final void resize(int newWidth) {
        for (Row row : rows) {
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

    private void loadKeyboard(String jd) {
        Log.d("json", jd);
        loadx = 0;
        loady = 0;
        try {
            JSONArray json = (new JSONObject(jd)).getJSONArray("keyb");

            for (int i = 0; i < json.length(); i++) {
                int pos = i == 0 ? 0 : i == json.length() - 1 ? 6 : 3;
                loadRow(json.getJSONArray(i), pos);
            }

            totalHeight = loady;
        } catch (JSONException e) {
            Log.e("PSR", e.getMessage());
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
        loady += loadcurrentRow.height;
    }

    public static class Row {
        public int width;
        public int height;
        ArrayList<Key> keys = new ArrayList<>();

        public Row(Keyboard parent) {
            width = parent.dWidth;
            height = parent.dHeight;
        }
    }

    public static class Key {
        public int[] codes;
        public CharSequence label;
        public int width;
        public int height;
        public int x;
        public int y;
        public boolean repeat = false;
        public CharSequence text;
        public CharSequence lang;
        public CharSequence extChars;
        public int forward;
        public int backward;
        public boolean cursor;
        public String[] rand;

        public Key(Row parent) {
            height = parent.height;
            width = parent.width;
        }

        public Key(Row parent, int x, int y, JSONObject jdata, int pos) {
            this(parent);
            this.x = x;
            this.y = y;
            try {
                label = jdata.has("key") ? jdata.getString("key") : "";
                codes = new int[]{jdata.has("code") ? jdata.getInt("code") : 0};
                if (codes[0] == 0 && !TextUtils.isEmpty(label)) codes[0] = label.charAt(0);
                width = parent.width * (jdata.has("size") ? jdata.getInt("size") : 1);
                extChars = jdata.has("ext") ? jdata.getString("ext") : "";
                if (extChars.length() > 0) extChars = padExtChars(extChars, pos);
                cursor = jdata.has("cur") && (jdata.getInt("cur") == 1);
                repeat = jdata.has("repeat") && (jdata.getInt("repeat") == 1);
                text = jdata.has("text") ? jdata.getString("text") : "";
                lang = jdata.has("lang") ? jdata.getString("lang") : null;
                forward = jdata.has("forward") ? jdata.getInt("forward") : 0;
                backward = jdata.has("backward") ? jdata.getInt("backward") : 0;
                JSONArray rands = jdata.has("rand") ? jdata.getJSONArray("rand") : null;
                if (rands == null) {
                    rand = null;
                } else {
                    rand = new String[rands.length()];
                    for (int i = 0; i < rands.length(); i++) {
                        rand[i] = rands.getString(i);
                    }
                }
            } catch (JSONException e) {
                Log.d("Key", e.getMessage());
                return;
            }
            height = parent.width;
        }

        private CharSequence padExtChars(CharSequence chars, int pos) {
            int[][] modes = new int[][] {
                    {-4, 1, -1, 2}, {-3, 5}, {-3, 1, -1, 2},
                    {-1, 2, -1, 1, 2}, {8}, {2, -1, 1, -1, 2},
                    {-1, 2, -1, 1}, {5}, {2, -1, 1}
            };

            StringBuilder sb = new StringBuilder();
            int[] curv = modes[pos - 1];
            int p = 0;

            for (int i : curv) {
                if (p >= chars.length()) break;

                if (i > 0) sb.append(chars.subSequence(p, Math.min((p += i), chars.length())));
                else sb.append(new String(new char[-i]).replace("\0", " "));
            }
            return sb.toString();
        }
    }
}