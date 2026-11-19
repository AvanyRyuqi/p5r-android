package io.github.p5rrecomp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Full-bleed Direction A (Calling Card / Pause Menu) procedural vector background
 * with optional custom user wallpaper support and kinetic tab-switch slash animation.
 */
final class P5BackgroundView extends View {
    private Bitmap wallpaper;
    private Bitmap cachedDefaultArt;
    private int cachedW = 0, cachedH = 0;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix wpMatrix = new Matrix();
    private final Path railPath = new Path();
    private final Path seamWhitePath = new Path();
    private final Path seamRedPath = new Path();
    private final Path slashPath = new Path();

    private boolean settingsMode = false;
    private float scrimAlpha = 0f; // 0 = home, 1 = settings
    private float slashProgress = 1f; // 0 -> 1 kinetic sweep on tab switch
    private ValueAnimator slashAnim;
    private ValueAnimator scrimAnim;

    P5BackgroundView(Context context) {
        super(context);
    }

    void setWallpaper(Bitmap bmp) {
        this.wallpaper = bmp;
        updateWallpaperMatrix();
        invalidate();
    }

    void setSettingsMode(boolean settings, boolean animate) {
        if (this.settingsMode == settings && animate) {
            triggerSlash();
            return;
        }
        this.settingsMode = settings;
        final float target = settings ? 1f : 0f;
        if (!animate) {
            scrimAlpha = target;
            invalidate();
            return;
        }
        if (scrimAnim != null) scrimAnim.cancel();
        scrimAnim = ValueAnimator.ofFloat(scrimAlpha, target);
        scrimAnim.setDuration(180);
        scrimAnim.setInterpolator(new DecelerateInterpolator());
        scrimAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                scrimAlpha = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        scrimAnim.start();
        triggerSlash();
    }

    void triggerSlash() {
        if (slashAnim != null) slashAnim.cancel();
        slashProgress = 0f;
        slashAnim = ValueAnimator.ofFloat(0f, 1f);
        slashAnim.setDuration(210);
        slashAnim.setInterpolator(new DecelerateInterpolator(2.0f));
        slashAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                slashProgress = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        slashAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            rebuildStaticGeometry(w, h);
            rebuildDefaultArt(w, h);
            updateWallpaperMatrix();
        }
    }

    private void updateWallpaperMatrix() {
        if (wallpaper == null || getWidth() <= 0 || getHeight() <= 0) return;
        float vw = getWidth(), vh = getHeight();
        float bw = wallpaper.getWidth(), bh = wallpaper.getHeight();
        float scale = Math.max(vw / bw, vh / bh);
        float dx = (vw - bw * scale) * 0.5f;
        float dy = (vh - bh * scale) * 0.5f;
        wpMatrix.reset();
        wpMatrix.postScale(scale, scale);
        wpMatrix.postTranslate(dx, dy);
    }

    private void rebuildStaticGeometry(int w, int h) {
        // Left rail diagonal boundary: sits cleanly in the gutter between leftRail (236dp) and rightStage (+34dp pad)
        float railTopX = Math.min(w * 0.305f, P5Theme.dp(getContext(), 252));
        float railBotX = Math.min(w * 0.255f, P5Theme.dp(getContext(), 214));
        float seamW = P5Theme.dp(getContext(), 6.0f);
        float redW = P5Theme.dp(getContext(), 3.8f);

        railPath.reset();
        railPath.moveTo(0, 0);
        railPath.lineTo(railTopX, 0);
        railPath.lineTo(railBotX, h);
        railPath.lineTo(0, h);
        railPath.close();

        // Jagged cut-paper white seam along the diagonal split
        seamWhitePath.reset();
        seamWhitePath.moveTo(railTopX - seamW * 0.3f, 0);
        seamWhitePath.lineTo(railTopX + seamW, 0);
        seamWhitePath.lineTo(railBotX + seamW * 1.4f, h * 0.36f);
        seamWhitePath.lineTo(railBotX + seamW * 0.6f, h * 0.38f);
        seamWhitePath.lineTo(railBotX + seamW * 1.2f, h * 0.72f);
        seamWhitePath.lineTo(railBotX + seamW * 0.4f, h * 0.74f);
        seamWhitePath.lineTo(railBotX + seamW * 0.9f, h);
        seamWhitePath.lineTo(railBotX - seamW * 0.4f, h);
        seamWhitePath.close();

        seamRedPath.reset();
        seamRedPath.moveTo(railTopX + seamW, 0);
        seamRedPath.lineTo(railTopX + seamW + redW, 0);
        seamRedPath.lineTo(railBotX + seamW + redW, h);
        seamRedPath.lineTo(railBotX + seamW * 0.7f, h);
        seamRedPath.close();
    }

    /**
     * Pre-renders the procedural P5 Calling-Card art (crimson ground, dark crimson diagonal bands,
     * monochrome starburst, comic Ben-Day halftone dots, and bold graphic shards) once per resize.
     */
    private void rebuildDefaultArt(int w, int h) {
        if (cachedDefaultArt != null && cachedW == w && cachedH == h) return;
        if (cachedDefaultArt != null) cachedDefaultArt.recycle();
        cachedW = w;
        cachedH = h;
        cachedDefaultArt = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(cachedDefaultArt);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float d = P5Theme.dp(getContext(), 1f);

        // 1. Rich P5 Crimson ground
        c.drawColor(P5Theme.P5_CRIMSON_BG);

        // 2. Bold layered deep-crimson diagonal speed ribbons across right stage
        p.setStyle(Paint.Style.FILL);
        p.setColor(P5Theme.P5_DEEP_RED);
        Path band1 = new Path();
        band1.moveTo(w * 0.38f, 0);
        band1.lineTo(w * 0.63f, 0);
        band1.lineTo(w * 0.46f, h);
        band1.lineTo(w * 0.25f, h);
        band1.close();
        c.drawPath(band1, p);

        Path band2 = new Path();
        band2.moveTo(w * 0.78f, 0);
        band2.lineTo(w * 0.91f, 0);
        band2.lineTo(w * 0.74f, h);
        band2.lineTo(w * 0.64f, h);
        band2.close();
        c.drawPath(band2, p);

        // 3. Large asymmetric P5-style ink-black & white graphic star emblem on right-center stage
        float cx = w * 0.68f;
        float cy = h * 0.47f;
        float outerR = Math.min(w, h) * 0.48f;
        float innerR = outerR * 0.42f;
        Path star = makeStar(cx, cy, 5, outerR, innerR, -18f);
        // Solid offset white cutout behind star
        p.setColor(P5Theme.PAPER_WHITE);
        c.save();
        c.translate(10f * d, 8f * d);
        c.drawPath(star, p);
        c.restore();
        // Solid ink-black star body
        p.setColor(P5Theme.INK_BLACK);
        c.drawPath(star, p);
        // Inner crimson star punch-out
        p.setColor(P5Theme.P5_RED);
        c.drawPath(makeStar(cx, cy, 5, outerR * 0.62f, innerR * 0.56f, -14f), p);
        // Innermost ink core
        p.setColor(P5Theme.INK_BLACK);
        c.drawPath(makeStar(cx, cy, 5, outerR * 0.30f, innerR * 0.28f, -18f), p);

        // Sharp black accent wedge at top-right corner
        p.setColor(P5Theme.INK_BLACK);
        Path topCorner = new Path();
        topCorner.moveTo(w * 0.84f, 0);
        topCorner.lineTo(w, 0);
        topCorner.lineTo(w, h * 0.28f);
        topCorner.close();
        c.drawPath(topCorner, p);

        // 5. Procedural comic Ben-Day halftone dots radiating along the bottom-right & diagonal seam
        p.setColor(P5Theme.INK_BLACK);
        float step = 11f * d;
        for (float y = step * 0.5f; y < h; y += step) {
            for (float x = w * 0.26f; x < w; x += step) {
                // Density stronger near lower-right and diagonal edge
                float normX = (x - w * 0.26f) / (w * 0.74f);
                float normY = y / h;
                float edgeFactor = Math.max(0f, 1f - normX * 2.2f) * 0.7f;
                float cornerFactor = Math.max(0f, (normX + normY - 1.15f) * 1.3f);
                float intensity = Math.min(1f, Math.max(edgeFactor, cornerFactor));
                if (intensity > 0.12f) {
                    float radius = intensity * 3.1f * d;
                    c.drawCircle(x, y, radius, p);
                }
            }
        }
    }

    private static Path makeStar(float cx, float cy, int points, float outerR, float innerR, float rotDeg) {
        Path p = new Path();
        double step = Math.PI / points;
        double start = Math.toRadians(rotDeg - 90.0);
        for (int i = 0; i < points * 2; i++) {
            double r = (i % 2 == 0) ? outerR : innerR;
            double a = start + i * step;
            float x = cx + (float) (Math.cos(a) * r);
            float y = cy + (float) (Math.sin(a) * r);
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Draw custom wallpaper or procedural P5 Calling-Card art
        if (wallpaper != null && !wallpaper.isRecycled()) {
            canvas.drawColor(P5Theme.INK_BLACK);
            canvas.drawBitmap(wallpaper, wpMatrix, bitmapPaint);
            // Crisp dark scrim so foreground UI stays 100% readable over any user image
            int homeA = 110;
            int setA = 205;
            int a = Math.round(homeA + (setA - homeA) * scrimAlpha);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(android.graphics.Color.argb(a, 10, 10, 12));
            canvas.drawRect(0, 0, w, h, paint);
        } else if (cachedDefaultArt != null) {
            canvas.drawBitmap(cachedDefaultArt, 0, 0, null);
            // Darken right stage smoothly when on settings tabs so technical options are effortless to read
            if (scrimAlpha > 0.01f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(android.graphics.Color.argb(Math.round(182 * scrimAlpha), 10, 10, 12));
                canvas.drawRect(0, 0, w, h, paint);
            }
        }

        // 2. Kinetic slash sweep across stage on tab switches
        if (slashProgress < 1f) {
            float sweepX = w * (0.22f + 0.95f * slashProgress);
            float bandW = P5Theme.dp(getContext(), 44f) * (1f - slashProgress * 0.65f);
            float tilt = P5Theme.dp(getContext(), 90f);
            slashPath.reset();
            slashPath.moveTo(sweepX, 0);
            slashPath.lineTo(sweepX + bandW, 0);
            slashPath.lineTo(sweepX + bandW - tilt, h);
            slashPath.lineTo(sweepX - tilt, h);
            slashPath.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(android.graphics.Color.argb(Math.round(170 * (1f - slashProgress)), 246, 245, 240));
            canvas.drawPath(slashPath, paint);
        }

        // 3. Persistent matte-black diagonal left command rail + jagged white/crimson seam
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(P5Theme.INK_BLACK);
        canvas.drawPath(railPath, paint);

        paint.setColor(P5Theme.PAPER_WHITE);
        canvas.drawPath(seamWhitePath, paint);

        paint.setColor(P5Theme.P5_RED);
        canvas.drawPath(seamRedPath, paint);
    }
}
