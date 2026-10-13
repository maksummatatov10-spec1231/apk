package com.example.spamton;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Общая обвязка харнесса: вид 720×1440 (масштаб 2×), запись звуков, память в RAM. */
final class Env {

    static final int W = 720, H = 1440;

    static final class Rec implements Sounds {
        final List<String> played = new ArrayList<>();
        @Override public void play(String name) { played.add(name); }
        int count(String name) {
            int n = 0;
            for (String s : played) if (s.equals(name)) n++;
            return n;
        }
    }

    static final class MemStore implements Store {
        final Map<String, Boolean> m = new HashMap<>();
        @Override public boolean getBool(String key, boolean def) { return m.containsKey(key) ? m.get(key) : def; }
        @Override public void putBool(String key, boolean value) { m.put(key, value); }
    }

    final SpamtonView view;
    final Rec sounds = new Rec();
    final MemStore store;

    Env(long seed, boolean ally) {
        store = new MemStore();
        if (ally) store.putBool("defeated", true);
        view = new SpamtonView(new Context(), sounds, store, new Random(seed));
        view.layout(0, 0, W, H);
        view.onSizeChanged(W, H, 0, 0);
        // Те же портреты, что грузит MainActivity: по эмоции, если файл есть в res/drawable-nodpi.
        java.util.Map<Mood, android.graphics.Bitmap> portraits = new java.util.EnumMap<>(Mood.class);
        for (Mood m : Mood.values()) {
            java.io.File f = new java.io.File("res/drawable-nodpi/" + m.resName() + ".png");
            if (f.exists()) portraits.put(m, new android.graphics.Bitmap(f.getPath(), 126, 126));
        }
        view.setPortraits(portraits);
        view.setBulletTexture(new android.graphics.Bitmap("res/drawable-nodpi/bullet_tex.png", 256, 256));
    }

    /** Журнал команд рисования текущего кадра (см. scripts/render_ops.py). */
    String shot() {
        Canvas canvas = new Canvas();
        view.onDraw(canvas);
        return canvas.ops();
    }

    void tap(float x, float y) {
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, x, y));
        view.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, x, y));
    }

    /** Нажатие клавиши калькулятора по надписи (реальные координаты раскладки). */
    void key(String label) {
        float[] c = view.keyCenterForTest(label);
        tap(c[0], c[1]);
    }

    void type(String keys) {
        for (int i = 0; i < keys.length(); i++) key(String.valueOf(keys.charAt(i)));
    }

    void advance(float seconds) { view.advanceForTest(seconds); }

    /** Эмоция Спамтона сейчас. */
    Mood mood() { return view.mood(); }

    /** Закрывает рекламу: она перекрывает ввод, пока её не закроют. */
    void dismissAds() {
        for (int i = 0; i < 8 && view.debugState().contains("popup=true"); i++) {
            float[] c = view.closeCenterForTest();
            tap(c[0], c[1]);
        }
    }

    /** Дожидается конца печати реплики и её выдержки. */
    void settle() {
        for (int i = 0; i < 600; i++) {
            advance(1f / 30f);
        }
    }
}
