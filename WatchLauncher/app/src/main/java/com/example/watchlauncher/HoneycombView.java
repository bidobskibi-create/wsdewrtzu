package com.example.watchlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Scroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Apple-Watch-style honeycomb app grid.
 *
 * - Icons sit on a hex grid (spiral from the centre) under a fisheye lens.
 * - Ambient glow behind the grid takes the colour of the icon in the middle.
 * - Edge icons are dimmed for depth; icons pop in with a springy intro.
 * - Springy snap to the nearest icon, a tick of haptics as icons pass the centre.
 * - Tap: the icon swells to fill the screen, then the app launches.
 */
public class HoneycombView extends View {

    public interface Listener {
        void onAppClick(AppEntry app);
        void onAppLongClick(AppEntry app);          // respond with showMenu(...)
        void onMenuItem(AppEntry app, MenuItem item);
        void onBackgroundLongClick();
    }

    /** One entry in the long-press popup. */
    public static class MenuItem {
        public final String label;
        public final int id;
        public final String arg;
        public final boolean danger;

        public MenuItem(String label, int id, String arg, boolean danger) {
            this.label = label;
            this.id = id;
            this.arg = arg;
            this.danger = danger;
        }

        public MenuItem(String label, int id) {
            this(label, id, null, false);
        }
    }

    // ---- look & feel knobs -------------------------------------------------
    private static final float DEFAULT_K = 1.6f;       // fisheye strength (adjustable in settings)
    private static final float FILL = 0.88f;           // icon diameter / local spacing
    private static final float SPACING = 0.153f;       // grid spacing as fraction of screen size
    private static final float EDGE_DIM = 0.30f;       // how much icons dim toward the edge (0..1)
    private static final float GLOW_ALPHA = 0.55f;     // strength of the ambient glow (0..1)
    // ------------------------------------------------------------------------

    private float K, NORM, MAG0;                       // lens strength, normaliser, centre magnification
    private static final int[][] DIRS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

    private final List<AppEntry> apps = new ArrayList<>();
    private Listener listener;

    // Style (set from the settings screen).
    private Styles.Theme theme = Styles.THEMES[0];
    private Styles.Bg bg = Styles.BGS[0];
    private float bounce = Styles.DEFAULT_BOUNCE / 100f;
    private float introC1 = Styles.DEFAULT_BOUNCE / 100f * 4.25f;
    private final Paint bgPaint = new Paint();
    private final Paint bgImgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    // Extra options (set from settings).
    private float sizeScale = 1f, bgDim;
    private boolean glowOn = true, showLabels = true, haptics = true;
    private Bitmap bgImage;

    // World (unlensed) positions and per-frame screen results.
    private float[] wx = new float[0], wy = new float[0], wd = new float[0];
    private float[] sx = new float[0], sy = new float[0], sr = new float[0];
    private int[] sa = new int[0];
    private int[] order = new int[0];

    private float spacing, radius, cx, cy, side;
    private float panX, panY, minPX, maxPX, minPY, maxPY;
    private boolean dragging, snapPending;

    // Animation state.
    private float intro = 1f;
    private boolean introPlayed;
    private ValueAnimator introAnim, panAnim, launchAnim;
    private int pressed = -1;
    private int launching = -1;
    private float launchT;
    private boolean launchFired;
    private int lastCenter = -1;

    // Glow.
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int glowColor, glowFilterColor;
    private float glowStrength;
    private PorterDuffColorFilter glowFilter;

    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    // Long-press popup.
    private AppEntry menuApp;
    private List<MenuItem> menuItems = new ArrayList<>();
    private RectF[] itemRects = new RectF[0];
    private final RectF menuBox = new RectF();
    private final Paint menuBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint menuStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint itemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint();
    private final TextPaint menuText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path caretPath = new Path();
    private boolean menuSwallow, caretShown, caretDown;
    private float caretX, caretY;
    private int menuDown = -1, menuPressed = -1;

    private final Scroller scroller;
    private final GestureDetector detector;

    public HoneycombView(Context context) {
        this(context, null);
    }

    public HoneycombView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scroller = new Scroller(context);
        setLens(DEFAULT_K);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        pillPaint.setColor(0xFF1C1C1E);
        pillStroke.setStyle(Paint.Style.STROKE);
        pillStroke.setStrokeWidth(context.getResources().getDisplayMetrics().density);
        pillStroke.setColor(Color.WHITE);
        menuStroke.setStyle(Paint.Style.STROKE);
        menuStroke.setStrokeWidth(context.getResources().getDisplayMetrics().density);
        menuStroke.setColor(0x55FFFFFF);
        scrimPaint.setColor(0xB0000000);
        menuText.setTextAlign(Paint.Align.CENTER);

        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                dragging = true;
                snapPending = false;
                scroller.forceFinished(true);
                if (panAnim != null) panAnim.cancel();
                pressed = hit(e.getX(), e.getY());
                invalidate();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                pressed = -1;
                // Divide by MAG0 so the icon under the finger roughly follows it at the centre.
                panX += dx / MAG0;
                panY += dy / MAG0;
                clampPan();
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                pressed = -1;
                scroller.fling(Math.round(panX), Math.round(panY),
                        Math.round(-vx / MAG0), Math.round(-vy / MAG0),
                        Math.round(minPX), Math.round(maxPX),
                        Math.round(minPY), Math.round(maxPY));
                snapPending = true;
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                int i = hit(e.getX(), e.getY());
                if (i >= 0) startLaunch(i);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                pressed = -1;
                invalidate();
                int i = hit(e.getX(), e.getY());
                if (listener != null) {
                    if (i >= 0) {
                        listener.onAppLongClick(apps.get(i));
                    } else {
                        try {
                            if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        } catch (Exception ignored) {
                        }
                        listener.onBackgroundLongClick();
                    }
                }
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setApps(List<AppEntry> list) {
        boolean first = apps.isEmpty();
        menuApp = null;
        apps.clear();
        apps.addAll(list);
        rebuildWorld();
        if (first) {
            panX = 0;
            panY = 0;
        }
        clampPan();
        maybeIntro();
        invalidate();
    }

    /** Apply the saved theme, bounciness (0..100) and background. */
    public void applyStyle(Styles.Theme t, int bouncePercent, Styles.Bg b) {
        theme = t;
        bg = b;
        bounce = bouncePercent / 100f;
        introC1 = bounce * 4.25f;
        pillPaint.setColor(t.pillColor);
        menuBgPaint.setColor((t.pillColor & 0x00FFFFFF) | 0xF5000000);
        rebuildBg();
        invalidate();
    }

    private void setLens(float k) {
        K = k;
        NORM = 1f - (float) Math.exp(-k);
        MAG0 = k / NORM;
    }

    /** Icon size, lens strength, glow / label / haptic switches and the optional background image. */
    public void applyOptions(float sizeScale, float lensK, boolean glow, boolean labels,
                             boolean haptics, Bitmap image, float dim) {
        boolean relayout = Math.abs(sizeScale - this.sizeScale) > 1e-4f;
        this.sizeScale = sizeScale;
        setLens(lensK);
        this.glowOn = glow;
        this.showLabels = labels;
        this.haptics = haptics;
        this.bgImage = image;
        this.bgDim = dim;
        if (relayout && side > 0) {
            spacing = SPACING * side * sizeScale;
            rebuildWorld();
            jumpToCenter();
            clampPan();
        }
        invalidate();
    }

    private void rebuildBg() {
        if (radius <= 0) return;
        bgPaint.setShader(new RadialGradient(cx, cy, radius * 1.3f, bg.center, bg.edge,
                Shader.TileMode.CLAMP));
    }

    /** Snap straight to the centre icon (used when the list changes). */
    public void jumpToCenter() {
        scroller.forceFinished(true);
        if (panAnim != null) panAnim.cancel();
        panX = 0;
        panY = 0;
        invalidate();
    }

    public void recenter() {
        scroller.forceFinished(true);
        animateTo(0, 0);
    }

    public boolean isCentered() {
        return Math.abs(panX) < 1f && Math.abs(panY) < 1f;
    }

    /** Icons pop in from the centre outward. */
    public void playIntro() {
        if (introAnim != null) introAnim.cancel();
        intro = 0f;
        introAnim = ValueAnimator.ofFloat(0f, 1f);
        introAnim.setDuration(650);
        introAnim.setInterpolator(new LinearInterpolator());
        introAnim.addUpdateListener(a -> {
            intro = (Float) a.getAnimatedValue();
            invalidate();
        });
        introAnim.start();
        invalidate();
    }

    /** Call when the launcher comes back after an app was launched from it. */
    public void resetLaunch() {
        if (launching >= 0) {
            if (launchAnim != null) launchAnim.cancel();
            launching = -1;
            launchT = 0f;
            playIntro();
        }
    }

    private void maybeIntro() {
        if (!introPlayed && spacing > 0 && !apps.isEmpty()) {
            introPlayed = true;
            playIntro();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        side = Math.min(w, h);
        radius = side / 2f;
        cx = w / 2f;
        cy = h / 2f;
        spacing = SPACING * side * sizeScale;
        textPaint.setTextSize(0.062f * side);
        menuText.setTextSize(0.047f * side);

        glowPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{Color.argb((int) (255 * GLOW_ALPHA), 255, 255, 255),
                        Color.argb((int) (255 * GLOW_ALPHA * 0.38f), 255, 255, 255),
                        0x00FFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));

        rebuildBg();
        rebuildWorld();
        clampPan();
        maybeIntro();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (panAnim != null) panAnim.cancel();
        if (introAnim != null) introAnim.cancel();
        if (launchAnim != null) launchAnim.cancel();
    }

    /** Lay icons out on a hex grid, spiralling outward from the centre. */
    private void rebuildWorld() {
        int n = apps.size();
        wx = new float[n];
        wy = new float[n];
        wd = new float[n];
        sx = new float[n];
        sy = new float[n];
        sr = new float[n];
        sa = new int[n];
        order = new int[n];
        lastCenter = -1;
        for (int i = 0; i < n; i++) order[i] = i;
        if (n == 0 || spacing == 0) return;

        int i = 0;
        place(i++, 0, 0);
        for (int k = 1; i < n; k++) {
            int q = -k, r = k;
            for (int s = 0; s < 6 && i < n; s++) {
                for (int j = 0; j < k && i < n; j++) {
                    place(i++, q, r);
                    q += DIRS[s][0];
                    r += DIRS[s][1];
                }
            }
        }

        minPX = minPY = Float.MAX_VALUE;
        maxPX = maxPY = -Float.MAX_VALUE;
        for (int j = 0; j < n; j++) {
            minPX = Math.min(minPX, wx[j]);
            maxPX = Math.max(maxPX, wx[j]);
            minPY = Math.min(minPY, wy[j]);
            maxPY = Math.max(maxPY, wy[j]);
        }
    }

    private void place(int idx, int q, int r) {
        wx[idx] = spacing * (q + r * 0.5f);
        wy[idx] = spacing * (r * 0.8660254f);
        wd[idx] = (float) Math.sqrt(wx[idx] * wx[idx] + wy[idx] * wy[idx]) / spacing;
    }

    private void clampPan() {
        if (apps.isEmpty()) return;
        panX = Math.max(minPX, Math.min(maxPX, panX));
        panY = Math.max(minPY, Math.min(maxPY, panY));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Apply the fisheye lens (plus dimming and intro scaling) to every icon. */
    private void compute() {
        int n = apps.size();
        for (int i = 0; i < n; i++) {
            float dx = wx[i] - panX;
            float dy = wy[i] - panY;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float a = d / radius;
            float rp, radial, tang;
            if (a < 1e-4f) {
                rp = 0;
                radial = MAG0;
                tang = MAG0;
                sx[i] = cx;
                sy[i] = cy;
            } else {
                float e = (float) Math.exp(-K * a);
                float g = (1f - e) / NORM;
                rp = radius * g;
                radial = K * e / NORM;
                tang = g / a;
                float f = rp / d;
                sx[i] = cx + dx * f;
                sy[i] = cy + dy * f;
            }
            float mag = (float) Math.sqrt(radial * tang);
            float rn = rp / radius;

            // Intro: each icon pops in with a little overshoot, staggered by ring.
            float sc = 1f, ia = 1f;
            if (intro < 1f) {
                float local = clamp01((intro - wd[i] * 0.07f) / 0.5f);
                float t = local - 1f;
                sc = local >= 1f ? 1f : 1f + (introC1 + 1f) * t * t * t + introC1 * t * t;
                ia = local;
            }
            sr[i] = 0.5f * FILL * spacing * mag * sc;

            // Fade past the screen edge, and dim gently toward it for depth.
            float fade = clamp01((1.3f - rn) / 0.3f);
            float x = clamp01((rn - 0.5f) / 0.5f);
            float dim = 1f - EDGE_DIM * (x * x * (3f - 2f * x));
            sa[i] = (int) (255 * fade * dim * ia);
        }
        // Insertion sort by size so big (central) icons draw last, on top.
        for (int i = 1; i < n; i++) {
            int v = order[i];
            int j = i - 1;
            while (j >= 0 && sr[order[j]] > sr[v]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = v;
        }
    }

    /** Blend the accent colours of icons near the centre into one glow colour. */
    private void computeGlow() {
        int n = apps.size();
        if (!glowOn || theme.glowMode == Styles.GLOW_OFF) {
            glowStrength = 0f;
            return;
        }
        if (theme.glowMode == Styles.GLOW_FIXED) {
            glowColor = theme.glowColor;
            glowStrength = 0.85f;
            return;
        }
        float lim = 1.3f * spacing * MAG0;
        float rs = 0, gs = 0, bs = 0, ws = 0;
        for (int i = 0; i < n; i++) {
            float dx = sx[i] - cx, dy = sy[i] - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d >= lim) continue;
            float w = 1f - d / lim;
            w *= w;
            int c = apps.get(i).glow;
            rs += Color.red(c) * w;
            gs += Color.green(c) * w;
            bs += Color.blue(c) * w;
            ws += w;
        }
        if (ws < 1e-3f) {
            glowStrength = 0f;
            return;
        }
        glowColor = Color.rgb((int) (rs / ws), (int) (gs / ws), (int) (bs / ws));
        glowStrength = clamp01(ws);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bgImage != null) {
            drawBgImage(canvas);
        } else if (bgPaint.getShader() != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        } else {
            canvas.drawColor(Color.BLACK);
        }
        int n = apps.size();
        if (n == 0) return;
        compute();
        computeGlow();

        float lf = launching >= 0 ? 1f - launchT : 1f; // everything else fades while launching

        // Ambient glow.
        if (glowStrength > 0.01f && glowPaint.getShader() != null) {
            if (glowFilter == null || glowFilterColor != glowColor) {
                glowFilter = new PorterDuffColorFilter(glowColor, PorterDuff.Mode.SRC_IN);
                glowFilterColor = glowColor;
            }
            glowPaint.setColorFilter(glowFilter);
            glowPaint.setAlpha((int) (255 * glowStrength * lf * Math.min(1f, intro)));
            canvas.drawCircle(cx, cy, radius * 1.05f, glowPaint);
        }

        int w = getWidth(), h = getHeight();
        for (int k = 0; k < n; k++) {
            int i = order[k];
            if (i == launching) continue;
            float r = sr[i] * (i == pressed ? 0.9f : 1f);
            int alpha = (int) (sa[i] * lf);
            if (alpha <= 0 || r < 1.5f) continue;
            if (sx[i] + r < 0 || sx[i] - r > w || sy[i] + r < 0 || sy[i] - r > h) continue;
            Bitmap b = apps.get(i).bubble;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(sx[i] - r, sy[i] - r, sx[i] + r, sy[i] + r);
            bubblePaint.setAlpha(alpha);
            canvas.drawBitmap(b, src, dst, bubblePaint);
        }

        // The launched icon swells toward the screen centre and past the edges.
        if (launching >= 0 && launching < n) {
            int i = launching;
            float t = launchT * launchT;
            float r = sr[i] + (radius * 1.5f - sr[i]) * t;
            float x = sx[i] + (cx - sx[i]) * launchT;
            float y = sy[i] + (cy - sy[i]) * launchT;
            Bitmap b = apps.get(i).bubble;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(x - r, y - r, x + r, y + r);
            bubblePaint.setAlpha(255);
            canvas.drawBitmap(b, src, dst, bubblePaint);
        }

        drawLabel(canvas, lf);

        if (menuApp != null) drawMenu(canvas);
    }

    /** Centre-crop the picture over the whole view, then darken it by the chosen amount. */
    private void drawBgImage(Canvas canvas) {
        float vw = getWidth(), vh = getHeight();
        int bw = bgImage.getWidth(), bh = bgImage.getHeight();
        float scale = Math.max(vw / bw, vh / bh);
        int sw = Math.min(bw, Math.round(vw / scale)), sh = Math.min(bh, Math.round(vh / scale));
        int l = (bw - sw) / 2, t = (bh - sh) / 2;
        src.set(l, t, l + sw, t + sh);
        dst.set(0, 0, vw, vh);
        canvas.drawBitmap(bgImage, src, dst, bgImgPaint);
        if (bgDim > 0f) canvas.drawColor(Color.argb((int) (255 * bgDim), 0, 0, 0));
    }

    /** Show the name of the icon at the centre, fading as it moves away. */
    private void drawLabel(Canvas canvas, float lf) {
        int n = apps.size();
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float dx = sx[i] - cx, dy = sy[i] - cy;
            float d2 = dx * dx + dy * dy;
            if (d2 < bd) {
                bd = d2;
                best = i;
            }
        }
        if (best < 0) return;

        // A small haptic tick each time a new icon becomes the centred one.
        if (best != lastCenter) {
            boolean moving = dragging || !scroller.isFinished()
                    || (panAnim != null && panAnim.isRunning());
            if (lastCenter != -1 && moving && intro >= 1f && launching < 0) {
                try {
                    if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } catch (Exception ignored) {
                }
            }
            lastCenter = best;
        }

        if (!showLabels) return;

        float lim = 0.4f * spacing * MAG0;
        float dist = (float) Math.sqrt(bd);
        if (dist >= lim) return;
        int alpha = (int) (255 * (1f - dist / lim) * lf * clamp01(intro));
        if (alpha <= 0) return;

        String text = TextUtils.ellipsize(apps.get(best).label, textPaint,
                side * 0.42f, TextUtils.TruncateAt.END).toString();
        float tw = textPaint.measureText(text);
        float ts = textPaint.getTextSize();
        float pad = 0.03f * side;
        float ty = cy + radius * 0.84f;

        dst.set(cx - tw / 2f - pad, ty - ts * 0.95f - pad * 0.5f,
                cx + tw / 2f + pad, ty + ts * 0.3f + pad * 0.5f);
        float rr = dst.height() / 2f;
        pillPaint.setAlpha(alpha * 200 / 255);
        canvas.drawRoundRect(dst, rr, rr, pillPaint);
        pillStroke.setAlpha(alpha * 60 / 255);
        canvas.drawRoundRect(dst, rr, rr, pillStroke);
        textPaint.setAlpha(alpha);
        canvas.drawText(text, cx, ty, textPaint);
    }

    /** Index of the icon under (x, y), preferring the biggest, or -1. */
    private int hit(float x, float y) {
        int n = apps.size();
        if (n == 0) return -1;
        compute();
        float slop = 2f * getResources().getDisplayMetrics().density;
        for (int k = n - 1; k >= 0; k--) {
            int i = order[k];
            if (sa[i] <= 0) continue;
            float dx = x - sx[i], dy = y - sy[i];
            float r = sr[i] + slop;
            if (dx * dx + dy * dy <= r * r) return i;
        }
        return -1;
    }

    // ---- long-press popup --------------------------------------------------

    /** Show a box of options next to the given app's icon. */
    public void showMenu(AppEntry app, List<MenuItem> items) {
        menuApp = app;
        menuItems = new ArrayList<>(items);
        menuSwallow = true; // ignore the rest of the current gesture (the finger is still down)
        menuDown = -1;
        menuPressed = -1;
        pressed = -1;
        dragging = false;
        snapPending = false;
        try {
            if (haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignored) {
        }
        invalidate();
    }

    public void dismissMenu() {
        menuApp = null;
        menuDown = -1;
        menuPressed = -1;
        invalidate();
    }

    public boolean isMenuShowing() {
        return menuApp != null;
    }

    /** Work out where the box and its buttons go: above the icon if there is room, else below. */
    private void layoutMenu(int idx) {
        int n = menuItems.size();
        int rows = (n + 1) / 2;
        float iw = 0.235f * side, ih = 0.095f * side, gap = 0.012f * side, pad = 0.02f * side;
        float bw = 2 * iw + gap + 2 * pad;
        float bh = rows * ih + (rows - 1) * gap + 2 * pad;
        float caret = 0.02f * side;

        float icx = sx[idx], icy = sy[idx], ir = sr[idx];
        float bcx = Math.max(cx - 0.05f * side, Math.min(cx + 0.05f * side, icx));

        // Keep all four corners inside the round screen.
        float needHalf = bw / 2f + Math.abs(bcx - cx);
        float maxDy = (float) Math.sqrt(Math.max(0f, radius * radius - needHalf * needHalf))
                - 0.01f * side;
        float minTop = cy - maxDy, maxBottom = cy + maxDy;

        float top = icy - ir - caret - bh;
        caretDown = true;
        caretShown = true;
        if (top < minTop) {
            float below = icy + ir + caret;
            if (below + bh <= maxBottom) {
                top = below;
                caretDown = false;
            } else {
                caretShown = false; // no room either side: sit as close as we can
            }
        }
        top = Math.max(minTop, Math.min(maxBottom - bh, top));
        menuBox.set(bcx - bw / 2f, top, bcx + bw / 2f, top + bh);

        caretX = Math.max(menuBox.left + 0.06f * side, Math.min(menuBox.right - 0.06f * side, icx));
        caretY = caretDown ? menuBox.bottom : menuBox.top;

        if (itemRects.length != n) {
            itemRects = new RectF[n];
            for (int i = 0; i < n; i++) itemRects[i] = new RectF();
        }
        for (int i = 0; i < n; i++) {
            int r = i / 2, c = i % 2;
            float l = menuBox.left + pad + c * (iw + gap);
            float t = menuBox.top + pad + r * (ih + gap);
            boolean lone = (i == n - 1) && (n % 2 == 1);
            itemRects[i].set(lone ? menuBox.left + pad : l, t,
                    lone ? menuBox.right - pad : l + iw, t + ih);
        }
    }

    private void drawMenu(Canvas canvas) {
        int idx = apps.indexOf(menuApp);
        if (idx < 0) {
            menuApp = null;
            return;
        }
        layoutMenu(idx);

        // Dim everything, then put the chosen icon back on top at full brightness.
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        float r = sr[idx];
        Bitmap b = menuApp.bubble;
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(sx[idx] - r, sy[idx] - r, sx[idx] + r, sy[idx] + r);
        bubblePaint.setAlpha(255);
        canvas.drawBitmap(b, src, dst, bubblePaint);

        float rad = 0.05f * side;
        canvas.drawRoundRect(menuBox, rad, rad, menuBgPaint);
        canvas.drawRoundRect(menuBox, rad, rad, menuStroke);

        if (caretShown) {
            float cw = 0.03f * side, ch = 0.02f * side;
            float dir = caretDown ? 1f : -1f;
            caretPath.reset();
            caretPath.moveTo(caretX - cw, caretY - dir * 0.5f);
            caretPath.lineTo(caretX + cw, caretY - dir * 0.5f);
            caretPath.lineTo(caretX, caretY + dir * ch);
            caretPath.close();
            canvas.drawPath(caretPath, menuBgPaint);
        }

        float ir = 0.03f * side;
        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem it = menuItems.get(i);
            RectF rc = itemRects[i];
            itemPaint.setColor(i == menuPressed ? 0xFF5A5A5E : 0xFF2C2C2E);
            canvas.drawRoundRect(rc, ir, ir, itemPaint);
            menuText.setColor(it.danger ? 0xFFFF453A : Color.WHITE);
            String text = TextUtils.ellipsize(it.label, menuText, rc.width() - 0.02f * side,
                    TextUtils.TruncateAt.END).toString();
            float ty = rc.centerY() - (menuText.ascent() + menuText.descent()) / 2f;
            canvas.drawText(text, rc.centerX(), ty, menuText);
        }
    }

    private int menuHit(float x, float y) {
        int idx = apps.indexOf(menuApp);
        if (idx < 0) return -1;
        compute();
        layoutMenu(idx);
        for (int i = 0; i < menuItems.size(); i++) {
            if (itemRects[i].contains(x, y)) return i;
        }
        return -1;
    }

    private boolean handleMenuTouch(MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            menuSwallow = false;
            menuDown = menuHit(e.getX(), e.getY());
            menuPressed = menuDown;
            invalidate();
            return true;
        }
        if (menuSwallow) return true; // tail of the long-press gesture that opened the menu

        if (act == MotionEvent.ACTION_MOVE) {
            int h = menuHit(e.getX(), e.getY());
            int np = (h == menuDown) ? h : -1;
            if (np != menuPressed) {
                menuPressed = np;
                invalidate();
            }
        } else if (act == MotionEvent.ACTION_UP) {
            int h = menuHit(e.getX(), e.getY());
            int down = menuDown;
            AppEntry app = menuApp;
            List<MenuItem> items = menuItems;
            menuDown = -1;
            menuPressed = -1;
            if (h >= 0 && h == down) {
                menuApp = null; // close first: the listener may open another menu
                invalidate();
                if (listener != null) listener.onMenuItem(app, items.get(h));
            } else if (!menuBox.contains(e.getX(), e.getY())) {
                dismissMenu(); // tapped outside the box
            } else {
                invalidate();
            }
        } else if (act == MotionEvent.ACTION_CANCEL) {
            menuDown = -1;
            menuPressed = -1;
            invalidate();
        }
        return true;
    }

    private void startLaunch(final int i) {
        if (launching >= 0) return;
        launching = i;
        launchT = 0f;
        launchFired = false;
        pressed = -1;
        launchAnim = ValueAnimator.ofFloat(0f, 1f);
        launchAnim.setDuration(240);
        launchAnim.setInterpolator(new AccelerateInterpolator(1.2f));
        launchAnim.addUpdateListener(a -> {
            launchT = (Float) a.getAnimatedValue();
            invalidate();
            if (launchT >= 1f && !launchFired) {
                launchFired = true;
                if (listener != null && launching >= 0 && launching < apps.size()) {
                    listener.onAppClick(apps.get(launching));
                }
            }
        });
        launchAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (launching >= 0) return true; // busy launching
        if (menuApp != null) return handleMenuTouch(e);
        detector.onTouchEvent(e);
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            pressed = -1;
            invalidate();
            // No fling started: settle onto the nearest icon right away.
            if (launching < 0 && scroller.isFinished()) snapToNearest();
        }
        return true;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            panX = scroller.getCurrX();
            panY = scroller.getCurrY();
            clampPan();
            invalidate();
        } else if (snapPending && !dragging) {
            snapPending = false;
            snapToNearest();
        }
    }

    private void snapToNearest() {
        int n = apps.size();
        if (n == 0) return;
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float dx = wx[i] - panX, dy = wy[i] - panY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bd) {
                bd = d2;
                best = i;
            }
        }
        animateTo(wx[best], wy[best]);
    }

    /** Springy glide of the pan position (slight overshoot, then settles). */
    private void animateTo(final float tx, final float ty) {
        if (panAnim != null) panAnim.cancel();
        final float ax = panX, ay = panY;
        if (Math.abs(tx - ax) < 0.5f && Math.abs(ty - ay) < 0.5f) return;
        panAnim = ValueAnimator.ofFloat(0f, 1f);
        panAnim.setDuration(340);
        panAnim.setInterpolator(new OvershootInterpolator(bounce * 2.25f));
        panAnim.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            panX = ax + (tx - ax) * t;
            panY = ay + (ty - ay) * t;
            invalidate();
        });
        panAnim.start();
    }
}
