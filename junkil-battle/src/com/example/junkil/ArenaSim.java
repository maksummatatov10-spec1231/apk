package com.example.junkil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Арена атаки Джанклима (без Android). Сердце двигается джойстиком, хлам летит сверху.
 *
 * Типы хлама: КРЫШКА (отскакивает от стен), БАТАРЕЙКА (падает прямо), ПЛАТА (медленно крутится).
 * Сигнал Морзе пролетает поверх арены: точка — короткий импульс, тире — длинный.
 * Тип атаки (BattleLogic.Pattern) меняет частоту и состав хлама.
 */
final class ArenaSim {

    static final float W = 300f;
    static final float H = 220f;
    static final float HEART = 12f;
    static final float DURATION = 10f;
    static final float HEART_SPEED = 120f;
    static final float DOT_UNIT = 0.12f;      // длительность «точки» в секундах сигнала

    enum Kind { CAP, BATTERY, BOARD, DOT, DASH }

    static final class Item {
        final Kind kind;
        float x, y, vx, vy, rot, spin, r;
        boolean hit;

        Item(Kind kind, float x, float y, float vx, float vy, float r) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.r = r;
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
    private float nextCap, nextBattery, nextBoard;
    private int signalIndex;

    ArenaSim(Random rng, BattleLogic.Pattern pattern, String morseWord) {
        this.rng = rng;
        this.pattern = pattern;
        this.morseWord = morseWord;
        nextCap = 0.6f;
        nextBattery = 0.9f;
        nextBoard = 1.6f;
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
                t += DOT_UNIT * 3f;  // пауза между буквами
            }
        }
    }

    private void spawn(Item it) {
        items.add(it);
        spawned++;
    }

    float intervalScale() {
        switch (pattern) {
            case REDUCED: return 1.8f;
            case BOOSTED: return 0.6f;
            default: return 1f;
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

        float s = intervalScale();
        if (time >= nextCap) {
            nextCap = time + 0.7f * s;
            float x = 20f + rng.nextFloat() * (W - 40f);
            spawn(new Item(Kind.CAP, x, -8f, (rng.nextBoolean() ? 1 : -1) * (40f + rng.nextFloat() * 40f), 60f, 6f));
        }
        if (time >= nextBattery) {
            nextBattery = time + 0.9f * s;
            float x = 15f + rng.nextFloat() * (W - 30f);
            spawn(new Item(Kind.BATTERY, x, -10f, 0f, 90f + rng.nextFloat() * 30f, 7f));
        }
        if (pattern != BattleLogic.Pattern.REDUCED && time >= nextBoard) {
            nextBoard = time + 1.6f * s;
            float x = 20f + rng.nextFloat() * (W - 40f);
            spawn(new Item(Kind.BOARD, x, -12f, 0f, 45f, 9f));
        }
        while (signalIndex < signal.size() && signal.get(signalIndex).at <= time) {
            Pulse p = signal.get(signalIndex++);
            float w = p.kind == Kind.DASH ? 12f : 4f;
            spawn(new Item(p.kind, W + w, 26f, -120f, 0f, w / 2f));
        }

        int newHits = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            it.x += it.vx * dt;
            it.y += it.vy * dt;
            if (it.kind == Kind.CAP) {
                if (it.x < 6f || it.x > W - 6f) it.vx = -it.vx;
            }
            if (it.kind == Kind.BOARD) {
                it.rot += 3f * dt;
            }
            if (!it.hit && dist(it.x, it.y, heartX, heartY) < it.r + HEART / 2f - 2f) {
                it.hit = true;
                hits++;
                newHits++;
            }
            boolean gone = it.y > H + 20f || it.x < -20f || it.x > W + 20f;
            if (gone) items.remove(i);
        }
        return newHits;
    }

    static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
