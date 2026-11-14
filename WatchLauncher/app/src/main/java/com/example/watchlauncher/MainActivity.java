package com.example.watchlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Path;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity implements HoneycombView.Listener {

    private static final int BUBBLE_PX = 128;

    private HoneycombView view;
    private final Map<String, Bitmap> bubbleCache = new HashMap<>();
    private final Map<String, Integer> glowCache = new HashMap<>();
    private Bitmap shapeMask, settingsBubble, backBubble;
    private int maskShape = -1;
    private int builtTheme = -1;
    private boolean returningFromSettings;
    private Bitmap bgBitmap;
    private long bgStamp = -1;
    private String openFolder; // name of the folder being shown, or null for the main grid

    private static final int ACT_INFO = 1, ACT_DELETE = 2, ACT_HIDE = 3, ACT_FOLDER = 4,
            ACT_PICK = 5, ACT_NEWFOLDER = 6, ACT_TAKEOUT = 7, ACT_OPEN = 8, ACT_DISSOLVE = 9;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new HoneycombView(this);
        view.setListener(this);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Theme changes affect how icons are rendered, so rebuild them.
        int themeId = Styles.getTheme(this);
        boolean themeChanged = themeId != builtTheme;
        if (themeChanged) {
            bubbleCache.clear();
            glowCache.clear();
            settingsBubble = null;
            backBubble = null;
            builtTheme = themeId;
        }
        view.applyStyle(Styles.theme(this), Styles.getBounce(this), Styles.bg(this));

        // Optional background picture (cached until the file changes).
        Bitmap img = null;
        File bgFile = BgImage.file(this);
        if (Styles.getUseImage(this) && bgFile.exists()) {
            if (bgBitmap == null || bgStamp != bgFile.lastModified()) {
                bgBitmap = BgImage.load(this);
                bgStamp = bgFile.lastModified();
            }
            img = bgBitmap;
        } else {
            bgBitmap = null;
        }
        view.applyOptions(Styles.sizeScale(this), Styles.lensK(this), Styles.getGlow(this),
                Styles.getLabels(this), Styles.getHaptics(this), img, Styles.getDim(this) / 100f);

        view.resetLaunch(); // coming back from an app we launched: replay the pop-in
        if (returningFromSettings) {
            returningFromSettings = false;
            view.playIntro(); // show off the new look / bounciness
        }
        loadApps();         // picks up newly installed / removed apps
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed: glide back to the centre and pop the icons in again.
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            view.dismissMenu();
            if (openFolder != null) {
                openFolder = null;
                view.jumpToCenter();
            } else {
                view.recenter();
            }
            view.playIntro();
        }
    }

    @Override
    public void onBackPressed() {
        // A launcher never exits on Back: close the popup / folder, or just recentre.
        if (view.isMenuShowing()) {
            view.dismissMenu();
        } else if (openFolder != null) {
            setOpenFolder(null);
        } else if (!view.isCentered()) {
            view.recenter();
        }
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);

        Set<String> hidden = Styles.getHidden(this);
        Map<String, AppEntry> all = new HashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            AppEntry a = new AppEntry();
            a.label = String.valueOf(ri.loadLabel(pm));
            a.pkg = ri.activityInfo.packageName;
            a.component = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);

            String key = a.key();
            if (hidden.contains(key)) continue;
            Bitmap b = bubbleCache.get(key);
            Integer g = glowCache.get(key);
            if (b == null || g == null) {
                b = makeBubble(ri.loadIcon(pm));
                g = dominantColor(b);
                bubbleCache.put(key, b);
                glowCache.put(key, g);
            }
            a.bubble = b;
            a.glow = g;
            all.put(key, a);
        }

        List<Styles.Folder> folders = Styles.getFolders(this);
        List<AppEntry> list = new ArrayList<>();

        if (openFolder != null) {
            // Inside a folder: show only its members.
            for (Styles.Folder f : folders) {
                if (!f.name.equals(openFolder)) continue;
                for (String key : f.apps) {
                    AppEntry a = all.get(key);
                    if (a != null) list.add(a);
                }
            }
            if (list.isEmpty()) openFolder = null; // folder vanished or is empty
        }

        if (openFolder == null) {
            Set<String> inFolder = new HashSet<>();
            for (Styles.Folder f : folders) {
                List<AppEntry> members = new ArrayList<>();
                for (String key : f.apps) {
                    AppEntry a = all.get(key);
                    if (a != null) {
                        members.add(a);
                        inFolder.add(key);
                    }
                }
                if (members.isEmpty()) continue;
                AppEntry fe = new AppEntry();
                fe.isFolder = true;
                fe.folderName = f.name;
                fe.label = f.name;
                fe.bubble = makeFolderBubble(members);
                fe.glow = 0xFF8A94A6;
                list.add(fe);
            }
            for (Map.Entry<String, AppEntry> en : all.entrySet()) {
                if (!inFolder.contains(en.getKey())) list.add(en.getValue());
            }
        }
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));

        if (openFolder != null) {
            // Back entry, for watches without an easy back gesture.
            if (backBubble == null) backBubble = makeBackBubble();
            AppEntry bk = new AppEntry();
            bk.label = "Back";
            bk.pkg = getPackageName();
            bk.isBack = true;
            bk.bubble = backBubble;
            bk.glow = 0xFF8A94A6;
            list.add(bk);
        } else {
            // Built-in settings icon (also reachable by long-pressing empty space).
            if (settingsBubble == null) settingsBubble = makeGearBubble();
            AppEntry s = new AppEntry();
            s.label = "Look & Feel";
            s.pkg = getPackageName();
            s.isSettings = true;
            s.bubble = settingsBubble;
            s.glow = 0xFF8A94A6;
            list.add(s);
        }

        view.setApps(list);
    }

    private void setOpenFolder(String name) {
        openFolder = name;
        view.jumpToCenter();
        loadApps();
        view.resetLaunch(); // if this came from tapping a folder, end the swell animation
        view.playIntro();
    }

    /** After hide / folder changes: rebuild the grid and pop the icons back in. */
    private void refreshAfterChange() {
        view.jumpToCenter();
        loadApps();
        view.playIntro();
    }

    /** Render an app icon into a round bitmap in the current theme's style. */
    private Bitmap makeBubble(Drawable d) {
        final int S = BUBBLE_PX;
        Styles.Theme t = Styles.theme(this);
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        boolean adaptive = Build.VERSION.SDK_INT >= 26 && d instanceof AdaptiveIconDrawable;
        boolean none = t.shape == Styles.SHAPE_NONE;   // "Original": no disc, no mask
        if (adaptive || none) {
            d.setBounds(0, 0, S, S);              // full-bleed; masked to the theme shape below
        } else {
            c.drawColor(t.disc);                   // disc behind legacy square icons
            int inset = (int) (S * t.inset);
            d.setBounds(inset, inset, S - inset, S - inset);
        }
        d.draw(c);

        if (t.gray) b = toGray(b);
        if (t.tint != 0) b = tintBitmap(b, t.tint);
        return finishBubble(b, t, false);
    }

    /** Gear icon for the built-in settings entry. */
    private Bitmap makeGearBubble() {
        final int S = BUBBLE_PX;
        Styles.Theme t = Styles.theme(this);
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, S, 0xFF5A6270, 0xFF2A2F38, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, S, S, bg);

        Paint gear = new Paint(Paint.ANTI_ALIAS_FLAG);
        gear.setColor(0xFFE8ECF2);
        c.save();
        c.translate(S / 2f, S / 2f);
        for (int i = 0; i < 8; i++) {
            c.drawRoundRect(-S * 0.07f, -S * 0.37f, S * 0.07f, -S * 0.20f, S * 0.03f, S * 0.03f, gear);
            c.rotate(45f);
        }
        c.drawCircle(0, 0, S * 0.27f, gear);
        Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        hole.setColor(0xFF2F3540);
        c.drawCircle(0, 0, S * 0.11f, hole);
        c.restore();

        return finishBubble(b, t, true);
    }

    /** Folder icon: a dark disc with up to four mini icons of its members. */
    private Bitmap makeFolderBubble(List<AppEntry> members) {
        final int S = BUBBLE_PX;
        Styles.Theme t = Styles.theme(this);
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, S, 0xFF4A5060, 0xFF23272F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, S, S, bg);

        float cell = S * 0.34f, gap = S * 0.05f;
        float start = (S - (2 * cell + gap)) / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int n = Math.min(4, members.size());
        RectF r = new RectF();
        for (int i = 0; i < n; i++) {
            float l = start + (i % 2) * (cell + gap);
            float tp = start + (i / 2) * (cell + gap);
            r.set(l, tp, l + cell, tp + cell);
            c.drawBitmap(members.get(i).bubble, null, r, p);
        }
        if (t.gray) b = toGray(b);
        if (t.tint != 0) b = tintBitmap(b, t.tint);
        return finishBubble(b, t, true);
    }

    /** Left-pointing chevron for the "Back" entry inside a folder. */
    private Bitmap makeBackBubble() {
        final int S = BUBBLE_PX;
        Styles.Theme t = Styles.theme(this);
        Bitmap b = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, 0, S, 0xFF5A6270, 0xFF2A2F38, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, S, S, bg);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(S * 0.09f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFFE8ECF2);
        Path path = new Path();
        path.moveTo(S * 0.58f, S * 0.27f);
        path.lineTo(S * 0.37f, S * 0.50f);
        path.lineTo(S * 0.58f, S * 0.73f);
        c.drawPath(path, line);

        return finishBubble(b, t, true);
    }

    private Bitmap toGray(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(src, 0, 0, p);
        return out;
    }

    /** Multiply a colour over an (already grey) icon for a duotone look. */
    private Bitmap tintBitmap(Bitmap src, int color) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        new Canvas(out).drawBitmap(src, 0, 0, p);
        return out;
    }

    /** Outline of the icon shape, filling a S x S box. */
    private Path shapePath(int S, int shape) {
        Path p = new Path();
        if (shape == Styles.SHAPE_SQUIRCLE) {
            // Superellipse |x|^5 + |y|^5 = 1: the iOS-style "squircle".
            float h = S / 2f;
            final int steps = 96;
            for (int i = 0; i <= steps; i++) {
                double a = 2 * Math.PI * i / steps;
                double cs = Math.cos(a), sn = Math.sin(a);
                float x = h + h * (float) (Math.signum(cs) * Math.pow(Math.abs(cs), 2.0 / 5.0));
                float y = h + h * (float) (Math.signum(sn) * Math.pow(Math.abs(sn), 2.0 / 5.0));
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            p.close();
        } else if (shape == Styles.SHAPE_ROUND) {
            p.addRoundRect(new RectF(0, 0, S, S), S * 0.30f, S * 0.30f, Path.Direction.CW);
        } else {
            p.addCircle(S / 2f, S / 2f, S / 2f, Path.Direction.CW);
        }
        return p;
    }

    /** Crewmate visor drawn over the icon (Among Us theme). */
    private void drawVisor(Canvas c, int S) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF v = new RectF(S * 0.38f, S * 0.17f, S * 0.94f, S * 0.47f);
        p.setColor(0xAA9FE5F5);
        c.drawRoundRect(v, S * 0.15f, S * 0.15f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(S * 0.025f);
        p.setColor(0xFF3A0A0A);
        c.drawRoundRect(v, S * 0.15f, S * 0.15f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCCFFFFFF);
        c.drawRoundRect(new RectF(S * 0.50f, S * 0.21f, S * 0.78f, S * 0.27f),
                S * 0.03f, S * 0.03f, p);
    }

    /**
     * Theme gloss + rim light, then an anti-aliased crop to the theme's shape.
     * "Original" leaves app icons untouched; built-in icons (gear, back, folder) still get a circle.
     */
    private Bitmap finishBubble(Bitmap b, Styles.Theme t, boolean builtin) {
        int shape = t.shape;
        if (shape == Styles.SHAPE_NONE) {
            if (!builtin) return b;
            shape = Styles.SHAPE_CIRCLE;
        }
        final int S = b.getWidth();
        Canvas c = new Canvas(b);

        if (t.gloss) {
            Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
            gloss.setShader(new LinearGradient(0, 0, 0, S * 0.55f, 0x2EFFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, S, S * 0.55f, gloss);
        }
        if (t.visor && !builtin) drawVisor(c, S);

        Path outline = shapePath(S, shape);

        if (Color.alpha(t.rimColor) != 0) {
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            float sw = t.rimSolid ? S * 0.05f : S * 0.025f;
            rim.setStrokeWidth(sw);
            if (t.rimSolid) {
                rim.setColor(t.rimColor);
            } else {
                rim.setShader(new LinearGradient(0, 0, 0, S, t.rimColor, t.rimColor & 0x00FFFFFF,
                        Shader.TileMode.CLAMP));
            }
            // Pull the outline in by half the stroke so the rim stays inside the shape.
            Path rp = new Path(outline);
            Matrix m = new Matrix();
            float sc = (S - sw) / S;
            m.setScale(sc, sc, S / 2f, S / 2f);
            rp.transform(m);
            c.drawPath(rp, rim);
        }

        if (shapeMask == null || shapeMask.getWidth() != S || maskShape != shape) {
            shapeMask = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            new Canvas(shapeMask).drawPath(outline, p);
            maskShape = shape;
        }
        Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(shapeMask, 0, 0, m);

        b.setHasMipMap(true);
        return b;
    }

    /** Vivid average colour of the icon (favours saturated pixels), for the ambient glow. */
    private int dominantColor(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 8, 8, true);
        float[] hsv = new float[3];
        float rs = 0, gs = 0, bs = 0, ws = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int p = s.getPixel(x, y);
                int a = Color.alpha(p);
                if (a < 40) continue;
                Color.colorToHSV(p, hsv);
                float w = (a / 255f) * (0.15f + hsv[1]);
                rs += Color.red(p) * w;
                gs += Color.green(p) * w;
                bs += Color.blue(p) * w;
                ws += w;
            }
        }
        if (s != b) s.recycle();
        if (ws < 1e-3f) return 0xFF8899AA;
        int avg = Color.rgb((int) (rs / ws), (int) (gs / ws), (int) (bs / ws));
        Color.colorToHSV(avg, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.2f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    @Override
    public void onAppClick(AppEntry app) {
        if (app.isBack) {
            setOpenFolder(null);
            return;
        }
        if (app.isFolder) {
            setOpenFolder(app.folderName);
            return;
        }
        if (app.isSettings) {
            // Plain explicit intent: launcher-style flags (NEW_TASK / RESET_TASK_IF_NEEDED) make
            // Android reset this task, which swallows our own settings screen.
            returningFromSettings = true;
            try {
                startActivity(new Intent(this, SettingsActivity.class));
            } catch (Exception e) {
                returningFromSettings = false;
                view.resetLaunch();
            }
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(app.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception ignored) {
            // App may have been removed or disabled since the list was built.
            view.resetLaunch();
        }
    }

    @Override
    public void onAppLongClick(AppEntry app) {
        if (app.isBack) return;
        if (app.isSettings) {
            onBackgroundLongClick();
            return;
        }
        List<HoneycombView.MenuItem> items = new ArrayList<>();
        if (app.isFolder) {
            items.add(new HoneycombView.MenuItem("Open", ACT_OPEN, app.folderName, false));
            items.add(new HoneycombView.MenuItem("Ungroup", ACT_DISSOLVE, app.folderName, false));
        } else {
            items.add(new HoneycombView.MenuItem("App info", ACT_INFO));
            items.add(new HoneycombView.MenuItem("Delete", ACT_DELETE, null, true));
            items.add(new HoneycombView.MenuItem("Hide", ACT_HIDE));
            items.add(openFolder != null
                    ? new HoneycombView.MenuItem("Take out", ACT_TAKEOUT)
                    : new HoneycombView.MenuItem("Folder", ACT_FOLDER));
        }
        view.showMenu(app, items);
    }

    @Override
    public void onMenuItem(AppEntry app, HoneycombView.MenuItem item) {
        switch (item.id) {
            case ACT_INFO:
                openAppInfo(app);
                break;
            case ACT_DELETE:
                deleteApp(app);
                break;
            case ACT_HIDE:
                Styles.hide(this, app.key());
                toast("Hidden. Restore in Look & Feel");
                refreshAfterChange();
                break;
            case ACT_FOLDER: {
                List<Styles.Folder> folders = Styles.getFolders(this);
                if (folders.isEmpty()) {
                    toast("Added to " + Styles.addToNewFolder(this, app.key()));
                    refreshAfterChange();
                } else {
                    // Second popup: pick which folder.
                    List<HoneycombView.MenuItem> pick = new ArrayList<>();
                    for (int i = 0; i < folders.size() && i < 5; i++) {
                        String n = folders.get(i).name;
                        pick.add(new HoneycombView.MenuItem(n, ACT_PICK, n, false));
                    }
                    pick.add(new HoneycombView.MenuItem("+ New", ACT_NEWFOLDER));
                    view.showMenu(app, pick);
                }
                break;
            }
            case ACT_PICK:
                Styles.addToFolder(this, item.arg, app.key());
                toast("Added to " + item.arg);
                refreshAfterChange();
                break;
            case ACT_NEWFOLDER:
                toast("Added to " + Styles.addToNewFolder(this, app.key()));
                refreshAfterChange();
                break;
            case ACT_TAKEOUT:
                Styles.removeFromFolder(this, app.key());
                refreshAfterChange();
                break;
            case ACT_OPEN:
                setOpenFolder(item.arg);
                break;
            case ACT_DISSOLVE:
                Styles.dissolveFolder(this, item.arg);
                refreshAfterChange();
                break;
        }
    }

    private void openAppInfo(AppEntry app) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + app.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("No app info screen on this watch");
        }
    }

    private void deleteApp(AppEntry app) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(app.pkg, 0);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (system) {
                toast("System app, can't be deleted");
                return;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i); // list refreshes in onResume once the dialog closes
        } catch (Exception e) {
            toast("Can't delete this app");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackgroundLongClick() {
        returningFromSettings = true;
        startActivity(new Intent(this, SettingsActivity.class));
    }
}
