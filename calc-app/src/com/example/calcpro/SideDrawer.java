package com.example.calcpro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Левая выезжающая панель (navigation drawer) без AndroidX.
 * Открывается кнопкой, свайпом от левого края; закрывается кнопкой «назад»,
 * нажатием на затемнённую область или свайпом влево.
 */
final class SideDrawer extends FrameLayout {

    interface ProgressListener {
        /** 0 — закрыта, 1 — открыта. */
        void onProgress(float progress);
    }

    private final View content;
    private final View scrim;
    private final FrameLayout panel;
    private final int edgePx;
    private final int slopPx;

    private float progress;          // 0..1
    private int panelWidth;
    private ValueAnimator animator;
    private ProgressListener listener;

    private boolean dragging;
    private float downX, downY, startProgress;
    private VelocityTracker velocity;
    private float flingVx;

    SideDrawer(Context c, View content, View panelContent) {
        super(c);
        this.content = content;
        this.edgePx = Ui.dp(c, 24);
        this.slopPx = ViewConfiguration.get(c).getScaledTouchSlop();

        addView(content);

        scrim = new View(c);
        scrim.setBackgroundColor(Ui.color(c, R.color.scrim));
        scrim.setVisibility(GONE);
        scrim.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { close(); }
        });
        addView(scrim);

        panel = new FrameLayout(c);
        panel.setBackgroundColor(Ui.color(c, R.color.surface));
        panel.setElevation(Ui.dp(c, 12));
        panel.addView(panelContent);
        addView(panel);
    }

    void setProgressListener(ProgressListener l) {
        listener = l;
    }

    boolean isOpen() {
        return progress > 0.5f;
    }

    void open() {
        animateTo(1f);
    }

    void close() {
        animateTo(0f);
    }

    // ---------- измерение и раскладка ----------

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        panelWidth = Math.min(Ui.dp(getContext(), 300), (int) (w * 0.82f));
        content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        scrim.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        panel.measure(MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        content.layout(0, 0, w, h);
        scrim.layout(0, 0, w, h);
        panel.layout(0, 0, panelWidth, h);
        applyProgress();
    }

    private void applyProgress() {
        panel.setTranslationX((progress - 1f) * panelWidth);
        content.setTranslationX(progress * panelWidth * 0.12f);
        scrim.setAlpha(progress);
        scrim.setVisibility(progress > 0f ? VISIBLE : GONE);
        if (listener != null) listener.onProgress(progress);
    }

    private void setProgressValue(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        applyProgress();
    }

    private void animateTo(float target) {
        if (animator != null) animator.cancel();
        if (progress == target) {
            applyProgress();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration((long) (280 * Math.abs(target - progress)) + 60);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                setProgressValue((Float) a.getAnimatedValue());
            }
        });
        animator.start();
    }

    // ---------- жесты ----------

    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                startProgress = progress;
                dragging = false;
                if (velocity != null) velocity.recycle();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(ev);
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(ev);
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!dragging && Math.abs(dx) > slopPx && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    boolean opening = progress <= 0f && downX <= edgePx && dx > 0;
                    boolean closing = progress > 0f && dx < 0;
                    if (opening || closing) {
                        dragging = true;
                        if (animator != null) animator.cancel();
                        startProgress = progress;
                        downX = ev.getX();
                        return true;
                    }
                }
                return dragging;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging && velocity != null) {
                    velocity.recycle();
                    velocity = null;
                }
                return dragging;
            default:
                return dragging;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (!dragging) {
            // нажатие на затемнённую область закрывает панель; остальное — обычные клики
            return progress > 0f || super.onTouchEvent(ev);
        }
        if (velocity != null) velocity.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                setProgressValue(startProgress + (ev.getX() - downX) / panelWidth);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                    flingVx = velocity.getXVelocity();
                    velocity.recycle();
                    velocity = null;
                }
                boolean open;
                if (Math.abs(flingVx) > 900) open = flingVx > 0;
                else open = progress > 0.5f;
                animateTo(open ? 1f : 0f);
                return true;
            }
            default:
                return true;
        }
    }
}
