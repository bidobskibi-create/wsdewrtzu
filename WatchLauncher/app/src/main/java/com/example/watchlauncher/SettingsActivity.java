package com.example.watchlauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Round-screen friendly settings: theme, bounciness, background. Changes save instantly. */
public class SettingsActivity extends Activity {

    private int w; // screen size in px; all sizes are fractions of this
    private final List<SwatchView> themeViews = new ArrayList<>();
    private final List<SwatchView> bgViews = new ArrayList<>();
    private TextView themeLabel, bounceLabel, bgLabel, unhide;
    private TextView pickBtn, useBtn, removeBtn, dimLabel, sizeLabel, lensLabel;
    private SeekBar dimSeek;

    private static final int REQ_PICK = 11, REQ_PERM = 12;

    private interface IntCb { void on(int v); }
    private interface BoolCb { void on(boolean v); }
    private SeekBar seek;
    private BounceDot dot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        w = Math.min(dm.widthPixels, dm.heightPixels);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        // Extra top/bottom padding keeps content clear of the round bezel.
        col.setPadding(px(0.10f), px(0.17f), px(0.10f), px(0.26f));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        add(col, label("Look & Feel", 0.066f, Color.WHITE, true), 0f);

        // ---- Theme ----
        themeLabel = label("", 0.045f, 0xFF8E8E93, false);
        add(col, themeLabel, 0.06f);
        LinearLayout row = null;
        for (int i = 0; i < Styles.THEMES.length; i++) {
            if (i % 3 == 0) {
                row = newRow();
                add(col, row, 0.012f);
            }
            final int idx = i;
            Styles.Theme t = Styles.THEMES[i];
            SwatchView sv = new SwatchView(this, t.swatchTop, t.swatchBottom, false);
            sv.setOnClickListener(v -> {
                Styles.setTheme(this, idx);
                refresh();
            });
            row.addView(sv, swatchParams());
            themeViews.add(sv);
        }

        // ---- Bounciness ----
        bounceLabel = label("", 0.045f, 0xFF8E8E93, false);
        add(col, bounceLabel, 0.07f);

        dot = new BounceDot(this);
        dot.setOnClickListener(v -> dot.pop(Styles.getBounce(this) / 100f));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(0.17f), px(0.17f));
        dlp.setMargins(0, px(0.02f), 0, 0);
        col.addView(dot, dlp);

        seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(Styles.getBounce(this));
        seek.setPadding(px(0.04f), px(0.04f), px(0.04f), px(0.04f));
        seek.setProgressTintList(ColorStateList.valueOf(0xFF0A84FF));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
        seek.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    Styles.setBounce(SettingsActivity.this, progress);
                    refresh();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                dot.pop(sb.getProgress() / 100f); // demo the bounce on release
            }
        });
        add(col, seek, 0.01f);

        // ---- Background ----
        bgLabel = label("", 0.045f, 0xFF8E8E93, false);
        add(col, bgLabel, 0.06f);
        row = null;
        for (int i = 0; i < Styles.BGS.length; i++) {
            if (i % 3 == 0) {
                row = newRow();
                add(col, row, 0.012f);
            }
            final int idx = i;
            Styles.Bg b = Styles.BGS[i];
            // Black would vanish against the black screen, so give its swatch a lighter centre.
            int center = (b.center == 0xFF000000) ? 0xFF2A2A2A : b.center;
            SwatchView sv = new SwatchView(this, center, b.edge, true);
            sv.setOnClickListener(v -> {
                Styles.setBg(this, idx);
                Styles.setUseImage(this, false);
                refresh();
            });
            row.addView(sv, swatchParams());
            bgViews.add(sv);
        }

        // ---- Background image ----
        pickBtn = pill("Choose image", 0xFF0A84FF);
        pickBtn.setOnClickListener(v -> pickImage());
        addPill(col, pickBtn, 0.03f);
        useBtn = pill("Use saved image", 0xFF0A84FF);
        useBtn.setOnClickListener(v -> {
            Styles.setUseImage(this, true);
            refresh();
        });
        addPill(col, useBtn, 0.02f);
        removeBtn = pill("Remove image", 0xFFFF453A);
        removeBtn.setOnClickListener(v -> {
            BgImage.delete(this);
            Styles.setUseImage(this, false);
            refresh();
        });
        addPill(col, removeBtn, 0.02f);

        dimLabel = label("", 0.045f, 0xFF8E8E93, false);
        add(col, dimLabel, 0.05f);
        dimSeek = newSeek(90, Styles.getDim(this), pr -> {
            Styles.setDim(this, pr);
            refresh();
        });
        add(col, dimSeek, 0.01f);

        // ---- Icon size ----
        sizeLabel = label("", 0.045f, 0xFF8E8E93, false);
        add(col, sizeLabel, 0.07f);
        add(col, newSeek(100, Styles.getIconSize(this), pr -> {
            Styles.setIconSize(this, pr);
            refresh();
        }), 0.01f);

        // ---- Lens ----
        lensLabel = label("", 0.045f, 0xFF8E8E93, false);
        add(col, lensLabel, 0.06f);
        add(col, newSeek(100, Styles.getLens(this), pr -> {
            Styles.setLens(this, pr);
            refresh();
        }), 0.01f);

        // ---- Switches ----
        addPill(col, toggle("Glow", Styles.getGlow(this), on -> Styles.setGlow(this, on)), 0.07f);
        addPill(col, toggle("App names", Styles.getLabels(this), on -> Styles.setLabels(this, on)), 0.025f);
        addPill(col, toggle("Haptics", Styles.getHaptics(this), on -> Styles.setHaptics(this, on)), 0.025f);

        // ---- Hidden apps ----
        unhide = label("", 0.048f, 0xFF0A84FF, false);
        GradientDrawable pill2 = new GradientDrawable();
        pill2.setColor(0xFF1C1C1E);
        pill2.setCornerRadius(px(0.08f));
        unhide.setBackground(pill2);
        unhide.setPadding(px(0.07f), px(0.035f), px(0.07f), px(0.035f));
        unhide.setOnClickListener(v -> {
            if (!Styles.getHidden(this).isEmpty()) {
                Styles.unhideAll(this);
                refresh();
            }
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.setMargins(0, px(0.07f), 0, 0);
        col.addView(unhide, ulp);

        // ---- Reset ----
        TextView reset = label("Reset to defaults", 0.048f, 0xFFFF453A, false);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF1C1C1E);
        pill.setCornerRadius(px(0.08f));
        reset.setBackground(pill);
        reset.setPadding(px(0.07f), px(0.035f), px(0.07f), px(0.035f));
        reset.setOnClickListener(v -> {
            Styles.reset(this);
            recreate(); // rebuild the screen from the default values
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, px(0.04f), 0, 0);
        col.addView(reset, rlp);

        refresh();
    }

    private void refresh() {
        int ti = Styles.getTheme(this);
        int bi = Styles.getBg(this);
        int bp = Styles.getBounce(this);
        for (int i = 0; i < themeViews.size(); i++) themeViews.get(i).setSelected(i == ti);
        boolean hasImg = BgImage.file(this).exists();
        boolean useImg = hasImg && Styles.getUseImage(this);
        for (int i = 0; i < bgViews.size(); i++) bgViews.get(i).setSelected(!useImg && i == bi);
        themeLabel.setText("THEME  ·  " + Styles.THEMES[ti].name.toUpperCase());
        bgLabel.setText("BACKGROUND  ·  " + (useImg ? "IMAGE" : Styles.BGS[bi].name.toUpperCase()));
        pickBtn.setText(hasImg ? "Change image" : "Choose image");
        useBtn.setVisibility(hasImg && !useImg ? View.VISIBLE : View.GONE);
        removeBtn.setVisibility(hasImg ? View.VISIBLE : View.GONE);
        dimLabel.setVisibility(useImg ? View.VISIBLE : View.GONE);
        dimSeek.setVisibility(useImg ? View.VISIBLE : View.GONE);
        dimLabel.setText("IMAGE DIMMING  ·  " + Styles.getDim(this) + "%");
        sizeLabel.setText("ICON SIZE  ·  " + Math.round(Styles.sizeScale(this) * 100) + "%");
        lensLabel.setText("LENS  ·  " + Styles.getLens(this) + "%");
        bounceLabel.setText("BOUNCINESS  ·  " + bounceName(bp));
        int hc = Styles.getHidden(this).size();
        unhide.setText(hc > 0 ? "Show " + hc + " hidden app" + (hc == 1 ? "" : "s")
                : "No hidden apps");
        unhide.setAlpha(hc > 0 ? 1f : 0.45f);
    }

    // ---- background image import ----

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            importFromFolder(); // no gallery / file picker on this watch
        }
    }

    /** Fallback: read /sdcard/WatchLauncher/background.jpg (push it with adb). */
    private void importFromFolder() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
            return;
        }
        File dir = new File(Environment.getExternalStorageDirectory(), "WatchLauncher");
        File found = null;
        for (String n : new String[]{"background.jpg", "background.jpeg", "background.png"}) {
            File f = new File(dir, n);
            if (f.exists()) {
                found = f;
                break;
            }
        }
        if (found == null) {
            Toast.makeText(this, "No gallery on this watch. Put the picture at "
                    + "/sdcard/WatchLauncher/background.jpg and tap again", Toast.LENGTH_LONG).show();
            return;
        }
        importAsync(BgImage.fromFile(found));
    }

    private void importAsync(final BgImage.Src src) {
        Toast.makeText(this, "Loading image...", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        new Thread(() -> {
            final boolean ok = BgImage.importFrom(app, src);
            runOnUiThread(() -> {
                if (ok) Styles.setUseImage(this, true);
                Toast.makeText(this, ok ? "Background set" : "Couldn't load that image",
                        Toast.LENGTH_SHORT).show();
                if (!isFinishing()) refresh();
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) {
            importAsync(BgImage.fromUri(this, data.getData()));
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_PERM) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                importFromFolder();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---- widget builders ----

    private SeekBar newSeek(int max, int progress, final IntCb cb) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setPadding(px(0.04f), px(0.04f), px(0.04f), px(0.04f));
        sb.setProgressTintList(ColorStateList.valueOf(0xFF0A84FF));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0x55FFFFFF));
        sb.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) cb.on(p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        return sb;
    }

    private TextView pill(String text, int color) {
        TextView tv = label(text, 0.048f, color, false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1C1C1E);
        g.setCornerRadius(px(0.08f));
        tv.setBackground(g);
        tv.setPadding(px(0.07f), px(0.035f), px(0.07f), px(0.035f));
        return tv;
    }

    private void addPill(LinearLayout col, View v, float topFrac) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(topFrac), 0, 0);
        col.addView(v, lp);
    }

    private TextView toggle(final String name, boolean initial, final BoolCb cb) {
        final TextView t = pill("", Color.WHITE);
        final boolean[] st = {initial};
        showToggle(t, name, initial);
        t.setOnClickListener(v -> {
            st[0] = !st[0];
            cb.on(st[0]);
            showToggle(t, name, st[0]);
        });
        return t;
    }

    private static void showToggle(TextView t, String name, boolean on) {
        t.setText(name + "  ·  " + (on ? "ON" : "OFF"));
        t.setTextColor(on ? 0xFF30D158 : 0xFF8E8E93);
    }

    private static String bounceName(int p) {
        if (p == 0) return "OFF";
        if (p < 25) return "SUBTLE";
        if (p < 60) return "BOUNCY";
        return "VERY BOUNCY";
    }

    // ---- small UI helpers ----

    private int px(float frac) {
        return Math.round(w * frac);
    }

    private TextView label(String text, float sizeFrac, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, w * sizeFrac);
        tv.setGravity(Gravity.CENTER);
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    private void add(LinearLayout col, View v, float topFrac) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(topFrac), 0, 0);
        col.addView(v, lp);
    }

    private LinearLayout newRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private LinearLayout.LayoutParams swatchParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(0.19f), px(0.19f));
        lp.setMargins(px(0.012f), px(0.006f), px(0.012f), px(0.006f));
        return lp;
    }

    /** Round colour swatch; shows a white ring when selected. */
    static class SwatchView extends View {
        private final int top, bottom;
        private final boolean radial;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwatchView(Context c, int top, int bottom, boolean radial) {
            super(c);
            this.top = top;
            this.bottom = bottom;
            this.radial = radial;
            ring.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f * 0.80f;
            fill.setShader(radial
                    ? new RadialGradient(cx, cy, r, top, bottom, Shader.TileMode.CLAMP)
                    : new LinearGradient(cx - r, cy - r, cx + r, cy + r, top, bottom,
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float half = Math.min(getWidth(), getHeight()) / 2f;
            canvas.drawCircle(cx, cy, half * 0.80f, fill);
            float sw = half * (isSelected() ? 0.09f : 0.03f);
            ring.setStrokeWidth(sw);
            ring.setColor(isSelected() ? Color.WHITE : 0x33FFFFFF);
            canvas.drawCircle(cx, cy, half - sw / 2f - half * 0.02f, ring);
        }
    }

    /** A dot that pops with the chosen bounciness so you can feel the setting. */
    static class BounceDot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float scale = 1f;
        private ValueAnimator anim;

        BounceDot(Context c) {
            super(c);
        }

        void pop(float bounce) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0.55f, 1f);
            anim.setDuration(480);
            anim.setInterpolator(new OvershootInterpolator(bounce * 2.25f));
            anim.addUpdateListener(a -> {
                scale = (Float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new LinearGradient(0, 0, 0, h, 0xFF0A84FF, 0xFFFF375F,
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = Math.min(getWidth(), getHeight()) / 2f * 0.8f * scale;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, r, p);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }
    }
}
