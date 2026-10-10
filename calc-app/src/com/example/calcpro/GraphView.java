package com.example.calcpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** График f(x) с осями, сеткой и отмеченными корнями. */
public final class GraphView extends View {
    private final Node fn;
    private final List<Double> marks;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double xMin = -10, xMax = 10, yMin = -10, yMax = 10;
    private final float pad;

    public GraphView(Context ctx, Node fn, List<Double> marks, int gridColor, int axisColor,
                     int curveColor, int markColor, int textColor) {
        super(ctx);
        this.fn = fn;
        this.marks = marks == null ? Collections.<Double>emptyList() : new ArrayList<>(marks);
        pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, ctx.getResources().getDisplayMetrics());

        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);
        axisPaint.setColor(axisColor);
        axisPaint.setStrokeWidth(2.5f);
        curvePaint.setColor(curveColor);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(4f);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        markPaint.setColor(markColor);
        markPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(textColor);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, ctx.getResources().getDisplayMetrics()));
        computeRanges();
    }

    private static double niceStep(double range) {
        double raw = range / 6.0;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        double s = n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10;
        return s * mag;
    }

    /** Диапазон по x — вокруг корней; по y — по перцентилям значений, чтобы асимптоты не ломали масштаб. */
    private void computeRanges() {
        double lo = -10, hi = 10;
        for (double m : marks) {
            if (Double.isNaN(m) || Double.isInfinite(m)) continue;
            lo = Math.min(lo, m - 2);
            hi = Math.max(hi, m + 2);
        }
        xMin = Math.max(lo, -1000);
        xMax = Math.min(hi, 1000);
        List<Double> ys = new ArrayList<>();
        int samples = 400;
        Map<String, Double> env = new HashMap<>();
        for (int i = 0; i <= samples; i++) {
            double x = xMin + (xMax - xMin) * i / samples;
            env.put("x", x);
            try {
                double y = fn.eval(env);
                if (!Double.isNaN(y) && !Double.isInfinite(y)) ys.add(y);
            } catch (MathError ignored) {
                // точка вне области определения — пропускаем
            }
        }
        if (ys.isEmpty()) {
            yMin = -1;
            yMax = 1;
            return;
        }
        Collections.sort(ys);
        double a = ys.get((int) (ys.size() * 0.03));
        double b = ys.get(Math.min(ys.size() - 1, (int) (ys.size() * 0.97)));
        a = Math.min(a, 0);
        b = Math.max(b, 0);
        if (b - a < 1e-9) {
            a -= 1;
            b += 1;
        }
        double margin = (b - a) * 0.1;
        yMin = a - margin;
        yMax = b + margin;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float left = pad, top = pad / 2, right = getWidth() - pad / 2, bottom = getHeight() - pad;
        if (right <= left || bottom <= top) return;

        double xs = niceStep(xMax - xMin);
        double ys = niceStep(yMax - yMin);
        for (double x = Math.ceil(xMin / xs) * xs; x <= xMax; x += xs) {
            float px = sx(x, left, right);
            c.drawLine(px, top, px, bottom, gridPaint);
            if (Math.abs(x) > 1e-9) c.drawText(fmt(x), px - 8, bottom + pad * 0.7f, textPaint);
        }
        for (double y = Math.ceil(yMin / ys) * ys; y <= yMax; y += ys) {
            float py = sy(y, top, bottom);
            c.drawLine(left, py, right, py, gridPaint);
            if (Math.abs(y) > 1e-9) c.drawText(fmt(y), 2, py - 4, textPaint);
        }
        // оси
        if (xMin <= 0 && xMax >= 0) c.drawLine(sx(0, left, right), top, sx(0, left, right), bottom, axisPaint);
        if (yMin <= 0 && yMax >= 0) c.drawLine(left, sy(0, top, bottom), right, sy(0, top, bottom), axisPaint);

        // кривая; разрывы (асимптоты, пропуски) рисуем отдельными отрезками
        Path path = new Path();
        boolean pen = false;
        double prevY = Double.NaN;
        Map<String, Double> env = new HashMap<>();
        int n = (int) Math.min(1200, Math.max(200, right - left));
        for (int i = 0; i <= n; i++) {
            double x = xMin + (xMax - xMin) * i / n;
            env.put("x", x);
            double y;
            try {
                y = fn.eval(env);
            } catch (MathError e) {
                pen = false;
                continue;
            }
            if (Double.isNaN(y) || Double.isInfinite(y)) {
                pen = false;
                continue;
            }
            float px = sx(x, left, right), py = sy(clampY(y), top, bottom);
            boolean jump = !Double.isNaN(prevY) && Math.abs(y - prevY) > (yMax - yMin) * 0.8;
            if (!pen || jump) {
                path.moveTo(px, py);
                pen = true;
            } else {
                path.lineTo(px, py);
            }
            prevY = y;
        }
        c.drawPath(path, curvePaint);

        for (double m : marks) {
            if (m < xMin || m > xMax) continue;
            float px = sx(m, left, right), py = sy(0, top, bottom);
            c.drawCircle(px, py, pad / 4, markPaint);
        }
    }

    private double clampY(double y) {
        return Math.max(yMin - (yMax - yMin), Math.min(yMax + (yMax - yMin), y));
    }

    private float sx(double x, float left, float right) {
        return (float) (left + (x - xMin) / (xMax - xMin) * (right - left));
    }

    private float sy(double y, float top, float bottom) {
        return (float) (top + (yMax - y) / (yMax - yMin) * (bottom - top));
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return Long.toString(Math.round(v));
        return String.format(java.util.Locale.US, "%.2f", v).replace('.', ',');
    }
}
