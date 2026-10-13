package com.example.spamton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Поле фазы атаки Спамтона, в логических координатах (без Android).
 *
 * Белые пули разных форм (звёзды, ромбы, капли, купоны) летят сверху и сбоку.
 * Жёлтые кромеры падают медленно: их нужно поймать сердцем — каждый наносит урон Спамтону.
 * Сердце ведётся джойстиком: setStick(dx, dy), |dx|,|dy| ≤ 1.
 */
public final class BulletField {

    public static final float WIDTH = 200f;
    public static final float HEIGHT = 130f;
    public static final float HEART = 8f;
    public static final float DURATION = 12f;
    /** Скорость сердца при полном отклонении джойстика, ед./с. */
    public static final float SPEED = 120f;
    /** Радиус кромера. */
    public static final float COIN_R = 5f;

    /** Виды белых пуль. */
    public enum Kind { STAR, DIAMOND, DROP, TICKET }

    public static final class Bullet {
        public final String label;   // подпись (для журнала и тестов)
        public final Kind kind;
        public float x, y, w, h;     // ограничивающий прямоугольник (он же хитбокс)
        public final float vx, vy;
        public float rot;            // текущий угол вращения, рад
        public final float spin;     // скорость вращения, рад/с
        /** Контур фигуры в локальных координатах: пары (x, y) от -0.5 до 0.5 относительно центра. */
        public final float[] outline;

        Bullet(String label, float x, float y, float w, float h, float vx, float vy) {
            this(label, Kind.DIAMOND, x, y, w, h, vx, vy, 0f, null);
        }

        Bullet(String label, Kind kind, float x, float y, float w, float h, float vx, float vy,
               float spin, float[] outline) {
            this.label = label;
            this.kind = kind;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.vx = vx; this.vy = vy;
            this.spin = spin;
            this.outline = outline != null ? outline : outlineFor(kind, w, h);
        }

        /** Центр фигуры. */
        public float cx() { return x + w / 2; }
        public float cy() { return y + h / 2; }
    }

    /** Жёлтый кромер: ловится сердцем. */
    public static final class Coin {
        public float x, y;
        public final float vx, vy;

        Coin(float x, float y, float vx, float vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }

    /** Итог одного шага симуляции. */
    public static final class Step {
        public final int hits;   // попаданий белых пуль
        public final int coins;  // поймано кромеров

        Step(int hits, int coins) {
            this.hits = hits;
            this.coins = coins;
        }
    }

    private static final String[] WORDS = {"$", "СДЕЛКА", "СКИДКА", "КРОМЕР", "ДАРОМ", "КУПИ"};

    private final Random rnd;
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Coin> coins = new ArrayList<>();
    private float heartX = WIDTH / 2, heartY = HEIGHT / 2;
    private float stickX, stickY;
    private float time;
    private float nextShot = 0.5f, nextSide = 1.2f, nextCoin = 1.0f;
    private int hits;
    private int caught;

    public BulletField(Random rnd) {
        this.rnd = rnd;
    }

    public List<Bullet> bullets() { return bullets; }
    public List<Coin> coins() { return coins; }
    public float heartX() { return heartX; }
    public float heartY() { return heartY; }
    public float time() { return time; }
    public int hits() { return hits; }
    public int caught() { return caught; }
    public boolean finished() { return time >= DURATION; }

    /** Сердце переходит в точку (с ограничением рамкой). */
    public void moveHeartTo(float x, float y) {
        heartX = clamp(x, HEART / 2, WIDTH - HEART / 2);
        heartY = clamp(y, HEART / 2, HEIGHT - HEART / 2);
    }

    /** Джойстик: направление и отклонение, |dx|, |dy| ≤ 1. Сердце двигается в update(). */
    public void setStick(float dx, float dy) {
        stickX = clamp(dx, -1f, 1f);
        stickY = clamp(dy, -1f, 1f);
    }

    /** Один шаг симуляции. */
    public Step update(float dt) {
        if (finished()) return new Step(0, 0);
        time += dt;

        // Сердце по джойстику.
        if (stickX != 0 || stickY != 0) {
            moveHeartTo(heartX + stickX * SPEED * dt, heartY + stickY * SPEED * dt);
        }

        // Плотность растёт со временем: интервалы сокращаются.
        float shotGap = Math.max(0.26f, 0.55f - time * 0.025f);
        if (time >= nextShot) {
            nextShot = time + shotGap * (0.7f + rnd.nextFloat() * 0.6f);
            spawnFalling();
        }
        if (time >= nextSide) {
            nextSide = time + 1.1f + rnd.nextFloat() * 0.9f;
            spawnTicket();
        }
        if (time >= nextCoin) {
            nextCoin = time + 0.65f + rnd.nextFloat() * 0.5f;
            spawnCoin();
        }

        int stepHits = 0;
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.rot += b.spin * dt;
            if (hitsHeart(b)) {
                stepHits++;
                bullets.remove(i);
            } else if (b.y > HEIGHT + 12 || b.x < -30 || b.x > WIDTH + 30) {
                bullets.remove(i);
            }
        }

        int stepCoins = 0;
        for (int i = coins.size() - 1; i >= 0; i--) {
            Coin c = coins.get(i);
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            if (coinTouchesHeart(c)) {
                stepCoins++;
                coins.remove(i);
            } else if (c.y > HEIGHT + 10 || c.x < -20 || c.x > WIDTH + 20) {
                coins.remove(i);
            }
        }

        hits += stepHits;
        caught += stepCoins;
        return new Step(stepHits, stepCoins);
    }

    private void spawnFalling() {
        Kind kind = Kind.values()[rnd.nextInt(3)];   // STAR, DIAMOND, DROP — падают сверху
        float size = 9f + rnd.nextFloat() * 7f;
        float x = rnd.nextFloat() * (WIDTH - size);
        float vy = 45f + rnd.nextFloat() * 30f + time * 1.5f;
        String word = WORDS[rnd.nextInt(WORDS.length)];
        bullets.add(new Bullet(word, kind, x, -size, size, size, 0f, vy,
                (rnd.nextFloat() * 2 - 1) * 3f, null));
    }

    private void spawnTicket() {
        boolean fromLeft = rnd.nextBoolean();
        float y = 10f + rnd.nextFloat() * (HEIGHT - 30f);
        float vx = (fromLeft ? 1 : -1) * (55f + rnd.nextFloat() * 25f);
        float x = fromLeft ? -18f : WIDTH + 2f;
        bullets.add(new Bullet("КУПОН", Kind.TICKET, x, y, 18f, 8f, vx, 0f, 0f, null));
    }

    private void spawnCoin() {
        float x = COIN_R + rnd.nextFloat() * (WIDTH - 2 * COIN_R);
        float vx = (rnd.nextFloat() * 2 - 1) * 12f;
        float vy = 28f + rnd.nextFloat() * 14f;
        coins.add(new Coin(x, -COIN_R, vx, vy));
    }

    /** Контур фигуры по виду (точки от -0.5 до 0.5). */
    static float[] outlineFor(Kind kind, float w, float h) {
        switch (kind) {
            case STAR: {
                // Восьмиконечная звезда: внешний и внутренний радиусы чередуются.
                float[] p = new float[16];
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8 - Math.PI / 2;
                    double r = (i % 2 == 0) ? 0.5 : 0.22;
                    p[2 * i] = (float) (Math.cos(a) * r);
                    p[2 * i + 1] = (float) (Math.sin(a) * r);
                }
                return p;
            }
            case DIAMOND:
                return new float[] {0f, -0.5f, 0.42f, 0f, 0f, 0.5f, -0.42f, 0f};
            case DROP: {
                // Капля: острая вершина сверху, округлое основание.
                float[] p = new float[14];
                p[0] = 0f; p[1] = -0.5f;
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * (0.25 + i * 0.25);
                    p[2 + 2 * i] = (float) (Math.cos(a) * 0.36);
                    p[3 + 2 * i] = (float) (Math.sin(a) * 0.36 + 0.14);
                }
                return p;
            }
            default:
                return new float[] {-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f};
        }
    }

    /** Пересечение прямоугольника пули с сердцем (AABB). */
    boolean hitsHeart(Bullet b) {
        float hl = heartX - HEART / 2, hr = heartX + HEART / 2;
        float ht = heartY - HEART / 2, hb = heartY + HEART / 2;
        return b.x < hr && b.x + b.w > hl && b.y < hb && b.y + b.h > ht;
    }

    /** Кромер касается сердца (круг против квадрата). */
    boolean coinTouchesHeart(Coin c) {
        float nx = clamp(c.x, heartX - HEART / 2, heartX + HEART / 2);
        float ny = clamp(c.y, heartY - HEART / 2, heartY + HEART / 2);
        float dx = c.x - nx, dy = c.y - ny;
        return dx * dx + dy * dy <= COIN_R * COIN_R;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
