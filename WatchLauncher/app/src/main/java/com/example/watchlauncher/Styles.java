package com.example.watchlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Theme / background presets and the saved launcher settings. */
final class Styles {
    private Styles() {}

    static final int GLOW_ICON = 0;   // glow takes the colour of the centred icon
    static final int GLOW_FIXED = 1;  // glow is one fixed accent colour
    static final int GLOW_OFF = 2;

    // Icon shapes. NONE = leave the icon exactly as Android draws it ("Original").
    static final int SHAPE_CIRCLE = 0, SHAPE_SQUIRCLE = 1, SHAPE_ROUND = 2, SHAPE_NONE = 3;

    static class Theme {
        final String name;
        final int swatchTop, swatchBottom;
        final boolean gloss, gray, rimSolid;
        final int rimColor, disc, glowMode, glowColor, pillColor;
        final int shape, tint;      // tint: colour multiplied over a grey icon (0 = none)
        final float inset;          // padding of legacy icons on the disc
        final boolean visor;        // crewmate visor overlay

        Theme(String name, int swatchTop, int swatchBottom, boolean gloss, boolean gray,
              boolean rimSolid, int rimColor, int disc, int glowMode, int glowColor,
              int pillColor) {
            this(name, swatchTop, swatchBottom, gloss, gray, rimSolid, rimColor, disc, glowMode,
                    glowColor, pillColor, SHAPE_CIRCLE, 0.16f, 0, false);
        }

        Theme(String name, int swatchTop, int swatchBottom, boolean gloss, boolean gray,
              boolean rimSolid, int rimColor, int disc, int glowMode, int glowColor,
              int pillColor, int shape, float inset, int tint, boolean visor) {
            this.shape = shape;
            this.inset = inset;
            this.tint = tint;
            this.visor = visor;
            this.name = name;
            this.swatchTop = swatchTop;
            this.swatchBottom = swatchBottom;
            this.gloss = gloss;
            this.gray = gray;
            this.rimSolid = rimSolid;
            this.rimColor = rimColor;
            this.disc = disc;
            this.glowMode = glowMode;
            this.glowColor = glowColor;
            this.pillColor = pillColor;
        }
    }

    static class Bg {
        final String name;
        final int center, edge;

        Bg(String name, int center, int edge) {
            this.name = name;
            this.center = center;
            this.edge = edge;
        }
    }

    static final Theme[] THEMES = {
            new Theme("Classic", 0xFF0A84FF, 0xFFFF375F, true, false, false,
                    0x70FFFFFF, 0xFF2C2C2E, GLOW_ICON, 0, 0xFF1C1C1E),
            new Theme("Ocean", 0xFF64D2FF, 0xFF0A3A8A, true, false, false,
                    0xAA7FD6FF, 0xFF12263F, GLOW_FIXED, 0xFF2D8CFF, 0xFF0B1E3A),
            new Theme("Sunset", 0xFFFFD60A, 0xFFFF375F, true, false, false,
                    0xAAFFB27A, 0xFF35201A, GLOW_FIXED, 0xFFFF6A3D, 0xFF3A1410),
            new Theme("Neon", 0xFF39FFB6, 0xFF0A1F18, false, false, true,
                    0xFF39FFB6, 0xFF0C1A15, GLOW_FIXED, 0xFF39FFB6, 0xFF08140F),
            new Theme("Candy", 0xFFFF9FD2, 0xFFB28DFF, true, false, false,
                    0xAAFFC2E2, 0xFF3A2A40, GLOW_FIXED, 0xFFFF7ACB, 0xFF2A1630),
            new Theme("Mono", 0xFFEDEDED, 0xFF3A3A3C, false, true, false,
                    0x60FFFFFF, 0xFF2C2C2E, GLOW_OFF, 0, 0xFF1C1C1E),
            // Flat: plain circles, no rim, no gloss, no glow.
            new Theme("Flat", 0xFF8E8E93, 0xFF636366, false, false, false,
                    0x00000000, 0xFF2C2C2E, GLOW_OFF, 0, 0xFF1C1C1E,
                    SHAPE_CIRCLE, 0.16f, 0, false),
            // iOS: squircle icons with a hairline rim.
            new Theme("iOS", 0xFF5AC8FA, 0xFF007AFF, false, false, false,
                    0x38FFFFFF, 0xFF2C2C2E, GLOW_ICON, 0, 0xFF1C1C1E,
                    SHAPE_SQUIRCLE, 0.14f, 0, false),
            // Samsung: rounded squares, clean and rimless.
            new Theme("Samsung", 0xFF4A6CF7, 0xFF1428A0, false, false, false,
                    0x00000000, 0xFF202124, GLOW_ICON, 0, 0xFF16181C,
                    SHAPE_ROUND, 0.15f, 0, false),
            // Original: icons exactly as the system draws them.
            new Theme("Original", 0xFF3DDC84, 0xFF073042, false, false, false,
                    0x00000000, 0xFF000000, GLOW_ICON, 0, 0xFF1C1C1E,
                    SHAPE_NONE, 0f, 0, false),
            // Ultra Sigma: gold-on-black chrome.
            new Theme("Ultra Sigma", 0xFFFFE08A, 0xFF8A5A00, true, true, true,
                    0xFFFFC83D, 0xFF0A0A0A, GLOW_FIXED, 0xFFFFC83D, 0xFF14110A,
                    SHAPE_CIRCLE, 0.16f, 0xFFFFD36B, false),
            // Among Us: crewmate-red discs with a cyan visor. Kinda sus.
            new Theme("Among Us", 0xFFFF4040, 0xFF7A0808, true, false, true,
                    0xFF6B0A0A, 0xFFC51111, GLOW_FIXED, 0xFFFF3B3B, 0xFF2A0808,
                    SHAPE_CIRCLE, 0.16f, 0, true),
    };

    static final Bg[] BGS = {
            new Bg("Black", 0xFF000000, 0xFF000000),
            new Bg("Midnight", 0xFF10264D, 0xFF000000),
            new Bg("Aurora", 0xFF0B4A4A, 0xFF1B0B3D),
            new Bg("Graphite", 0xFF2A2A2E, 0xFF050506),
            new Bg("Ember", 0xFF3A1208, 0xFF000000),
            new Bg("Violet", 0xFF2A0F55, 0xFF000000),
    };

    static final int DEFAULT_THEME = 0;
    static final int DEFAULT_BOUNCE = 40;
    static final int DEFAULT_BG = 0;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int getTheme(Context c) {
        return clamp(p(c).getInt("theme", DEFAULT_THEME), 0, THEMES.length - 1);
    }

    static int getBg(Context c) {
        return clamp(p(c).getInt("bg", DEFAULT_BG), 0, BGS.length - 1);
    }

    static int getBounce(Context c) {
        return clamp(p(c).getInt("bounce", DEFAULT_BOUNCE), 0, 100);
    }

    static void setTheme(Context c, int v) {
        p(c).edit().putInt("theme", v).apply();
    }

    static void setBg(Context c, int v) {
        p(c).edit().putInt("bg", v).apply();
    }

    static void setBounce(Context c, int v) {
        p(c).edit().putInt("bounce", v).apply();
    }

    // ---- extra options -----------------------------------------------------

    private static int geti(Context c, String k, int def, int lo, int hi) {
        return clamp(p(c).getInt(k, def), lo, hi);
    }

    static int getIconSize(Context c) { return geti(c, "iconsize", 50, 0, 100); }
    static int getLens(Context c) { return geti(c, "lens", 50, 0, 100); }
    static int getDim(Context c) { return geti(c, "dim", 35, 0, 90); }
    static boolean getGlow(Context c) { return p(c).getBoolean("glow", true); }
    static boolean getLabels(Context c) { return p(c).getBoolean("labels", true); }
    static boolean getHaptics(Context c) { return p(c).getBoolean("haptics", true); }
    static boolean getUseImage(Context c) { return p(c).getBoolean("useimage", false); }

    static void setIconSize(Context c, int v) { p(c).edit().putInt("iconsize", v).apply(); }
    static void setLens(Context c, int v) { p(c).edit().putInt("lens", v).apply(); }
    static void setDim(Context c, int v) { p(c).edit().putInt("dim", v).apply(); }
    static void setGlow(Context c, boolean v) { p(c).edit().putBoolean("glow", v).apply(); }
    static void setLabels(Context c, boolean v) { p(c).edit().putBoolean("labels", v).apply(); }
    static void setHaptics(Context c, boolean v) { p(c).edit().putBoolean("haptics", v).apply(); }
    static void setUseImage(Context c, boolean v) { p(c).edit().putBoolean("useimage", v).apply(); }

    /** Icon size slider (0..100) -> grid scale 70%..130%. */
    static float sizeScale(Context c) { return 0.7f + getIconSize(c) * 0.006f; }

    /** Lens slider (0..100) -> fisheye strength 0.4..2.8 (default 1.6). */
    static float lensK(Context c) { return 0.4f + getLens(c) * 0.024f; }

    /** Resets the look and options; hidden apps, folders and the saved image file are kept. */
    static void reset(Context c) {
        p(c).edit().remove("theme").remove("bounce").remove("bg")
                .remove("iconsize").remove("lens").remove("dim").remove("glow")
                .remove("labels").remove("haptics").remove("useimage").apply();
    }

    // ---- hidden apps -------------------------------------------------------

    static Set<String> getHidden(Context c) {
        return new HashSet<>(p(c).getStringSet("hidden", new HashSet<String>()));
    }

    static void hide(Context c, String key) {
        Set<String> s = getHidden(c);
        s.add(key);
        p(c).edit().putStringSet("hidden", s).apply();
    }

    static void unhideAll(Context c) {
        p(c).edit().remove("hidden").apply();
    }

    // ---- folders -----------------------------------------------------------

    static class Folder {
        final String name;
        final List<String> apps = new ArrayList<>();

        Folder(String name) {
            this.name = name;
        }
    }

    static List<Folder> getFolders(Context c) {
        List<Folder> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(p(c).getString("folders", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Folder f = new Folder(o.getString("name"));
                JSONArray a = o.getJSONArray("apps");
                for (int j = 0; j < a.length(); j++) f.apps.add(a.getString(j));
                out.add(f);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void saveFolders(Context c, List<Folder> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Folder f : list) {
                JSONObject o = new JSONObject();
                o.put("name", f.name);
                JSONArray a = new JSONArray();
                for (String s : f.apps) a.put(s);
                o.put("apps", a);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        p(c).edit().putString("folders", arr.toString()).apply();
    }

    private static void removeKey(List<Folder> list, String key) {
        for (int i = list.size() - 1; i >= 0; i--) {
            list.get(i).apps.remove(key);
            if (list.get(i).apps.isEmpty()) list.remove(i);
        }
    }

    private static Folder find(List<Folder> list, String name) {
        for (Folder f : list) if (f.name.equals(name)) return f;
        return null;
    }

    /** Put an app in a brand-new folder ("Folder 1", "Folder 2", ...). Returns its name. */
    static String addToNewFolder(Context c, String key) {
        List<Folder> list = getFolders(c);
        removeKey(list, key);
        int n = 1;
        while (find(list, "Folder " + n) != null) n++;
        Folder f = new Folder("Folder " + n);
        f.apps.add(key);
        list.add(f);
        saveFolders(c, list);
        return f.name;
    }

    static void addToFolder(Context c, String folderName, String key) {
        List<Folder> list = getFolders(c);
        removeKey(list, key);
        Folder f = find(list, folderName);
        if (f == null) {
            f = new Folder(folderName);
            list.add(f);
        }
        f.apps.add(key);
        saveFolders(c, list);
    }

    static void removeFromFolder(Context c, String key) {
        List<Folder> list = getFolders(c);
        removeKey(list, key);
        saveFolders(c, list);
    }

    static void dissolveFolder(Context c, String folderName) {
        List<Folder> list = getFolders(c);
        Folder f = find(list, folderName);
        if (f != null) list.remove(f);
        saveFolders(c, list);
    }

    static Theme theme(Context c) {
        return THEMES[getTheme(c)];
    }

    static Bg bg(Context c) {
        return BGS[getBg(c)];
    }
}
