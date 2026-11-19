package io.github.p5rrecomp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

/**
 * Full-bleed Direction A (Calling Card / Pause Menu) multi-layer vector background:
 * - Default Wallpaper: live 4-plane depth parallax reacting to phone gyro/gravity,
 *   tapered multi-angle opposing crimson ribbons with tab-switch kinetic surges,
 *   45-degree crimson-ink Ben-Day halftones clipped inside the ribbons, and
 *   subtle inner-star breathing (Approach B).
 * - Custom Wallpaper: completely static (sensors & frame loop automatically disabled).
 */
final class P5BackgroundView extends View implements SensorEventListener, Choreographer.FrameCallback {
    private Bitmap wallpaper;
    private Bitmap cachedHalftone;
    private int cachedW = 0, cachedH = 0;
    private float halftonePad = 0f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix wpMatrix = new Matrix();

    // Persistent geometry
    private final Path railPath = new Path();
    private final Path seamWhitePath = new Path();
    private final Path seamRedPath = new Path();
    private final Path slashPath = new Path();

    // Pre-built default wallpaper vector geometry (zero allocation in onDraw)
    private final Path ribbonStratum1Path = new Path();
    private final Path ribbonStratum2Path = new Path();
    private final Path ribbonAccentPath = new Path();
    private final Path ribbonClipPath = new Path();
    private final Path starOuterPath = new Path();
    private final Path starMidRedPath = new Path();
    private final Path starInnerCorePath = new Path();
    private final Path topWedgePath = new Path();

    private boolean settingsMode = false;
    private int wallpaperDimLevel = 1; // 0 = light, 1 = medium, 2 = dark
    private float scrimAlpha = 0f; // 0 = home, 1 = settings
    private float slashProgress = 1f; // 0 -> 1 kinetic sweep on tab switch
    private ValueAnimator slashAnim;
    private ValueAnimator scrimAnim;

    // Motion & gyro parallax state (active ONLY when Default Wallpaper is shown & activity resumed)
    private SensorManager sensorManager;
    private Sensor motionSensor;
    private int motionSensorType = 0;
    private boolean hostResumed = false;
    private boolean motionRunning = false;

    private final float[] rotMatrix = new float[9];
    private final float[] orientation = new float[3];
    private boolean hasBaseTilt = false;
    private float baseTiltX = 0f, baseTiltY = 0f;
    private float targetTiltX = 0f, targetTiltY = 0f;
    private float smoothTiltX = 0f, smoothTiltY = 0f;

    private long lastFrameNanos = 0L;
    private float animTime = 0f;
    private float ribbonBoostVel = 0f;
    private float ribbonKickDist = 0f;

    P5BackgroundView(Context context) {
        super(context);
        initSensor(context);
    }

    private void initSensor(Context context) {
        try {
            sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager == null) return;
            motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (motionSensor != null) {
                motionSensorType = Sensor.TYPE_GAME_ROTATION_VECTOR;
                return;
            }
            motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (motionSensor != null) {
                motionSensorType = Sensor.TYPE_ROTATION_VECTOR;
                return;
            }
            motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (motionSensor != null) {
                motionSensorType = Sensor.TYPE_GRAVITY;
                return;
            }
            motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (motionSensor != null) {
                motionSensorType = Sensor.TYPE_ACCELEROMETER;
            }
        } catch (Exception ignored) {
        }
    }

    void setHostResumed(boolean resumed) {
        this.hostResumed = resumed;
        updateMotionState();
    }

    private void updateMotionState() {
        boolean wantMotion = hostResumed && isAttachedToWindow() && (wallpaper == null || wallpaper.isRecycled());
        if (wantMotion == motionRunning) return;
        motionRunning = wantMotion;
        if (wantMotion) {
            hasBaseTilt = false;
            lastFrameNanos = 0L;
            if (sensorManager != null && motionSensor != null) {
                try {
                    sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME);
                } catch (Exception ignored) {
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            if (sensorManager != null) {
                try {
                    sensorManager.unregisterListener(this);
                } catch (Exception ignored) {
                }
            }
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateMotionState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateMotionState();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!motionRunning) return;
        float dt = (lastFrameNanos == 0L) ? 0.016f : (frameTimeNanos - lastFrameNanos) * 1e-9f;
        lastFrameNanos = frameTimeNanos;
        if (dt < 0f) dt = 0f;
        if (dt > 0.05f) dt = 0.05f;

        animTime += dt;
        if (ribbonBoostVel > 0.001f) {
            ribbonKickDist += ribbonBoostVel * dt;
            ribbonBoostVel *= (float) Math.exp(-5.5f * dt);
        } else {
            ribbonBoostVel = 0f;
        }
        // Critically damped smooth spring tracking toward gyro tilt
        float k = 1f - (float) Math.exp(-8.5f * dt);
        smoothTiltX += (targetTiltX - smoothTiltX) * k;
        smoothTiltY += (targetTiltY - smoothTiltY) * k;

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @SuppressWarnings("deprecation")
    private int currentDisplayRotation() {
        try {
            Display disp = getDisplay();
            if (disp != null) return disp.getRotation();
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) return wm.getDefaultDisplay().getRotation();
        } catch (Exception ignored) {
        }
        return Surface.ROTATION_90;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!motionRunning || event == null || event.values == null) return;
        float rawX = 0f;
        float rawY = 0f;
        int rot = currentDisplayRotation();

        if (motionSensorType == Sensor.TYPE_GAME_ROTATION_VECTOR
                || motionSensorType == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values);
            SensorManager.getOrientation(rotMatrix, orientation);
            // orientation[1] = pitch (device Y), orientation[2] = roll (device X)
            float pitch = orientation[1];
            float roll = orientation[2];
            if (rot == Surface.ROTATION_270) {
                rawX = pitch;
                rawY = -roll;
            } else {
                // Default landscape (ROTATION_90)
                rawX = -pitch;
                rawY = roll;
            }
        } else {
            // Gravity / Accelerometer normalized fallback
            float gx = event.values[0] / 9.81f;
            float gy = event.values[1] / 9.81f;
            if (rot == Surface.ROTATION_270) {
                rawX = -gy;
                rawY = gx;
            } else {
                rawX = gy;
                rawY = -gx;
            }
        }

        if (!hasBaseTilt) {
            baseTiltX = rawX;
            baseTiltY = rawY;
            hasBaseTilt = true;
        } else {
            // Slowly adapt resting baseline to however the user naturally holds the phone
            baseTiltX += (rawX - baseTiltX) * 0.012f;
            baseTiltY += (rawY - baseTiltY) * 0.012f;
        }

        float dx = (rawX - baseTiltX) * 2.8f;
        float dy = (rawY - baseTiltY) * 2.8f;
        // Soft saturation into [-1, +1] so hard tilts never clip abruptly
        targetTiltX = dx / (1f + Math.abs(dx));
        targetTiltY = dy / (1f + Math.abs(dy));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    void setWallpaper(Bitmap bmp) {
        this.wallpaper = bmp;
        updateWallpaperMatrix();
        updateMotionState();
        invalidate();
    }

    void setWallpaperDim(int level) {
        this.wallpaperDimLevel = Math.max(0, Math.min(2, level));
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
        ribbonBoostVel = Math.min(1.6f, ribbonBoostVel + 1.0f);
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
            rebuildDefaultLayers(w, h);
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
     * Pre-builds reusable origin-centered star paths, oversized top-right ink wedge,
     * and the cached transparent Ben-Day halftone bitmap once per resize.
     */
    private void rebuildDefaultLayers(int w, int h) {
        if (cachedHalftone != null && cachedW == w && cachedH == h) return;
        if (cachedHalftone != null) cachedHalftone.recycle();
        cachedW = w;
        cachedH = h;
        float d = P5Theme.dp(getContext(), 1f);

        // Origin-centered star layers (translated per frame for stacked 3D depth parallax)
        float outerR = Math.min(w, h) * 0.48f;
        float innerR = outerR * 0.42f;
        buildStar(starOuterPath, 0f, 0f, 5, outerR, innerR, -18f);
        buildStar(starMidRedPath, 0f, 0f, 5, outerR * 0.62f, innerR * 0.56f, -14f);
        buildStar(starInnerCorePath, 0f, 0f, 5, outerR * 0.30f, innerR * 0.28f, -18f);

        // Oversized top-right ink wedge so foreground gyro shift never exposes a corner gap
        float wedgeOver = 42f * d;
        topWedgePath.reset();
        topWedgePath.moveTo(w * 0.84f, -wedgeOver);
        topWedgePath.lineTo(w + wedgeOver, -wedgeOver);
        topWedgePath.lineTo(w + wedgeOver, h * 0.28f + wedgeOver);
        topWedgePath.lineTo(w, h * 0.28f);
        topWedgePath.close();

        // Pre-render 45-degree crimson-ink Ben-Day halftone screen once (clipped inside ribbons at draw time)
        halftonePad = 12f * d;
        int padPx = Math.round(halftonePad);
        int bw = w + padPx * 2;
        int bh = h + padPx * 2;
        cachedHalftone = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(cachedHalftone);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(android.graphics.Color.argb(132, 36, 2, 8));

        float step = 9.5f * d;
        float invSqrt2 = 0.70710678f;
        float diag = (float) Math.hypot(bw, bh);
        float originX = bw * 0.5f;
        float originY = bh * 0.5f;
        float minX = w * 0.16f;
        for (float u = -diag * 0.65f; u <= diag * 0.65f; u += step) {
            for (float v = -diag * 0.65f; v <= diag * 0.65f; v += step) {
                float sx = originX + (u - v) * invSqrt2;
                float sy = originY + (u + v) * invSqrt2;
                if (sx < minX || sx > bw + step || sy < -step || sy > bh + step) continue;
                float normX = (sx - halftonePad - w * 0.24f) / (w * 0.76f);
                float normY = (sy - halftonePad) / h;
                // Slightly bolder print grain near the diagonal seam and bottom-right, finer in center
                float edgeGrad = Math.max(0f, 1f - normX * 1.45f) * 0.75f;
                float cornerGrad = Math.max(0f, (normX + normY - 0.95f) * 0.95f);
                float intensity = Math.min(1f, Math.max(0.38f, Math.max(edgeGrad, cornerGrad)));
                float radius = intensity * 2.85f * d;
                c.drawCircle(sx, sy, radius, p);
            }
        }
    }

    private static void buildStar(Path p, float cx, float cy, int points, float outerR, float innerR, float rotDeg) {
        p.reset();
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
    }

    private static float posMod(float v, float m) {
        float r = v % m;
        return (r < 0f) ? (r + m) : r;
    }

    private static void addTaperedRibbon(Path target, float topLeftX, float topWidth,
                                         float bottomWidth, float tiltDx, float h) {
        float over = 24f;
        float bottomLeftX = topLeftX - tiltDx;
        target.moveTo(topLeftX, -over);
        target.lineTo(topLeftX + topWidth, -over);
        target.lineTo(bottomLeftX + bottomWidth, h + over);
        target.lineTo(bottomLeftX, h + over);
        target.close();
    }

    private void drawDefaultDynamicWallpaper(Canvas canvas, int w, int h) {
        float d = P5Theme.dp(getContext(), 1f);
        // Calm parallax gently when on Settings tabs so text stays rock-steady
        float gyroScale = 1f - 0.65f * scrimAlpha;
        float gx = smoothTiltX * gyroScale;
        float gy = smoothTiltY * gyroScale;

        // 1. Deepest Layer: P5 Crimson ground + tapered multi-angle opposing cut-paper ribbons
        canvas.drawColor(P5Theme.P5_CRIMSON_BG);
        paint.setStyle(Paint.Style.FILL);

        // Stratum 1: Wide slow-drifting tapered slabs (rightward, ~23s non-harmonic loop, bold 38-deg slant)
        ribbonStratum1Path.reset();
        float period1 = w * 0.88f;
        float drift1 = posMod(animTime * (period1 / 23f) + ribbonKickDist * (w * 0.22f), period1);
        float tilt1 = w * 0.34f;
        float start1 = w * 0.12f + drift1 + gx * 6f * d;
        for (int k = -1; k <= 2; k++) {
            float base = start1 + k * period1;
            addTaperedRibbon(ribbonStratum1Path, base, w * 0.24f, w * 0.15f, tilt1, h);
            addTaperedRibbon(ribbonStratum1Path, base + w * 0.50f, w * 0.085f, w * 0.145f, tilt1 * 0.88f, h);
        }
        paint.setColor(P5Theme.P5_DEEP_RED);
        canvas.drawPath(ribbonStratum1Path, paint);

        // Stratum 2: Counter-drifting darker oxblood tapered shard (leftward, ~17s loop, steeper angle)
        ribbonStratum2Path.reset();
        float period2 = w * 0.94f;
        float drift2 = posMod(-(animTime * (period2 / 17f) + ribbonKickDist * (w * 0.28f)), period2);
        float tilt2 = w * 0.24f;
        float start2 = w * 0.22f + drift2 + gx * 9f * d;
        for (int k = -1; k <= 2; k++) {
            float base = start2 + k * period2;
            addTaperedRibbon(ribbonStratum2Path, base, w * 0.135f, w * 0.065f, tilt2, h);
        }
        paint.setColor(android.graphics.Color.rgb(122, 0, 12));
        canvas.drawPath(ribbonStratum2Path, paint);

        // 2. Screen-Tone Halftones: 45-degree crimson-ink Ben-Day dots clipped strictly inside the ribbons
        // Anchored to the paper plane (minimal 3dp gyro) so ribbons reveal the print texture as they glide
        if (cachedHalftone != null && !cachedHalftone.isRecycled()) {
            ribbonClipPath.reset();
            ribbonClipPath.addPath(ribbonStratum1Path);
            ribbonClipPath.addPath(ribbonStratum2Path);
            canvas.save();
            canvas.clipPath(ribbonClipPath);
            canvas.drawBitmap(cachedHalftone,
                    -halftonePad + gx * 3f * d,
                    -halftonePad + gy * 3f * d,
                    null);
            canvas.restore();
        }

        // Stratum 3: Thin opposing ink-black accent slash (~11s loop, aggressive slant)
        ribbonAccentPath.reset();
        float period3 = w * 1.18f;
        float drift3 = posMod(-(animTime * (period3 / 11f) + ribbonKickDist * (w * 0.42f)), period3);
        float tilt3 = w * 0.44f;
        float start3 = w * 0.30f + drift3 + gx * 11f * d;
        for (int k = -1; k <= 2; k++) {
            float base = start3 + k * period3;
            addTaperedRibbon(ribbonAccentPath, base, w * 0.032f, w * 0.011f, tilt3, h);
        }
        paint.setColor(android.graphics.Color.argb(110, 10, 10, 12));
        canvas.drawPath(ribbonAccentPath, paint);

        // 3. Hero Layer: Stacked 3D Cut-Paper P5 Star Emblem (Approach B)
        // Outer star stays anchored when phone is still; inner crimson star has subtle slow life
        float cx = w * 0.68f;
        float cy = h * 0.47f;

        // Plane 3a: Solid offset white cutout behind star (shifts less than black body -> dynamic paper shadow)
        paint.setColor(P5Theme.PAPER_WHITE);
        canvas.save();
        canvas.translate(cx + 10f * d + gx * 12f * d, cy + 8f * d + gy * 12f * d);
        canvas.drawPath(starOuterPath, paint);
        canvas.restore();

        // Plane 3b: Solid ink-black main star body
        paint.setColor(P5Theme.INK_BLACK);
        canvas.save();
        canvas.translate(cx + gx * 22f * d, cy + gy * 22f * d);
        canvas.drawPath(starOuterPath, paint);
        canvas.restore();

        // Plane 3c: Inner crimson star with subtle slow counter-twist & breath (Approach B)
        float innerTwist = 2.6f * (float) Math.sin(animTime * 0.85f);
        float innerPulse = 1.0f + 0.022f * (float) Math.sin(animTime * 0.85f + 0.7f);
        paint.setColor(P5Theme.P5_RED);
        canvas.save();
        canvas.translate(cx + gx * 27f * d, cy + gy * 27f * d);
        canvas.rotate(innerTwist);
        canvas.scale(innerPulse, innerPulse);
        canvas.drawPath(starMidRedPath, paint);
        canvas.restore();

        // Plane 3d: Innermost ink core
        paint.setColor(P5Theme.INK_BLACK);
        canvas.save();
        canvas.translate(cx + gx * 31f * d, cy + gy * 31f * d);
        canvas.drawPath(starInnerCorePath, paint);
        canvas.restore();

        // 4. Foreground Accent: Sharp black wedge at top-right corner
        paint.setColor(P5Theme.INK_BLACK);
        canvas.save();
        canvas.translate(gx * 26f * d, gy * 26f * d);
        canvas.drawPath(topWedgePath, paint);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 1. Draw static custom wallpaper OR dynamic multi-layer Default Wallpaper
        if (wallpaper != null && !wallpaper.isRecycled()) {
            canvas.drawColor(P5Theme.INK_BLACK);
            canvas.drawBitmap(wallpaper, wpMatrix, bitmapPaint);
            // User-tunable dim scrim on Home (Light / Medium / Dark); always legible on Settings tabs
            int homeA = (wallpaperDimLevel == 0) ? 50 : (wallpaperDimLevel == 2 ? 172 : 112);
            int setA = (wallpaperDimLevel == 0) ? 188 : (wallpaperDimLevel == 2 ? 228 : 208);
            int a = Math.round(homeA + (setA - homeA) * scrimAlpha);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(android.graphics.Color.argb(a, 10, 10, 12));
            canvas.drawRect(0, 0, w, h, paint);
        } else {
            drawDefaultDynamicWallpaper(canvas, w, h);
            // Darken right stage smoothly when on settings tabs so technical options are effortless to read
            if (scrimAlpha > 0.01f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(android.graphics.Color.argb(Math.round(182 * scrimAlpha), 10, 10, 12));
                canvas.drawRect(0, 0, w, h, paint);
            }
        }

        // 2. Kinetic slash sweep across stage on tab switches / resume
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
