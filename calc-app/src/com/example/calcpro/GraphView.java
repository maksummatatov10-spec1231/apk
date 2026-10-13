package com.example.calcpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * График: функции y = f(x), неявные кривые F(x, y) = 0 (например, прямые системы), точки пересечения.
 * Перетаскивание одним пальцем двигает фигуру, щипок масштабирует, двойной тап возвращает вид.
 */
public final class GraphView extends View {
    private final List<Node> funcs;
    private final List<Node> implicits;
    private final List<double[]> points;
    private final List<Double> marks;
    private final int[] curveColors;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float pad;

    /** Текущий вид (координаты графика). */
    private double xMin = -10, xMax = 10, yMin = -10, yMax = 10;
    /** Вид по умолчанию: для двойного тапа. */
    private double homeXMin, homeXMax, homeYMin, homeYMax;
    /** Прямоугольник области построения в пикселях (заполняется в onDraw). */
    private float plotL, plotR, plotT, plotB;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private float lastX, lastY;
    private boolean dragging;

    /** Кэш кривых в координатах вида. На время жеста кэш сдвигается/масштабируется матрицей, без пересчёта. */
    private final List<Path> cachedPaths = new ArrayList<>();
    private final List<Integer> cachedColors = new ArrayList<>();
    private boolean cacheValid;
    private boolean interacting;
    private double cXMin, cXMax, cYMin, cYMax;
    private float cL, cT, cR, cB;

    public GraphView(Context ctx, List<Node> funcs, List<Node> implicits, List<double[]> points, List<Double> marks,
                     int gridColor, int axisColor, int[] curveColors, int markColor, int textColor) {
        super(ctx);
        this.funcs = funcs == null ? new ArrayList<Node>() : new ArrayList<>(funcs);
        this.implicits = implicits == null ? new ArrayList<Node>() : new ArrayList<>(implicits);
        this.points = points == null ? new ArrayList<double[]>() : new ArrayList<>(points);
        this.marks = marks == null ? Collections.<Double>emptyList() : new ArrayList<>(marks);
        this.curveColors = curveColors;
        pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, ctx.getResources().getDisplayMetrics());

        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f);
        axisPaint.setColor(axisColor);
        axisPaint.setStrokeWidth(2.5f);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(4f);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        markPaint.setColor(markColor);
        markPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(markColor);
        pointPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(textColor);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, ctx.getResources().getDisplayMetrics()));

        computeHome();
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoom(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) {
                resetView();
                return true;
            }
        });
    }

    private static double niceStep(double range) {
        double raw = range / 6.0;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double n = raw / mag;
        double s = n < 1.5 ? 1 : n < 3.5 ? 2 : n < 7.5 ? 5 : 10;
        return s * mag;
    }

    /** Начальный вид: по функциям — вокруг корней и по перцентилям; для систем — квадрат, включающий точки. */
    private void computeHome() {
        double lo = -10, hi = 10;
        for (double m : marks) {
            if (Double.isNaN(m) || Double.isInfinite(m)) continue;
            lo = Math.min(lo, m - 2);
            hi = Math.max(hi, m + 2);
        }
        for (double[] p : points) {
            lo = Math.min(lo, p[0] - 2);
            hi = Math.max(hi, p[0] + 2);
        }
        if (!funcs.isEmpty()) {
            xMin = Math.max(lo, -1000);
            xMax = Math.min(hi, 1000);
            computeFuncY();
        } else {
            double r = 10;
            for (double[] p : points) r = Math.max(r, Math.max(Math.abs(p[0]), Math.abs(p[1])) + 2);
            xMin = -r;
            xMax = r;
            yMin = -r;
            yMax = r;
        }
        homeXMin = xMin;
        homeXMax = xMax;
        homeYMin = yMin;
        homeYMax = yMax;
    }

    /** Диапазон по y — по перцентилям значений, чтобы асимптоты не ломали масштаб. */
    private void computeFuncY() {
        if (funcs.isEmpty()) return;
        List<Double> ys = new ArrayList<>();
        int samples = 400;
        Map<String, Double> env = new HashMap<>();
        for (int i = 0; i <= samples; i++) {
            double x = xMin + (xMax - xMin) * i / samples;
            env.put("x", x);
            for (Node f : funcs) {
                try {
                    double y = f.eval(env);
                    if (!Double.isNaN(y) && !Double.isInfinite(y)) ys.add(y);
                } catch (MathError ignored) {
                    // точка вне области определения — пропускаем
                }
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

    private void resetView() {
        xMin = homeXMin;
        xMax = homeXMax;
        yMin = homeYMin;
        yMax = homeYMax;
        invalidate();
    }

    // ---------- жесты ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                dragging = true;
                interacting = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                dragging = false;
                interacting = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && e.getPointerCount() == 1) {
                    pan(e.getX() - lastX, e.getY() - lastY);
                    lastX = e.getX();
                    lastY = e.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                interacting = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();   // после жеста пересчитываем кривые в точном виде
                break;
            default:
                break;
        }
        return true;
    }

    private void pan(float dx, float dy) {
        float w = plotR - plotL, h = plotB - plotT;
        if (w <= 0 || h <= 0) return;
        double sx = (xMax - xMin) / w, sy = (yMax - yMin) / h;
        xMin -= dx * sx;
        xMax -= dx * sx;
        yMin += dy * sy;
        yMax += dy * sy;
        invalidate();
    }

    private void zoom(double factor, float fx, float fy) {
        if (plotR <= plotL || plotB <= plotT || factor <= 0) return;
        double dataX = xMin + (fx - plotL) / (plotR - plotL) * (xMax - xMin);
        double dataY = yMax - (fy - plotT) / (plotB - plotT) * (yMax - yMin);
        double wx = (xMax - xMin) / factor, wy = (yMax - yMin) / factor;
        if (wx < 1e-4 || wx > 1e6 || wy < 1e-4 || wy > 1e6) return;
        xMin = dataX - (dataX - xMin) / factor;
        xMax = xMin + wx;
        yMax = dataY + (yMax - dataY) / factor;
        yMin = yMax - wy;
        invalidate();
    }

    // ---------- рисование ----------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        plotL = pad;
        plotT = pad / 2;
        plotR = getWidth() - pad / 2;
        plotB = getHeight() - pad;
        if (plotR <= plotL || plotB <= plotT) return;
        float left = plotL, top = plotT, right = plotR, bottom = plotB;

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
        if (xMin <= 0 && xMax >= 0) c.drawLine(sx(0, left, right), top, sx(0, left, right), bottom, axisPaint);
        if (yMin <= 0 && yMax >= 0) c.drawLine(left, sy(0, top, bottom), right, sy(0, top, bottom), axisPaint);

        boolean same = cacheValid && cXMin == xMin && cXMax == xMax && cYMin == yMin && cYMax == yMax
                && cL == left && cT == top && cR == right && cB == bottom;
        if (!cacheValid || (!same && !interacting)) {
            rebuildCache(left, top, right, bottom);
            same = true;
        }
        c.save();
        c.clipRect(left, top, right, bottom);
        if (same) {
            drawCache(c);
        } else {
            // жест: кэш переводится в текущий вид линейным преобразованием, без пересчёта кривых
            double dx = xMax - xMin, dy = yMax - yMin;
            float W = right - left, H = bottom - top;
            float cW = cR - cL, cH = cB - cT;
            float ax = (float) ((cXMax - cXMin) * W / (cW * dx));
            float ay = (float) ((cYMax - cYMin) * H / (cH * dy));
            float bx = (float) (left + (cXMin - xMin) * W / dx - cL * ax);
            float by = (float) (top + (yMax - cYMax) * H / dy - cT * ay);
            c.translate(bx, by);
            c.scale(ax, ay);
            drawCache(c);
        }
        c.restore();

        for (double m : marks) {
            if (m < xMin || m > xMax) continue;
            c.drawCircle(sx(m, left, right), sy(0, top, bottom), pad / 4, markPaint);
        }
        for (double[] p : points) {
            if (p[0] < xMin || p[0] > xMax || p[1] < yMin || p[1] > yMax) continue;
            float px = sx(p[0], left, right), py = sy(p[1], top, bottom);
            c.drawCircle(px, py, pad / 3, pointPaint);
            c.drawText("(" + fmt(p[0]) + "; " + fmt(p[1]) + ")", px + pad / 3, py - pad / 3, textPaint);
        }
    }

    private void rebuildCache(float left, float top, float right, float bottom) {
        cachedPaths.clear();
        cachedColors.clear();
        int k = 0;
        for (Node f : funcs) {
            cachedPaths.add(functionPath(f, left, right, top, bottom));
            cachedColors.add(color(k++));
        }
        for (Node f : implicits) {
            cachedPaths.add(implicitPath(f, left, right, top, bottom));
            cachedColors.add(color(k++));
        }
        cXMin = xMin;
        cXMax = xMax;
        cYMin = yMin;
        cYMax = yMax;
        cL = left;
        cT = top;
        cR = right;
        cB = bottom;
        cacheValid = true;
    }

    private void drawCache(Canvas c) {
        for (int i = 0; i < cachedPaths.size(); i++) {
            curvePaint.setColor(cachedColors.get(i));
            c.drawPath(cachedPaths.get(i), curvePaint);
        }
    }

    private int color(int i) {
        if (curveColors == null || curveColors.length == 0) return 0xFF4FC3F7;
        return curveColors[i % curveColors.length];
    }

    /** Кривая y = f(x); разрывы (асимптоты, пропуски) рисуем отдельными отрезками. */
    private Path functionPath(Node fn, float left, float right, float top, float bottom) {
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
        return path;
    }

    /**
     * Неявная кривая F(x, y) = 0: сетка значений и «марширующие квадраты» —
     * в каждой клетке, где знак меняется, проводим отрезок через точки пересечения ребер.
     */
    private Path implicitPath(Node f, float left, float right, float top, float bottom) {
        Path path = new Path();
        float[] box = {left, top, right, bottom};
        int cols = Math.max(60, (int) ((right - left) / 4));
        int rows = Math.max(60, (int) ((bottom - top) / 4));
        double[][] v = new double[rows + 1][cols + 1];
        Map<String, Double> env = new HashMap<>();
        for (int i = 0; i <= rows; i++) {
            double y = yMax - (yMax - yMin) * i / rows;
            for (int j = 0; j <= cols; j++) {
                double x = xMin + (xMax - xMin) * j / cols;
                env.put("x", x);
                env.put("y", y);
                double val;
                try {
                    val = f.eval(env);
                } catch (MathError e) {
                    val = Double.NaN;
                }
                v[i][j] = val;
            }
        }
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double a = v[i][j], b = v[i][j + 1], cc = v[i + 1][j + 1], d = v[i + 1][j];
                if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(cc) || Double.isNaN(d)) continue;
                List<float[]> hits = new ArrayList<>(4);
                // ребра: верх, правое, низ, левое (по часовой)
                edgeHit(hits, a, b, box, cols, rows, j, i, j + 1, i);
                edgeHit(hits, b, cc, box, cols, rows, j + 1, i, j + 1, i + 1);
                edgeHit(hits, d, cc, box, cols, rows, j, i + 1, j + 1, i + 1);
                edgeHit(hits, a, d, box, cols, rows, j, i, j, i + 1);
                if (hits.size() >= 2) {
                    path.moveTo(hits.get(0)[0], hits.get(0)[1]);
                    path.lineTo(hits.get(1)[0], hits.get(1)[1]);
                }
                if (hits.size() == 4) {
                    path.moveTo(hits.get(2)[0], hits.get(2)[1]);
                    path.lineTo(hits.get(3)[0], hits.get(3)[1]);
                }
            }
        }
        return path;
    }

    /** Если на ребре (p0→p1) знак меняется, добавляем точку пересечения в экранных координатах. */
    private void edgeHit(List<float[]> hits, double v0, double v1, float[] box,
                         int cols, int rows, int c0, int r0, int c1, int r1) {
        if ((v0 > 0) == (v1 > 0)) return;
        double t = v0 / (v0 - v1);
        float left = box[0], top = box[1], right = box[2], bottom = box[3];
        float x0 = left + (right - left) * c0 / cols, y0 = top + (bottom - top) * r0 / rows;
        float x1 = left + (right - left) * c1 / cols, y1 = top + (bottom - top) * r1 / rows;
        hits.add(new float[]{(float) (x0 + t * (x1 - x0)), (float) (y0 + t * (y1 - y0))});
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
        return String.format(Locale.US, "%.2f", v).replace('.', ',');
    }
}
