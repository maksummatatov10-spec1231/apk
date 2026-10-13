package com.example.spamton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Поле пуль фазы атаки Спамтона, в логических координатах (без Android).
 * Падают рекламные слова и летят боковые «купоны». Сердце-душа в рамке.
 */
public final class BulletField {

    public static final float WIDTH = 200f;
    public static final float HEIGHT = 130f;
    public static final float HEART = 8f;
    public static final float DURATION = 6f;

    public static final class Bullet {
        public final String label;   // текст пули
        public float x, y, w, h;     // прямоугольник
        public final float vx, vy;

        Bullet(String label, float x, float y, float w, float h, float vx, float vy) {
            this.label = label;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.vx = vx; this.vy = vy;
        }
    }

    private static final String[] WORDS = {"$", "СДЕЛКА", "!!", "СКИДКА", "КРОМЕР", "ДАРОМ", "!!!", "КУПИ"};

    private final Random rnd;
    private final List<Bullet> bullets = new ArrayList<>();
    private float heartX = WIDTH / 2, heartY = HEIGHT / 2;
    private float time;
    private float nextWord = 0.4f, nextSide = 0.9f;
    private int hits;

    public BulletField(Random rnd) {
        this.rnd = rnd;
    }

    public List<Bullet> bullets() { return bullets; }
    public float heartX() { return heartX; }
    public float heartY() { return heartY; }
    public float time() { return time; }
    public int hits() { return hits; }
    public boolean finished() { return time >= DURATION; }

    /** Сердце двигается к точке (центр), с ограничением рамкой. */
    public void moveHeartTo(float x, float y) {
        heartX = clamp(x, HEART / 2, WIDTH - HEART / 2);
        heartY = clamp(y, HEART / 2, HEIGHT - HEART / 2);
    }

    /** Один шаг симуляции. Возвращает число попаданий на этом шаге. */
    public int update(float dt) {
        if (finished()) return 0;
        time += dt;

        if (time >= nextWord) {
            nextWord += 0.45f;
            String word = WORDS[rnd.nextInt(WORDS.length)];
            float w = 6f + word.length() * 4.5f;
            float x = rnd.nextFloat() * (WIDTH - w);
            bullets.add(new Bullet(word, x, -8f, w, 8f, 0f, 45f + rnd.nextFloat() * 25f));
        }
        if (time >= nextSide) {
            nextSide += 0.8f + rnd.nextFloat() * 0.4f;
            boolean fromLeft = rnd.nextBoolean();
            float y = 10f + rnd.nextFloat() * (HEIGHT - 30f);
            float vx = (fromLeft ? 1 : -1) * (55f + rnd.nextFloat() * 20f);
            float x = fromLeft ? -16f : WIDTH + 2f;
            bullets.add(new Bullet("КУПОН", x, y, 14f, 6f, vx, 0f));
        }

        int stepHits = 0;
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            if (hitsHeart(b)) {
                stepHits++;
                bullets.remove(i);
            } else if (b.y > HEIGHT + 10 || b.x < -30 || b.x > WIDTH + 30) {
                bullets.remove(i);
            }
        }
        hits += stepHits;
        return stepHits;
    }

    /** Пересечение прямоугольника пули с сердцем (AABB). */
    boolean hitsHeart(Bullet b) {
        float hl = heartX - HEART / 2, hr = heartX + HEART / 2;
        float ht = heartY - HEART / 2, hb = heartY + HEART / 2;
        return b.x < hr && b.x + b.w > hl && b.y < hb && b.y + b.h > ht;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
