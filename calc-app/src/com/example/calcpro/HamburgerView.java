package com.example.calcpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Иконка «три полоски», плавно превращающаяся в крестик при открытии панели. */
final class HamburgerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress; // 0 — три полоски, 1 — крестик

    HamburgerView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.FILL);
    }

    void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float half = 9f * density;        // половина длины полоски
        float thick = 2.4f * density;     // толщина
        float gap = 6f * density;         // расстояние между полосками
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float p = progress;

        // верхняя: сдвигается к центру и поворачивается на +45°
        drawBar(canvas, cx, cy - gap * (1 - p), 45f * p, half, thick, 1f);
        // средняя: исчезает
        drawBar(canvas, cx, cy, 0f, half, thick, 1f - p);
        // нижняя: сдвигается к центру и поворачивается на −45°
        drawBar(canvas, cx, cy + gap * (1 - p), -45f * p, half, thick, 1f);
    }

    private void drawBar(Canvas canvas, float cx, float cy, float deg, float half, float thick, float scaleX) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(deg);
        canvas.scale(scaleX, 1f);
        rect.set(-half, -thick / 2f, half, thick / 2f);
        canvas.drawRoundRect(rect, thick / 2f, thick / 2f, paint);
        canvas.restore();
    }
}
