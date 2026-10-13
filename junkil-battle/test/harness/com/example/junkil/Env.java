package com.example.junkil;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;

/** Обвязка харнесса: ландшафт 1280×720; логические координаты 854×480 масштабируются как в BattleView. */
final class Env {

    static final int W = 1280, H = 720;
    static final String[] TEX = {
        "static", "circuit", "dollar", "chrome", "bubbles", "vinyl", "rust", "cassette", "stripes",
    };

    final BattleView view;
    final RecSounds sounds = new RecSounds();

    Env(long seed) {
        Bitmap[] sprites = {new Bitmap("res/drawable-nodpi/junkil.png", 290, 440), null, null};
        Bitmap[] textures = new Bitmap[TEX.length];
        for (int i = 0; i < TEX.length; i++) {
            textures[i] = new Bitmap("res/drawable-nodpi/atk_" + TEX[i] + ".png", 512, 512);
        }
        view = new BattleView(new Context(), sprites, textures, seed);
        view.layout(0, 0, W, H);
        view.setSounds(sounds);
    }

    private float scale() { return Math.min(W / BattleView.LW, H / BattleView.LH); }
    float pxX(float lx) { return (W - BattleView.LW * scale()) / 2f + lx * scale(); }
    float pxY(float ly) { return (H - BattleView.LH * scale()) / 2f + ly * scale(); }

    String shot() {
        Canvas canvas = new Canvas();
        view.onDraw(canvas);
        return canvas.ops();
    }

    /** Касание в логических координатах (как пользователь на экране). */
    void tapLogical(float lx, float ly) {
        float px = pxX(lx), py = pxY(ly);
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, px, py));
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, px, py));
    }

    void tapButton(String id) {
        float[] c = view.buttonCenterForTest(id);
        tapLogical(c[0], c[1]);
    }

    /** Тянем джойстик от точки from к точке to (логические координаты), затем отпускаем. */
    void joyDrag(float fx, float fy, float tx, float ty) {
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, pxX(fx), pxY(fy)));
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_MOVE, pxX(tx), pxY(ty)));
        advance(0.1f);
    }

    void joyRelease() {
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 0, 0));
        advance(0.05f);
    }

    void tapZ() { tapLogical(BattleView.Z_X, BattleView.Z_Y); }

    void advance(float seconds) {
        float step = 1f / 60f;
        for (float t = 0; t < seconds; t += step) view.tick(step);
    }

    BattleLogic logic() { return view.logic; }
}
