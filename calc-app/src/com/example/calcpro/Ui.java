package com.example.calcpro;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** Общие помощники оформления: цвета, скругления, волна нажатия, анимации. Только API 21. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static int color(Context c, int res) {
        return c.getResources().getColor(res);
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** Скругление только верхних углов (нижняя панель клавиатуры). */
    static GradientDrawable roundedTop(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        return g;
    }

    /** Кнопка с волной нажатия и скруглённой маской. */
    static RippleDrawable ripple(int color, float radius, int rippleColor) {
        GradientDrawable mask = rounded(0xFFFFFFFF, radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), rounded(color, radius), mask);
    }

    static TextView text(Context c, String s, float sp, int colorRes, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorRes));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Лёгкое «пружинящее» уменьшение при нажатии; клики при этом работают как обычно. */
    static void press(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                                .setInterpolator(new OvershootInterpolator(2.2f)).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /**
     * Удержание кнопки: сразу один вызов action, затем повтор. Пауза перед повтором — 400 мс,
     * далее интервал сокращается от 120 мс до 30 мс, как у Backspace на ПК.
     */
    static void hold(final View v, final Runnable action) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final long[] interval = {0};
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override public void run() {
                action.run();
                interval[0] = interval[0] == 400 ? 120 : Math.max(30, (long) (interval[0] * 0.9));
                handler.postDelayed(this, interval[0]);
            }
        };
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).start();
                        action.run();
                        interval[0] = 400;
                        handler.removeCallbacks(tick[0]);
                        handler.postDelayed(tick[0], 400);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(tick[0]);
                        view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                                .setInterpolator(new OvershootInterpolator(2.2f)).start();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    /** Появление карточки: плавно вверх и проявление. */
    static void appear(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 18));
        v.animate().alpha(1f).translationY(0f).setDuration(340).setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
    }

    /** Короткая тряска (например, при пустом вводе). */
    static void shake(View v) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, -14f, 14f, -9f, 9f, -4f, 0f).setDuration(420).start();
    }
}
