package com.example.junkil;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;

/** Обвязка харнесса: вид 720×1440 (масштаб 2×, сверху-снизу поля 60 px). */
final class Env {

    static final int W = 720, H = 1440;
    static final float SCALE = 2f, TOP = 60f;

    final BattleView view;

    Env(long seed) {
        Bitmap[] sprites = {
            new Bitmap("res/drawable-nodpi/junkil.png", 290, 440),
            new Bitmap("res/drawable-nodpi/junkil_angry.png", 290, 440),
            new Bitmap("res/drawable-nodpi/junkil_sad.png", 290, 440),
        };
        view = new BattleView(new Context(), sprites, seed);
        view.layout(0, 0, W, H);
    }

    String shot() {
        Canvas canvas = new Canvas();
        view.onDraw(canvas);
        return canvas.ops();
    }

    /** Касание в логических координатах (как пользователь на экране). */
    void tapLogical(float lx, float ly) {
        float px = lx * SCALE, py = TOP + ly * SCALE;
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, px, py));
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, px, py));
    }

    void tapButton(String id) {
        float[] c = view.buttonCenterForTest(id);
        tapLogical(c[0], c[1]);
    }

    void advance(float seconds) {
        float step = 1f / 60f;
        for (float t = 0; t < seconds; t += step) view.tick(step);
    }

    BattleLogic logic() { return view.logic; }
}
