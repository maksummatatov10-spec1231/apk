package android.view;

import android.content.Context;
import android.graphics.Canvas;

/** Заглушка View: размеры, касания и отложенные задачи (без реального окна). */
public class View {
    private int w, h;
    protected final Context context;

    public View(Context context) { this.context = context; }

    /** Как в Android: задаёт размеры вида. */
    public void layout(int l, int t, int r, int b) {
        w = r - l;
        h = b - t;
    }

    public final int getWidth() { return w; }
    public final int getHeight() { return h; }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) { }
    protected void onDraw(Canvas canvas) { }
    protected void onAttachedToWindow() { }
    protected void onDetachedFromWindow() { }
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public void invalidate() { }
    public boolean post(Runnable r) { return true; }
    public boolean postDelayed(Runnable r, long delay) { return true; }
    public boolean removeCallbacks(Runnable r) { return true; }
}
