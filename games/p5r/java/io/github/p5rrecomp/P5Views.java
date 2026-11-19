package io.github.p5rrecomp;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Custom Direction A (Persona 5 Pause / Calling Card) Canvas-drawn UI primitives:
 * sharp hand-cut paper polygons, 0px-blur offset ink shadows, zero rounded corners.
 */
final class P5Views {

    // ---- 1. Ransom-Cut / Calling-Card Masthead ("PERSONA 5" / "ROYAL") ----

    static final class CutTitleView extends View {
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path bgSlash = new Path();
        private static final float[] ANGLES = {-4.5f, 3.0f, -2.5f, 4.0f, -3.5f, 2.5f, -4.0f, 0f, 5.0f};

        CutTitleView(Context context) {
            super(context);
            textPaint.setTypeface(P5Theme.displayBold());
            textPaint.setTextAlign(Paint.Align.CENTER);
            borderPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int h = P5Theme.dpx(getContext(), 58);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();

            // Red jagged backing slash behind the title
            bgSlash.reset();
            bgSlash.moveTo(2 * d, 12 * d);
            bgSlash.lineTo(w - 14 * d, 4 * d);
            bgSlash.lineTo(w - 22 * d, 36 * d);
            bgSlash.lineTo(0, 42 * d);
            bgSlash.close();
            boxPaint.setStyle(Paint.Style.FILL);
            boxPaint.setColor(P5Theme.P5_RED);
            c.drawPath(bgSlash, boxPaint);

            // Row 1: "PERSONA 5" in alternating cut-paper tiles
            String line1 = "PERSONA 5";
            float tileW = Math.min(22.5f * d, (w - 20 * d) / 9.2f);
            float tileH = 26f * d;
            float x = 6 * d;
            float y = 7 * d;
            textPaint.setTextSize(tileH * 0.72f);
            borderPaint.setStrokeWidth(1.8f * d);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textBaselineOff = -(fm.ascent + fm.descent) * 0.5f;

            for (int i = 0; i < line1.length(); i++) {
                char ch = line1.charAt(i);
                if (ch == ' ') {
                    x += tileW * 0.45f;
                    continue;
                }
                boolean isFive = (ch == '5');
                boolean whiteTile = (i % 2 == 0) && !isFive;
                float tw = isFive ? tileW * 1.18f : tileW;
                float th = isFive ? tileH * 1.15f : tileH;
                float ty = isFive ? y - 2 * d : y + ((i % 2 == 0) ? 0f : 2f * d);

                c.save();
                c.rotate(ANGLES[i % ANGLES.length], x + tw * 0.5f, ty + th * 0.5f);
                // Hard offset shadow
                boxPaint.setColor(P5Theme.INK_BLACK);
                c.drawRect(x + 2.5f * d, ty + 2.5f * d, x + tw + 2.5f * d, ty + th + 2.5f * d, boxPaint);
                // Tile fill
                boxPaint.setColor(isFive ? P5Theme.P5_RED : (whiteTile ? P5Theme.PAPER_WHITE : P5Theme.INK_BLACK));
                c.drawRect(x, ty, x + tw, ty + th, boxPaint);
                // Crisp border
                borderPaint.setColor(isFive ? P5Theme.PAPER_WHITE : (whiteTile ? P5Theme.INK_BLACK : P5Theme.PAPER_WHITE));
                c.drawRect(x, ty, x + tw, ty + th, borderPaint);
                // Letter
                textPaint.setColor(whiteTile ? P5Theme.INK_BLACK : P5Theme.PAPER_WHITE);
                c.drawText(String.valueOf(ch), x + tw * 0.5f, ty + th * 0.5f + textBaselineOff, textPaint);
                c.restore();
                x += tw + 1.2f * d;
            }

            // Row 2: Slanted "ROYAL" cut-paper ribbon tucked underneath
            float rx = 18 * d, ry = 36 * d, rw = 96 * d, rh = 18 * d;
            Path royalPoly = new Path();
            P5Theme.setSlantedQuad(royalPoly, rx, ry, rx + rw, ry + rh, 5 * d, 5 * d);
            boxPaint.setColor(P5Theme.PAPER_WHITE);
            c.save();
            c.translate(2 * d, 2 * d);
            c.drawPath(royalPoly, boxPaint);
            c.restore();
            boxPaint.setColor(P5Theme.INK_BLACK);
            c.drawPath(royalPoly, boxPaint);

            textPaint.setTextSize(12.5f * d);
            textPaint.setColor(P5Theme.ACID_YELLOW);
            Paint.FontMetrics fm2 = textPaint.getFontMetrics();
            float b2 = -(fm2.ascent + fm2.descent) * 0.5f;
            c.drawText("R O Y A L", rx + rw * 0.5f, ry + rh * 0.5f + b2, textPaint);
        }
    }

    // ---- 2. Slanted Left-Rail Navigation Tab (P5 Pause Menu Style) ----

    static final class NavTabView extends View {
        private final String label;
        private final String subTag;
        private boolean active = false;
        private boolean pressed = false;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path mainPoly = new Path();
        private final Path backSlash = new Path();

        NavTabView(Context context, String label, String subTag) {
            super(context);
            this.label = label;
            this.subTag = subTag;
            setClickable(true);
            setFocusable(true);
            textPaint.setTypeface(P5Theme.displayBold());
        }

        void setActive(boolean a, boolean animate) {
            if (this.active == a && animate) return;
            this.active = a;
            float targetX = a ? P5Theme.dp(getContext(), 9f) : 0f;
            if (animate) {
                ObjectAnimator anim = ObjectAnimator.ofFloat(this, "translationX", targetX);
                anim.setDuration(150);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.start();
            } else {
                setTranslationX(targetX);
            }
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                invalidate();
            }
            return super.onTouchEvent(e);
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int h = P5Theme.dpx(getContext(), 40);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();
            float skew = 11f * d;
            float padR = 14f * d;
            float padV = 3f * d;

            if (active || isFocused()) {
                // Jagged crimson + yellow backing slash behind active item
                P5Theme.setCallingCardQuad(backSlash, 2 * d, padV - 1 * d, w - padR + 5 * d, h - padV + 2 * d,
                        4 * d, -1 * d, 2 * d, 2 * d, -6 * d, 1 * d, -2 * d, -1 * d);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(active ? P5Theme.P5_RED : P5Theme.ACID_YELLOW);
                c.drawPath(backSlash, paint);

                // Stark paper-white active polygon
                P5Theme.setSlantedQuad(mainPoly, 4 * d, padV, w - padR, h - padV, skew * 0.45f, skew);
                paint.setColor(pressed ? P5Theme.ACID_YELLOW : P5Theme.PAPER_WHITE);
                c.drawPath(mainPoly, paint);

                // Ink outline
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * d);
                paint.setColor(P5Theme.INK_BLACK);
                c.drawPath(mainPoly, paint);
            } else {
                // Subtle dark cut slab when idle
                P5Theme.setSlantedQuad(mainPoly, 4 * d, padV + 1 * d, w - padR - 6 * d, h - padV - 1 * d,
                        skew * 0.35f, skew * 0.85f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(pressed ? P5Theme.P5_DEEP_RED : Color.argb(150, 22, 22, 28));
                c.drawPath(mainPoly, paint);

                // Sharp crimson left tick bar
                Path tick = new Path();
                P5Theme.setSlantedQuad(tick, 4 * d, padV + 1 * d, 10 * d, h - padV - 1 * d, 2 * d, 2 * d);
                paint.setColor(P5Theme.P5_RED);
                c.drawPath(tick, paint);
            }

            // Tab label
            textPaint.setTextSize((active ? 17.5f : 15.5f) * d);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(active ? P5Theme.INK_BLACK : P5Theme.PAPER_WHITE);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float base = h * 0.5f - (fm.ascent + fm.descent) * 0.5f;
            c.drawText(label, 19 * d, base, textPaint);

            // Optional compact right index tag ("01", "02"...)
            if (subTag != null && !subTag.isEmpty()) {
                textPaint.setTextSize(10f * d);
                textPaint.setTextAlign(Paint.Align.RIGHT);
                textPaint.setColor(active ? P5Theme.P5_RED : P5Theme.MUTED_TEXT);
                c.drawText(subTag, w - padR - 14 * d, base, textPaint);
            }
        }
    }

    // ---- 3. Signature Cut-Paper Slash Action Button (PLAY / Secondary Actions) ----

    static final class SlashButton extends View {
        private String text;
        private final boolean primary; // true = massive crimson/white PLAY slab, false = compact action button
        private boolean warningStyle = false;
        private boolean pressed = false;
        private int fixedHeightDp;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path poly = new Path();
        private final Path shadowPoly = new Path();
        private final Path accentPoly = new Path();

        SlashButton(Context context, String text, boolean primary, int heightDp) {
            super(context);
            this.text = text;
            this.primary = primary;
            this.fixedHeightDp = heightDp;
            setClickable(true);
            setFocusable(true);
            textPaint.setTypeface(primary ? P5Theme.displayBold() : P5Theme.condensedBold());
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setText(String t) {
            this.text = t == null ? "" : t;
            invalidate();
        }

        void setWarningStyle(boolean warn) {
            if (this.warningStyle != warn) {
                this.warningStyle = warn;
                invalidate();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled()) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                setScaleX(0.96f);
                setScaleY(0.96f);
                invalidate();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                setScaleX(1f);
                setScaleY(1f);
                invalidate();
            }
            return super.onTouchEvent(e);
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            float d = P5Theme.dp(getContext(), 1f);
            int h = Math.round(fixedHeightDp * d);
            int wMode = MeasureSpec.getMode(wSpec);
            int wSize = MeasureSpec.getSize(wSpec);
            int w;
            if (wMode == MeasureSpec.EXACTLY) {
                w = wSize;
            } else {
                textPaint.setTextSize((primary ? 20f : 13.5f) * d);
                float tw = textPaint.measureText(text) + (primary ? 64 * d : 34 * d);
                int minW = Math.round((primary ? 170 : 88) * d);
                w = Math.max(minW, Math.round(tw));
                if (wMode == MeasureSpec.AT_MOST) w = Math.min(w, wSize);
            }
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();
            float sh = pressed ? 1.5f * d : (primary ? 5.5f * d : 3.5f * d);
            float skew = (primary ? 14f : 8f) * d;
            float l = 2 * d, t = 2 * d, r = w - sh - 2 * d, b = h - sh - 2 * d;

            // Hard 0px-blur offset shadow
            P5Theme.setSlantedQuad(shadowPoly, l + sh, t + sh, r + sh, b + sh, skew, skew);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(primary ? P5Theme.INK_BLACK : P5Theme.INK_BLACK);
            c.drawPath(shadowPoly, paint);

            // Main fill
            P5Theme.setSlantedQuad(poly, l, t, r, b, skew, skew);
            int fillColor;
            int txtColor;
            int strokeColor;
            if (primary) {
                if (warningStyle) {
                    fillColor = pressed ? P5Theme.PAPER_WHITE : P5Theme.ACID_YELLOW;
                    txtColor = P5Theme.INK_BLACK;
                    strokeColor = P5Theme.INK_BLACK;
                } else {
                    fillColor = pressed ? P5Theme.PAPER_WHITE : P5Theme.P5_RED;
                    txtColor = pressed ? P5Theme.INK_BLACK : P5Theme.PAPER_WHITE;
                    strokeColor = P5Theme.PAPER_WHITE;
                }
            } else {
                fillColor = pressed ? P5Theme.P5_RED : P5Theme.PAPER_WHITE;
                txtColor = pressed ? P5Theme.PAPER_WHITE : P5Theme.INK_BLACK;
                strokeColor = P5Theme.INK_BLACK;
            }
            if (isFocused()) strokeColor = P5Theme.ACID_YELLOW;

            paint.setColor(fillColor);
            c.drawPath(poly, paint);

            // Bold diagonal corner slash inside primary PLAY button
            if (primary && !warningStyle) {
                accentPoly.reset();
                accentPoly.moveTo(l + skew + 6 * d, t);
                accentPoly.lineTo(l + skew + 16 * d, t);
                accentPoly.lineTo(l + 16 * d, b);
                accentPoly.lineTo(l + 6 * d, b);
                accentPoly.close();
                paint.setColor(pressed ? P5Theme.P5_RED : P5Theme.PAPER_WHITE);
                c.drawPath(accentPoly, paint);
            }

            // Crisp border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((primary ? 2.8f : 1.8f) * d);
            paint.setColor(strokeColor);
            c.drawPath(poly, paint);

            // Label
            textPaint.setTextSize((primary ? (fixedHeightDp >= 54 ? 21f : 16f) : 13f) * d);
            textPaint.setColor(txtColor);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float base = (t + b) * 0.5f - (fm.ascent + fm.descent) * 0.5f;
            float cx = (l + r) * 0.5f + (primary && !warningStyle ? 4 * d : 0f);
            c.drawText(text.toUpperCase(java.util.Locale.ROOT), cx, base, textPaint);
        }
    }

    // ---- 4. Direct Segmented Angled Control (Tap exact option directly) ----

    static final class SegmentedControl extends View {
        interface OnPickListener {
            void onPick(int index);
        }

        private String[] labels;
        private int selected = 0;
        private OnPickListener listener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path segPath = new Path();

        SegmentedControl(Context context, String[] labels, int selected, OnPickListener listener) {
            super(context);
            this.labels = labels;
            this.selected = selected;
            this.listener = listener;
            setClickable(true);
            setFocusable(true);
            textPaint.setTypeface(P5Theme.condensedBold());
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setLabelsAndPick(String[] labels, int pick) {
            this.labels = labels;
            this.selected = pick;
            invalidate();
        }

        void setSelectedIndex(int idx) {
            this.selected = idx;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!isEnabled() || labels == null || labels.length == 0) return false;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float w = getWidth();
                if (w > 0) {
                    int idx = (int) ((e.getX() / w) * labels.length);
                    idx = Math.max(0, Math.min(labels.length - 1, idx));
                    if (idx != selected) {
                        selected = idx;
                        invalidate();
                        if (listener != null) listener.onPick(idx);
                    } else if (labels.length == 2) {
                        // Tapping the already-active side of a 2-option toggle flips it cleanly
                        selected = 1 - selected;
                        invalidate();
                        if (listener != null) listener.onPick(selected);
                    }
                }
                performClick();
                return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            float d = P5Theme.dp(getContext(), 1f);
            int count = labels != null ? labels.length : 2;
            int minSegW = (count <= 2) ? 74 : (count == 3 ? 62 : 52);
            int desiredW = Math.round((count * minSegW + 14) * d);
            int wMode = MeasureSpec.getMode(wSpec);
            int wSize = MeasureSpec.getSize(wSpec);
            int w = (wMode == MeasureSpec.EXACTLY) ? wSize
                    : (wMode == MeasureSpec.AT_MOST ? Math.min(desiredW, wSize) : desiredW);
            setMeasuredDimension(w, Math.round(36 * d));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (labels == null || labels.length == 0) return;
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();
            float skew = 6f * d;
            float pad = 1.5f * d;
            float usableW = w - skew - pad * 2f;
            float segW = usableW / labels.length;

            // Outer dark ink frame
            P5Theme.setSlantedQuad(segPath, pad, pad, w - pad, h - pad, skew, skew);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(P5Theme.INK_BLACK);
            c.drawPath(segPath, paint);

            for (int i = 0; i < labels.length; i++) {
                float sl = pad + i * segW;
                float sr = sl + segW + skew;
                boolean act = (i == selected);
                P5Theme.setSlantedQuad(segPath, sl, pad, sr, h - pad, skew, skew);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(act ? P5Theme.P5_RED : P5Theme.INK_ELEVATED);
                c.drawPath(segPath, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * d);
                paint.setColor(act ? P5Theme.PAPER_WHITE : P5Theme.BORDER_SUBTLE);
                c.drawPath(segPath, paint);

                // Compact label
                String txt = labels[i];
                float ts = (labels.length >= 5 ? 11.5f : 12.5f) * d;
                textPaint.setTextSize(ts);
                textPaint.setColor(act ? P5Theme.PAPER_WHITE : P5Theme.MUTED_TEXT);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float base = h * 0.5f - (fm.ascent + fm.descent) * 0.5f;
                c.drawText(txt, sl + (segW + skew) * 0.5f, base, textPaint);
            }
        }
    }

    // ---- 5. Compact Slanted Spec Tag (Hero Home Screen) ----

    static final class SpecBadge extends View {
        private String key;
        private String value;
        private boolean accent;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path poly = new Path();

        SpecBadge(Context context, String key, String value, boolean accent) {
            super(context);
            this.key = key;
            this.value = value;
            this.accent = accent;
            textPaint.setTypeface(P5Theme.monoBold());
        }

        void setValue(String v, boolean accent) {
            this.value = v;
            this.accent = accent;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            float d = P5Theme.dp(getContext(), 1f);
            textPaint.setTextSize(11f * d);
            float kw = textPaint.measureText(key);
            float vw = textPaint.measureText(value);
            int w = Math.round(kw + vw + 28 * d);
            int h = Math.round(28 * d);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();
            float skew = 6f * d;

            // Shadow
            P5Theme.setSlantedQuad(poly, 2.5f * d, 2.5f * d, w, h, skew, skew);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(P5Theme.INK_BLACK);
            c.drawPath(poly, paint);

            // Body
            P5Theme.setSlantedQuad(poly, 0, 0, w - 2.5f * d, h - 2.5f * d, skew, skew);
            paint.setColor(accent ? P5Theme.PAPER_WHITE : P5Theme.INK_BLACK);
            c.drawPath(poly, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * d);
            paint.setColor(accent ? P5Theme.INK_BLACK : P5Theme.PAPER_WHITE);
            c.drawPath(poly, paint);

            textPaint.setTextSize(10.5f * d);
            textPaint.setTextAlign(Paint.Align.LEFT);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float base = (h - 2.5f * d) * 0.5f - (fm.ascent + fm.descent) * 0.5f;
            float x = 10 * d;
            textPaint.setColor(accent ? P5Theme.P5_RED : P5Theme.MUTED_TEXT);
            c.drawText(key, x, base, textPaint);
            x += textPaint.measureText(key) + 5 * d;
            textPaint.setColor(accent ? P5Theme.INK_BLACK : P5Theme.PAPER_WHITE);
            c.drawText(value, x, base, textPaint);
        }
    }

    // ---- 6. Angled Section Header Banner ----

    static final class SectionHeaderView extends View {
        private String title;
        private String subtitle;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path poly = new Path();

        SectionHeaderView(Context context, String title, String subtitle) {
            super(context);
            this.title = title;
            this.subtitle = subtitle;
        }

        void setHeader(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(MeasureSpec.getSize(wSpec), P5Theme.dpx(getContext(), 44));
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();

            // Red accent bar on left
            P5Theme.setSlantedQuad(poly, 0, 5 * d, 10 * d, h - 7 * d, 4 * d, 4 * d);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(P5Theme.P5_RED);
            c.drawPath(poly, paint);

            // Title text in bold display italic
            textPaint.setTypeface(P5Theme.displayBold());
            textPaint.setTextSize(21f * d);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(P5Theme.PAPER_WHITE);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float base = (h - 4 * d) * 0.5f - (fm.ascent + fm.descent) * 0.5f;
            c.drawText(title.toUpperCase(java.util.Locale.ROOT), 17 * d, base, textPaint);

            // Subtitle on right
            if (subtitle != null && !subtitle.isEmpty()) {
                textPaint.setTypeface(P5Theme.monoBold());
                textPaint.setTextSize(10.5f * d);
                textPaint.setTextAlign(Paint.Align.RIGHT);
                textPaint.setColor(P5Theme.ACID_YELLOW);
                c.drawText(subtitle, w - 6 * d, base, textPaint);
            }

            // Sharp bottom cut rule
            paint.setColor(P5Theme.PAPER_WHITE);
            c.drawRect(0, h - 2.5f * d, w, h - 1f * d, paint);
        }
    }

    // ---- 7. Angled Ink Container Row Background ----

    static final class AngledRowLayout extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path poly = new Path();
        private boolean highlight = false;

        AngledRowLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            int hPad = P5Theme.dpx(context, 14);
            int vPad = P5Theme.dpx(context, 9);
            setPadding(hPad, vPad, hPad, vPad);
        }

        void setHighlight(boolean h) {
            this.highlight = h;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = P5Theme.dp(getContext(), 1f);
            float w = getWidth(), h = getHeight();
            float skew = 7f * d;

            // Hard dark slab
            P5Theme.setSlantedQuad(poly, 0, 0, w - 3 * d, h - 2 * d, skew * 0.5f, skew);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(highlight ? Color.argb(245, 28, 12, 14) : P5Theme.INK_SURFACE);
            c.drawPath(poly, paint);

            // Crisp border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * d);
            paint.setColor(highlight ? P5Theme.P5_RED : P5Theme.BORDER_SUBTLE);
            c.drawPath(poly, paint);

            // Tiny crimson left notch
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(highlight ? P5Theme.ACID_YELLOW : P5Theme.P5_RED);
            Path notch = new Path();
            P5Theme.setSlantedQuad(notch, 0, 0, 5 * d, h - 2 * d, skew * 0.5f, skew * 0.5f);
            c.drawPath(notch, paint);
        }
    }

    // ---- 8. Custom P5 Modal Picker Dialog (Replaces stock Android AlertDialog for lists) ----

    interface OnModalPick {
        void onPick(int index);
    }

    static void showPickerDialog(Context ctx, String title, String[] labels, int current, final OnModalPick cb) {
        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        int pad = P5Theme.dpx(ctx, 18);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(P5Theme.INK_BLACK);
        box.setPadding(pad, pad, pad, pad);

        SectionHeaderView hdr = new SectionHeaderView(ctx, title, "");
        box.addView(hdr, new LinearLayout.LayoutParams(P5Theme.dpx(ctx, 380), P5Theme.dpx(ctx, 42)));

        ScrollView sc = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, P5Theme.dpx(ctx, 10), 0, P5Theme.dpx(ctx, 6));

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            SlashButton btn = new SlashButton(ctx, labels[i], i == current, 42);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, P5Theme.dpx(ctx, 42));
            lp.bottomMargin = P5Theme.dpx(ctx, 6);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    cb.onPick(idx);
                }
            });
            list.addView(btn, lp);
        }
        sc.addView(list);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                P5Theme.dpx(ctx, 380), Math.min(P5Theme.dpx(ctx, 250), labels.length * P5Theme.dpx(ctx, 48) + P5Theme.dpx(ctx, 18)));
        box.addView(sc, scLp);

        // White crisp border frame around dialog
        LinearLayout outer = new LinearLayout(ctx);
        outer.setBackgroundColor(P5Theme.PAPER_WHITE);
        int b = P5Theme.dpx(ctx, 2.5f);
        outer.setPadding(b, b, b, b);
        outer.addView(box);

        dlg.setContentView(outer);
        dlg.show();
    }

    // ---- 9. Custom P5 Styled Info / Confirm Dialog ----

    static void showMessageDialog(Context ctx, String title, String message,
                                  String posLabel, final Runnable onPos,
                                  String neutralLabel, final Runnable onNeutral,
                                  String negLabel) {
        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        int pad = P5Theme.dpx(ctx, 18);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(P5Theme.INK_BLACK);
        box.setPadding(pad, pad, pad, pad);

        SectionHeaderView hdr = new SectionHeaderView(ctx, title, "");
        box.addView(hdr, new LinearLayout.LayoutParams(P5Theme.dpx(ctx, 420), P5Theme.dpx(ctx, 42)));

        TextView msg = new TextView(ctx);
        msg.setText(message);
        msg.setTextColor(P5Theme.PAPER_WHITE);
        msg.setTypeface(P5Theme.bodyRegular());
        msg.setTextSize(14);
        msg.setLineSpacing(P5Theme.dp(ctx, 3), 1f);
        msg.setPadding(P5Theme.dpx(ctx, 4), P5Theme.dpx(ctx, 12), P5Theme.dpx(ctx, 4), P5Theme.dpx(ctx, 16));
        box.addView(msg, new LinearLayout.LayoutParams(P5Theme.dpx(ctx, 420), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        if (negLabel != null) {
            SlashButton neg = new SlashButton(ctx, negLabel, false, 40);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, P5Theme.dpx(ctx, 40));
            lp.rightMargin = P5Theme.dpx(ctx, 8);
            neg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { dlg.dismiss(); }
            });
            actions.addView(neg, lp);
        }
        if (neutralLabel != null) {
            SlashButton neu = new SlashButton(ctx, neutralLabel, false, 40);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, P5Theme.dpx(ctx, 40));
            lp.rightMargin = P5Theme.dpx(ctx, 8);
            neu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    if (onNeutral != null) onNeutral.run();
                }
            });
            actions.addView(neu, lp);
        }
        SlashButton pos = new SlashButton(ctx, posLabel != null ? posLabel : "OK", true, 40);
        pos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
                if (onPos != null) onPos.run();
            }
        });
        actions.addView(pos, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, P5Theme.dpx(ctx, 40)));
        box.addView(actions, new LinearLayout.LayoutParams(P5Theme.dpx(ctx, 420), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout outer = new LinearLayout(ctx);
        outer.setBackgroundColor(P5Theme.PAPER_WHITE);
        int b = P5Theme.dpx(ctx, 2.5f);
        outer.setPadding(b, b, b, b);
        outer.addView(box);

        dlg.setContentView(outer);
        dlg.show();
    }
}
