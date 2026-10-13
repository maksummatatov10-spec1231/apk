package com.example.junkil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Арена атаки Джанклима (без Android). Поле — квадрат W×H внутри белой рамки.
 *
 * Особенность Джанклима — атаки строятся из его сигнала Морзе:
 *  - ЧАСТОТА (всегда первая): каждая точка слова — колючка, летящая в сердце;
 *    каждое тире — стена с дырой на линии сердца. По сигналу можно предсказать атаку.
 *  - Затем две случайные атаки: ПРИЦЕЛ (залп в сердце), СТЕНА (дыра на линии сердца),
 *    КАРУСЕЛЬ (кольцо со щелью), ОТСКОК (монеты отскакивают от стен).
 *
 * Все атаки прицеливаются в позицию сердца в момент запуска, поэтому нужно двигаться.
 * Фигуры: звезда, шестерёнка, полумесяц, монета, молния, кассета, заливка бесшовной текстурой.
 */
final class ArenaSim {

    /** Квадратное поле (в логических единицах рамки). */
    static final float W = 184f, H = 184f;
    static final float HEART = 12f;
    static final float DURATION = 14f;
    static final float HEART_SPEED = 120f;
    /** Бесшовных текстур атак сейчас. Десятая (блёстки) ещё не сгенерирована. */
    static final int TEX_COUNT = 9;

    enum Kind { STAR, GEAR, CRESCENT, COIN, BOLT, CASSETTE, DOT, DASH }

    enum Attack { MORSE, AIM, WALL, RING, BOUNCE }

    static final class Item {
        final Kind kind;
        final int tex;
        float x, y, vx, vy, rot, spin, r;
        float life = -1f;           // < 0 — живёт, пока не уйдёт за поле
        boolean bounce;
        boolean hit;

        Item(Kind kind, int tex, float x, float y, float vx, float vy, float r) {
            this.kind = kind;
            this.tex = tex;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.r = r;
        }
    }

    /** Событие расписания: в момент at выполнить шаг атаки. */
    private static final class Event {
        final float at;
        final Attack attack;
        final boolean dash;   // МОРЗЕ: тире (стена) или точка (колючка)
        final boolean start;  // начало атаки: подпись и звук

        Event(float at, Attack attack, boolean dash, boolean start) {
            this.at = at;
            this.attack = attack;
            this.dash = dash;
            this.start = start;
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
                    double rad = (i % 3 == 0) ? 1.0 : 0.78;
                    p.add(new float[] {(float) (Math.cos(a) * rad), (float) (Math.sin(a) * rad)});
                }
                break;
            case CRESCENT:
                for (int i = 0; i <= 12; i++) {
                    double a = 0.7 + i * (2 * Math.PI - 1.4) / 12;
                    p.add(new float[] {(float) Math.cos(a), (float) Math.sin(a)});
                }
                for (int i = 0; i <= 10; i++) {
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
    /** Атаки по порядку (для проверок). */
    final List<Attack> attackLog = new ArrayList<>();
    float heartX = W / 2f, heartY = H / 2f;
    float stickX, stickY;
    float time;
    int hits;
    int spawned;
    /** Сколько атак уже началось (BattleView проигрывает звук и подпись на каждую). */
    int attackStarts;
    private Attack current;
    private final List<Event> schedule = new ArrayList<>();
    private int next;

    ArenaSim(Random rng, BattleLogic.Pattern pattern, String morseWord) {
        this.rng = rng;
        this.pattern = pattern;
        this.morseWord = morseWord;
        buildSchedule();
    }

    /** Расписание хода: МОРЗЕ первой, затем две случайные атаки. */
    private void buildSchedule() {
        List<Attack> order = new ArrayList<>();
        order.add(Attack.MORSE);
        List<Attack> rest = new ArrayList<>(List.of(Attack.AIM, Attack.WALL, Attack.RING, Attack.BOUNCE));
        Collections.shuffle(rest, rng);
        order.add(rest.get(0));
        order.add(rest.get(1));

        float start = 0.8f;
        for (Attack a : order) {
            if (start > DURATION - 2.0f) break;
            start = scheduleAttack(a, start) + 0.5f;
        }
        java.util.Collections.sort(schedule, new java.util.Comparator<Event>() {
            @Override
            public int compare(Event x, Event y) {
                return Float.compare(x.at, y.at);
            }
        });
    }

    /** Ставит события атаки и возвращает время её окончания. */
    private float scheduleAttack(Attack a, float start) {
        schedule.add(new Event(start, a, false, true));
        switch (a) {
            case MORSE: {
                float t = start + 0.2f;
                String code = MorseCode.encode(morseWord);
                for (int i = 0; i < code.length(); i++) {
                    char c = code.charAt(i);
                    if (c == '.') {
                        schedule.add(new Event(t, a, false, false));
                        t += 0.25f;
                    } else if (c == '-') {
                        schedule.add(new Event(t, a, true, false));
                        t += 0.5f;
                    } else {
                        t += 0.3f;
                    }
                }
                return t + 0.6f;
            }
            case AIM: {
                int shots = more(pattern == BattleLogic.Pattern.REDUCED ? 2 : (pattern == BattleLogic.Pattern.BOOSTED ? 5 : 3));
                for (int i = 0; i < shots; i++) schedule.add(new Event(start + 0.3f + i * 0.6f, a, false, false));
                return start + 0.3f + shots * 0.6f + 0.8f;
            }
            case WALL: {
                int walls = more(pattern == BattleLogic.Pattern.REDUCED ? 2 : (pattern == BattleLogic.Pattern.BOOSTED ? 4 : 3));
                for (int i = 0; i < walls; i++) schedule.add(new Event(start + 0.3f + i * 0.9f, a, false, false));
                return start + 0.3f + walls * 0.9f + 1.0f;
            }
            case RING:
                schedule.add(new Event(start + 0.3f, a, false, false));
                return start + 3.0f;
            case BOUNCE:
                schedule.add(new Event(start + 0.3f, a, false, false));
                return start + 3.5f;
            default:
                return start + 1f;
        }
    }

    float intervalScale() {
        switch (pattern) {
            case REDUCED: return 1.3f;
            case BOOSTED: return 0.8f;
            default: return 1f;
        }
    }

    /** Скорость атак: на 50% быстрее, чем в v2.1 (умножение на 1.5 поверх режима). */
    static final float SPEED_UP = 1.5f;
    /** Количество фигур в атаках: на 40% больше, чем в v2.1. */
    static final float COUNT_UP = 1.4f;

    static int more(int base) {
        return Math.round(base * COUNT_UP);
    }

    float speedScale() {
        float mode;
        switch (pattern) {
            case REDUCED: mode = 0.75f; break;
            case BOOSTED: mode = 1.25f; break;
            default: mode = 1f;
        }
        return mode * SPEED_UP;
    }

    /** Атака, которая сейчас идёт (для подписи на арене). */
    Attack currentAttack() {
        return current;
    }

    private static float radiusOf(Kind k) {
        switch (k) {
            case GEAR: case CRESCENT: case CASSETTE: return 10f;
            case DOT: return 3f;
            case DASH: return 4f;
            default: return 8f;
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

    /** Шаг симуляции. Возвращает число новых попаданий (BattleView сразу отнимает ХП игрока). */
    int update(float dt) {
        if (finished()) return 0;
        time += dt;
        float len = (float) Math.sqrt(stickX * stickX + stickY * stickY);
        if (len > 1f) {
            stickX /= len;
            stickY /= len;
        }
        moveHeartTo(heartX + stickX * HEART_SPEED * dt, heartY + stickY * HEART_SPEED * dt);

        while (next < schedule.size() && schedule.get(next).at <= time) {
            run(schedule.get(next++));
        }

        int newHits = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            it.x += it.vx * dt;
            it.y += it.vy * dt;
            it.rot += it.spin * dt;
            if (it.bounce) {
                if (it.x < it.r) { it.x = it.r; it.vx = Math.abs(it.vx); }
                if (it.x > W - it.r) { it.x = W - it.r; it.vx = -Math.abs(it.vx); }
                if (it.y < it.r) { it.y = it.r; it.vy = Math.abs(it.vy); }
                if (it.y > H - it.r) { it.y = H - it.r; it.vy = -Math.abs(it.vy); }
            }
            if (it.life >= 0) it.life -= dt;
            if (!it.hit && dist(it.x, it.y, heartX, heartY) < it.r * 0.6f + HEART / 2f - 2f) {
                it.hit = true;
                hits++;
                newHits++;
            }
            boolean expired = it.life >= 0 && it.life <= 0;
            boolean gone = expired || (!it.bounce && (it.y > H + 30f || it.y < -40f || it.x < -40f || it.x > W + 40f));
            if (gone) items.remove(i);
        }
        return newHits;
    }

    private void run(Event e) {
        if (e.start) {
            startAttack(e.attack);
            return;
        }
        float sp = speedScale();
        switch (e.attack) {
            case MORSE:
                if (e.dash) spawnWall(sp * 60f, 36f, 22f);
                else spawnAimed(Kind.STAR, sp * 110f, 6f);   // точка — колючка в сердце
                break;
            case AIM:
                spawnAimed(Kind.BOLT, sp * 130f, 8f);
                break;
            case WALL:
                spawnWall(sp * 55f, 40f, 22f);
                break;
            case RING:
                spawnRing(sp * 34f);
                break;
            case BOUNCE:
                spawnBounce(sp * 70f);
                break;
            default:
                break;
        }
    }

    private void startAttack(Attack a) {
        attackStarts++;
        attackLog.add(a);
        current = a;
    }

    /** Фигура из верхнего края, летящая прямо в текущую позицию сердца. */
    private void spawnAimed(Kind k, float speed, float r) {
        float sx = 10f + rng.nextFloat() * (W - 20f);
        float sy = -8f;
        float[] v = aim(sx, sy, heartX, heartY, speed);
        Item it = new Item(k, rng.nextInt(TEX_COUNT), sx, sy, v[0], v[1], r);
        it.spin = (rng.nextFloat() * 2f - 1f) * 3f;
        spawn(it);
    }

    /** Направление от (x,y) к (tx,ty) с заданной скоростью. */
    static float[] aim(float x, float y, float tx, float ty, float speed) {
        float dx = tx - x, dy = ty - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-3f) return new float[] {0f, speed};
        return new float[] {dx / d * speed, dy / d * speed};
    }

    /** Стена сверху вниз с дырой (ширина gap) на линии сердца. */
    private void spawnWall(float speed, float gap, float step) {
        float gx = heartX;
        for (float x = 6f; x <= W - 6f; x += step) {
            if (Math.abs(x - gx) < gap / 2f) continue;
            Item it = new Item(Kind.CASSETTE, rng.nextInt(TEX_COUNT), x, -10f, 0f, speed, 9f);
            it.spin = (rng.nextFloat() * 2f - 1f) * 1.5f;
            spawn(it);
        }
    }

    /** Кольцо фигур к центру, со щелью в направлении сердца. */
    private void spawnRing(float speed) {
        float cx = W / 2f, cy = H / 2f, R = 96f;
        float gapAngle = (float) Math.atan2(heartY - cy, heartX - cx);
        int n = more(12);
        for (int i = 0; i < n; i++) {
            double a = i * 2 * Math.PI / n;
            double diff = Math.abs(normalize(a - gapAngle));
            if (diff < 0.45) continue;
            float x = cx + R * (float) Math.cos(a), y = cy + R * (float) Math.sin(a);
            float[] v = aim(x, y, cx, cy, speed);
            Item it = new Item(Kind.GEAR, rng.nextInt(TEX_COUNT), x, y, v[0], v[1], 9f);
            it.spin = 2.2f;
            spawn(it);
        }
    }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** Монеты, отскакивающие от стен; живут 3 секунды. */
    private void spawnBounce(float speed) {
        for (int i = 0; i < more(3); i++) {
            float x = 20f + rng.nextFloat() * (W - 40f);
            float y = 20f + rng.nextFloat() * (H - 40f);
            double a = rng.nextFloat() * 2 * Math.PI;
            Item it = new Item(Kind.COIN, rng.nextInt(TEX_COUNT), x, y,
                    (float) Math.cos(a) * speed, (float) Math.sin(a) * speed, 8f);
            it.bounce = true;
            it.life = 3.0f;
            it.spin = 1.5f;
            spawn(it);
        }
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
