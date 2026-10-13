package com.example.junkil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Арена атаки Джанклима (без Android). Ландшафт: поле 464×168 внутри белой рамки.
 *
 * Атаки — сложные фигуры (звезда, шестерёнка, полумесяц, монета «$», молния, кассета),
 * каждая заливается своей бесшовной текстурой (TEX_COUNT штук). Сердце — тоже фигура.
 * Сигнал Морзе летит поверх: точки и тире.
 */
final class ArenaSim {

    static final float W = 464f, H = 168f;
    static final float HEART = 12f;
    static final float DURATION = 10f;
    static final float HEART_SPEED = 140f;
    static final float DOT_UNIT = 0.12f;
    /** Бесшовных текстур атак сейчас. Десятая (блёстки) ещё не сгенерирована. */
    static final int TEX_COUNT = 9;

    enum Kind { STAR, GEAR, CRESCENT, COIN, BOLT, CASSETTE, DOT, DASH }

    static final class Item {
        final Kind kind;
        final int tex;
        float x, y, vx, vy, rot, spin, r;
        boolean hit;

        Item(Kind kind, int tex, float x, float y, float vx, float vy, float r) {
            this.kind = kind;
            this.tex = tex;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.r = r;
            this.spin = 0f;
        }
    }

    private static final class Pulse {
        final float at;
        final Kind kind;

        Pulse(float at, Kind kind) {
            this.at = at;
            this.kind = kind;
        }
    }

    private static final float[][] POLY = new float[Kind.values().length][];

    /** Многоугольник фигуры в локальных координатах (радиус ≈ 1). Для DOT/DASH не используется. */
    static float[] polygon(Kind k) {
        int i = k.ordinal();
        if (POLY[i] == null) POLY[i] = build(k);
        return POLY[i];
    }

    private static float[] build(Kind k) {
        List<float[]> p = new ArrayList<>();
        switch (k) {
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    double rad = i % 2 == 0 ? 1.0 : 0.42;
                    p.add(new float[] {(float) (Math.cos(a) * rad), (float) (Math.sin(a) * rad)});
                }
                break;
            case GEAR:
                for (int i = 0; i < 24; i++) {
                    double a = i * 2 * Math.PI / 24;
                    double rad = (i % 3 == 0) ? 1.0 : 0.78;   // зубцы
                    p.add(new float[] {(float) (Math.cos(a) * rad), (float) (Math.sin(a) * rad)});
                }
                break;
            case CRESCENT:
                for (int i = 0; i <= 12; i++) {                // внешняя дуга: от 0.7 до 2π-0.7
                    double a = 0.7 + i * (2 * Math.PI - 1.4) / 12;
                    p.add(new float[] {(float) Math.cos(a), (float) Math.sin(a)});
                }
                for (int i = 0; i <= 10; i++) {                // внутренняя дуга (сдвинутый круг), обратно
                    double b = (2 * Math.PI - 1.08) - i * (2 * Math.PI - 2.16) / 10;
                    p.add(new float[] {(float) (0.35 + 0.85 * Math.cos(b)), (float) (0.85 * Math.sin(b))});
                }
                break;
            case COIN:
                for (int i = 0; i < 20; i++) {
                    double a = i * 2 * Math.PI / 20;
                    p.add(new float[] {(float) Math.cos(a), (float) Math.sin(a)});
                }
                break;
            case BOLT:
                float[][] bolt = {{0.15f, -1f}, {-0.65f, 0.1f}, {-0.05f, 0.1f}, {-0.25f, 1f},
                        {0.65f, -0.2f}, {0.05f, -0.2f}};
                for (float[] v : bolt) p.add(v);
                break;
            case CASSETTE:
                float[][] cas = {{-1f, -0.7f}, {0.7f, -0.7f}, {1f, -0.4f}, {1f, 0.7f}, {-1f, 0.7f}};
                for (float[] v : cas) p.add(v);
                break;
            default:
                break;
        }
        float[] out = new float[p.size() * 2];
        for (int i = 0; i < p.size(); i++) {
            out[2 * i] = p.get(i)[0];
            out[2 * i + 1] = p.get(i)[1];
        }
        return out;
    }

    final Random rng;
    final BattleLogic.Pattern pattern;
    final String morseWord;
    final List<Item> items = new ArrayList<>();
    private final List<Pulse> signal = new ArrayList<>();
    float heartX = W / 2f, heartY = H / 2f;
    float stickX, stickY;
    float time;
    int hits;
    int spawned;
    private float nextAttack;
    private int signalIndex;

    ArenaSim(Random rng, BattleLogic.Pattern pattern, String morseWord) {
        this.rng = rng;
        this.pattern = pattern;
        this.morseWord = morseWord;
        nextAttack = 0.6f;
        if (pattern != BattleLogic.Pattern.REDUCED) buildSignal(1.2f);
    }

    /** Сигнал: буквы слова в морзе, точки и тире по времени. */
    private void buildSignal(float start) {
        String code = MorseCode.encode(morseWord);
        float t = start;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '.') {
                signal.add(new Pulse(t, Kind.DOT));
                t += DOT_UNIT * 2f;
            } else if (c == '-') {
                signal.add(new Pulse(t, Kind.DASH));
                t += DOT_UNIT * 4f;
            } else {
                t += DOT_UNIT * 3f;
            }
        }
    }

    float intervalScale() {
        switch (pattern) {
            case REDUCED: return 1.8f;
            case BOOSTED: return 0.6f;
            default: return 1f;
        }
    }

    private Kind pickKind() {
        Kind[] pool;
        switch (pattern) {
            case REDUCED: pool = new Kind[] {Kind.COIN, Kind.STAR}; break;
            case BOOSTED: pool = new Kind[] {Kind.STAR, Kind.COIN, Kind.GEAR, Kind.BOLT, Kind.CRESCENT, Kind.CASSETTE}; break;
            default: pool = new Kind[] {Kind.STAR, Kind.COIN, Kind.GEAR}; break;
        }
        return pool[rng.nextInt(pool.length)];
    }

    private static float radiusOf(Kind k) {
        switch (k) {
            case GEAR: case CRESCENT: case CASSETTE: return 14f;
            case DOT: return 3f;
            case DASH: return 4f;
            default: return 13f;
        }
    }

    void setStick(float dx, float dy) {
        stickX = dx;
        stickY = dy;
    }

    void moveHeartTo(float x, float y) {
        heartX = clamp(x, HEART / 2f, W - HEART / 2f);
        heartY = clamp(y, HEART / 2f, H - HEART / 2f);
    }

    boolean finished() {
        return time >= DURATION;
    }

    /** Шаг симуляции. Возвращает число новых попаданий. */
    int update(float dt) {
        if (finished()) return 0;
        time += dt;
        float len = (float) Math.sqrt(stickX * stickX + stickY * stickY);
        if (len > 1f) {
            stickX /= len;
            stickY /= len;
        }
        moveHeartTo(heartX + stickX * HEART_SPEED * dt, heartY + stickY * HEART_SPEED * dt);

        if (time >= nextAttack) {
            nextAttack = time + 0.65f * intervalScale();
            Kind k = pickKind();
            float x = 24f + rng.nextFloat() * (W - 48f);
            float vx = rng.nextBoolean() ? (rng.nextFloat() * 50f - 25f) : 0f;
            float vy = 50f + rng.nextFloat() * 35f;
            Item it = new Item(k, rng.nextInt(TEX_COUNT), x, -12f, vx, vy, radiusOf(k));
            it.spin = (rng.nextFloat() * 2f - 1f) * 2.2f;
            spawn(it);
        }
        while (signalIndex < signal.size() && signal.get(signalIndex).at <= time) {
            Pulse p = signal.get(signalIndex++);
            float w = p.kind == Kind.DASH ? 12f : 4f;
            spawn(new Item(p.kind, 0, W + w, 22f, -120f, 0f, radiusOf(p.kind)));
        }

        int newHits = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            it.x += it.vx * dt;
            it.y += it.vy * dt;
            it.rot += it.spin * dt;
            if (!it.hit && dist(it.x, it.y, heartX, heartY) < it.r * 0.8f + HEART / 2f - 1f) {
                it.hit = true;
                hits++;
                newHits++;
            }
            boolean gone = it.y > H + 20f || it.x < -20f || it.x > W + 20f;
            if (gone) items.remove(i);
        }
        return newHits;
    }

    private void spawn(Item it) {
        items.add(it);
        spawned++;
    }

    static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
