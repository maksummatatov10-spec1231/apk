package com.example.airhotkey;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/** Холст игры: игровой цикл на частоте кадров экрана, касания и клавиатура. */
final class GameView extends View implements Runnable {

    private final World world;
    private boolean running;
    private long lastNanos = -1;

    GameView(Context ctx) {
        super(ctx);
        world = new World(ctx);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
    }

    void onHostResume() {
        if (running) return;
        running = true;
        lastNanos = -1;
        postOnAnimation(this);
    }

    void onHostPause() {
        running = false;
        world.pauseIfPlaying();
    }

    boolean onBack() {
        return world.back();
    }

    boolean onKey(int keyCode) {
        return world.key(keyCode);
    }

    @Override
    public void run() {
        if (!running) return;
        long now = System.nanoTime();
        float dt = lastNanos < 0 ? 0f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        world.layout(getWidth(), getHeight());
        world.update(Math.min(dt, 0.05f));
        invalidate();
        postOnAnimation(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        world.layout(getWidth(), getHeight());
        world.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            world.tap(e.getX(), e.getY());
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
