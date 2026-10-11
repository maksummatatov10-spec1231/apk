package com.example.airhotkey;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;

import java.util.ArrayList;
import java.util.Random;

/**
 * Состояние игры, логика и отрисовка.
 *
 * Правила: внизу экрана всплывают неоновые клавиши. Сверху показана цепочка горячих клавиш —
 * их нужно поймать по порядку: тапом по клавише или нажатием той же клавиши на клавиатуре.
 * Тап по чужой клавише — промах (комбо сбрасывается). Нужная клавиша улетела за верх — теряется жизнь.
 */
final class World {

    static final int MENU = 0, PLAY = 1, PAUSE = 2, OVER = 3;

    private static final String POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int[] NEON = {0xFF00E5FF, 0xFFFF2BD6, 0xFF7CFF4F, 0xFFFFB020, 0xFFFFF35C, 0xFF9D6BFF};
    private static final int C_CYAN = 0xFF00E5FF, C_MAG = 0xFFFF2BD6, C_LIME = 0xFF7CFF4F, C_GOLD = 0xFFFFF35C;
    private static final int C_RED = 0xFFFF3B5C, C_WHITE = 0xFFFFFFFF, C_DIM = 0xFF6E5C8C;
    private static final Typeface TF_BOLD = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private static final Typeface TF_REG = Typeface.create("sans-serif", Typeface.NORMAL);

    static final class Orb {
        float x, y, vy, r, phase;
        char ch;
        int color;
        boolean demo;
        final float[] tx = new float[8], ty = new float[8];
        int tn;
    }

    private static final class Star {
        float x, y, v, size, a;
    }

    private static final class Nebula {
        float x, y, r, phase;
        int color;
    }

    final Random rnd = new Random();
    final Fx fx = new Fx(rnd);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SharedPreferences prefs;
    private ToneGenerator tone;

    int state = MENU;
    private int w, h;
    private float s = 1f;         // масштаб: 1 = 1/360 ширины экрана
    private float time;
    private float stateT;

    // игровые показатели
    int score, best, lives, combo, maxCombo, level, chainsDone;
    char[] chain = new char[0];
    int chainPos;
    private float spawnT, spawnEvery;
    private float shakeT, shakeAmp;
    private float flashT;
    private int flashColor;

    final ArrayList<Orb> orbs = new ArrayList<Orb>();
    private final ArrayList<Star> stars = new ArrayList<Star>();
    private final ArrayList<Nebula> nebulas = new ArrayList<Nebula>();
    private float gridOffset;

    // кэшированные шейдеры
    private Shader bgShader, vignette;
    private int shaderW = -1, shaderH = -1;

    World(Context ctx) {
        prefs = ctx.getSharedPreferences("airhotkey", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 45);
        } catch (RuntimeException e) {
            tone = null;   // звук необязателен
        }
        for (int i = 0; i < 110; i++) {
            Star st = new Star();
            st.x = rnd.nextFloat();
            st.y = rnd.nextFloat();
            st.v = 0.02f + rnd.nextFloat() * 0.09f;   // доля высоты в секунду
            st.size = 0.6f + rnd.nextFloat() * 2.0f;
            st.a = 0.3f + rnd.nextFloat() * 0.7f;
            stars.add(st);
        }
        for (int i = 0; i < 4; i++) {
            Nebula n = new Nebula();
            n.x = rnd.nextFloat();
            n.y = rnd.nextFloat();
            n.r = 0.35f + rnd.nextFloat() * 0.3f;
            n.phase = rnd.nextFloat() * 6f;
            n.color = NEON[i % NEON.length];
            nebulas.add(n);
        }
    }

    // ---------- размер и шейдеры ----------

    void layout(int width, int height) {
        if (width == w && height == h) return;
        w = width;
        h = height;
        s = Math.min(w, h) / 360f;
        fx.setScale(s);
        bgShader = new LinearGradient(0, 0, 0, h, 0xFF12002B, 0xFF02000A, Shader.TileMode.CLAMP);
        vignette = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.72f,
                0x00000000, 0xCC000000, Shader.TileMode.CLAMP);
        shaderW = w;
        shaderH = h;
    }

    // ---------- управление ----------

    /** Тап по экрану. */
    void tap(float x, float y) {
        switch (state) {
            case MENU:
                startGame();
                return;
            case PAUSE:
                state = PLAY;
                return;
            case OVER:
                if (stateT > 0.8f) startGame();
                return;
            default:
                break;
        }
        // кнопка паузы — правый верхний угол
        float px = w - 34 * s, py = 42 * s, pr = 26 * s;
        if (dist2(x, y, px, py) <= pr * pr) {
            pause();
            return;
        }
        Orb target = null;
        float best2 = Float.MAX_VALUE;
        for (Orb o : orbs) {
            if (o.demo) continue;
            float d2 = dist2(x, y, o.x, o.y);
            float reach = o.r * 1.25f;
            if (d2 <= reach * reach && d2 < best2) {
                best2 = d2;
                target = o;
            }
        }
        if (target != null) judge(target);
    }

    /** Нажатие клавиши на физической клавиатуре. Возвращает true, если клавиша обработана. */
    boolean key(int keyCode) {
        char ch = keyChar(keyCode);
        if (ch == 0) return false;
        if (state == MENU) {
            startGame();
            return true;
        }
        if (state != PLAY) return false;
        Orb target = null;
        for (Orb o : orbs) {
            if (o.demo || o.ch != ch) continue;
            // самая опасная клавиша — та, что ближе к верхнему краю
            if (target == null || o.y < target.y) target = o;
        }
        if (target != null && ch == needed()) {
            judge(target);
            return true;
        }
        return false;
    }

    /** Кнопка «Назад»: из игры — пауза, из паузы — меню. Возвращает true, если обработала. */
    boolean back() {
        if (state == PLAY) {
            pause();
            return true;
        }
        if (state == PAUSE || state == OVER) {
            state = MENU;
            stateT = 0;
            orbs.clear();
            fx.clear();
            return true;
        }
        return false;
    }

    void pauseIfPlaying() {
        if (state == PLAY) pause();
    }

    private void pause() {
        state = PAUSE;
        stateT = 0;
    }

    private static char keyChar(int code) {
        if (code >= android.view.KeyEvent.KEYCODE_A && code <= android.view.KeyEvent.KEYCODE_Z) {
            return (char) ('A' + (code - android.view.KeyEvent.KEYCODE_A));
        }
        if (code >= android.view.KeyEvent.KEYCODE_0 && code <= android.view.KeyEvent.KEYCODE_9) {
            return (char) ('0' + (code - android.view.KeyEvent.KEYCODE_0));
        }
        return 0;
    }

    // ---------- игра ----------

    private void startGame() {
        state = PLAY;
        stateT = 0;
        score = 0;
        lives = 3;
        combo = 0;
        maxCombo = 0;
        level = 1;
        chainsDone = 0;
        orbs.clear();
        fx.clear();
        spawnT = 0.4f;
        newChain();
    }

    private void newChain() {
        int len = Math.min(6, 3 + level / 3);
        chain = new char[len];
        for (int i = 0; i < len; i++) {
            chain[i] = POOL.charAt(rnd.nextInt(POOL.length()));
        }
        chainPos = 0;
    }

    char needed() {
        return chain.length == 0 ? 0 : chain[chainPos];
    }

    private float spawnInterval() {
        return Math.max(0.42f, 1.25f - level * 0.08f);
    }

    private float orbSpeed() {
        return (64f + level * 9f) * s;
    }

    private void spawnOrb(boolean demo) {
        Orb o = new Orb();
        o.demo = demo;
        o.r = (24f + rnd.nextFloat() * 4f) * s;
        float margin = o.r + 12 * s;
        o.x = margin + rnd.nextFloat() * Math.max(1f, w - 2 * margin);
        o.y = h + o.r + 4 * s;
        o.vy = orbSpeed() * (0.85f + rnd.nextFloat() * 0.45f);
        o.phase = rnd.nextFloat() * 6.28f;
        // чаще попадается нужная клавиша, чтобы цепочку можно было пройти
        if (!demo && chain.length > 0 && rnd.nextFloat() < 0.5f) {
            o.ch = needed();
        } else {
            o.ch = POOL.charAt(rnd.nextInt(POOL.length()));
        }
        o.color = NEON[rnd.nextInt(NEON.length)];
        orbs.add(o);
    }

    private void judge(Orb o) {
        if (o.ch == needed() && state == PLAY) {
            success(o);
        } else {
            miss(o);
        }
    }

    private void success(Orb o) {
        orbs.remove(o);
        combo++;
        maxCombo = Math.max(maxCombo, combo);
        int mult = Math.min(5, 1 + combo / 5);
        int gain = 10 * mult;
        score += gain;
        fx.burst(o.x, o.y, o.color, 30);
        fx.ring(o.x, o.y, o.color, 90 * s, 0.45f);
        fx.label(o.x, o.y - o.r, "+" + gain, C_GOLD, 22 * s, 0.9f);
        if (combo >= 3 && combo % 5 == 0) {
            fx.label(w / 2f, h * 0.44f, "КОМБО x" + combo, C_MAG, 26 * s, 1.1f);
        }
        beep(true);
        chainPos++;
        if (chainPos >= chain.length) {
            chainComplete();
        }
    }

    private void chainComplete() {
        chainsDone++;
        int bonus = 150 * level;
        score += bonus;
        int newLevel = Math.min(12, 1 + chainsDone / 4);
        if (newLevel > level) {
            level = newLevel;
            fx.label(w / 2f, h * 0.5f, "УРОВЕНЬ " + level, C_CYAN, 30 * s, 1.4f);
        }
        fx.label(w / 2f, h * 0.36f, "ЦЕПОЧКА +" + bonus, C_LIME, 26 * s, 1.3f);
        fx.ring(w / 2f, h * 0.36f, C_LIME, Math.max(w, h) * 0.9f, 0.8f);
        flashT = 0.32f;
        flashColor = C_LIME;
        spawnEvery = spawnInterval();
        newChain();
    }

    private void miss(Orb o) {
        orbs.remove(o);
        combo = 0;
        score = Math.max(0, score - 20);
        fx.burst(o.x, o.y, C_DIM, 14);
        fx.label(o.x, o.y - o.r, "ПРОМАХ", C_RED, 20 * s, 0.8f);
        shake(0.22f, 5f * s);
        beep(false);
    }

    private void loseLife(Orb o) {
        lives--;
        combo = 0;
        fx.burst(o.x, Math.min(h * 0.2f, o.y), C_RED, 40);
        fx.label(w / 2f, h * 0.3f, "−ЖИЗНЬ", C_RED, 28 * s, 1.0f);
        shake(0.45f, 12f * s);
        flashT = 0.35f;
        flashColor = C_RED;
        if (lives <= 0) gameOver();
    }

    private void gameOver() {
        state = OVER;
        stateT = 0;
        if (score > best) {
            best = score;
            prefs.edit().putInt("best", best).apply();
        }
        flashT = 0.6f;
        flashColor = C_MAG;
        shake(0.6f, 14f * s);
    }

    private void shake(float t, float amp) {
        shakeT = t;
        shakeAmp = amp;
    }

    private void beep(boolean ok) {
        if (tone == null) return;
        try {
            tone.startTone(ok ? ToneGenerator.TONE_PROP_BEEP : ToneGenerator.TONE_PROP_NACK, 35);
        } catch (RuntimeException ignored) {
            // звук не критичен
        }
    }

    // ---------- обновление ----------

    void update(float dt) {
        time += dt;
        stateT += dt;
        gridOffset = (gridOffset + dt * (state == PAUSE ? 0f : 0.45f)) % 1f;
        updateBackground(dt);
        if (shakeT > 0) shakeT -= dt;
        if (flashT > 0) flashT -= dt;

        switch (state) {
            case MENU:
                if (spawnT <= 0) {
                    spawnOrb(true);
                    spawnT = 0.7f + rnd.nextFloat() * 0.5f;
                }
                spawnT -= dt;
                moveOrbs(dt, true);
                break;
            case PLAY:
                if (spawnEvery <= 0) spawnEvery = spawnInterval();
                spawnT -= dt;
                if (spawnT <= 0) {
                    spawnOrb(false);
                    spawnT = spawnEvery;
                    spawnEvery = spawnInterval() * (0.9f + rnd.nextFloat() * 0.2f);
                }
                moveOrbs(dt, false);
                break;
            case OVER:
                moveOrbs(dt, true);
                break;
            default:
                break;
        }
        fx.update(dt);
    }

    private void moveOrbs(float dt, boolean demoOnly) {
        for (int i = orbs.size() - 1; i >= 0; i--) {
            Orb o = orbs.get(i);
            o.phase += dt * 2.2f;
            o.y -= o.vy * dt;
            o.x += (float) Math.sin(o.phase) * 22f * s * dt;
            // след
            pushTrail(o);
            if (o.y + o.r < 0) {
                orbs.remove(i);
                if (!o.demo && state == PLAY && o.ch == needed()) loseLife(o);
                continue;
            }
            if (state == OVER && o.y < h * 0.5f) orbs.remove(i);
        }
        if (demoOnly) {
            // в меню декоративные клавиши не должны копиться
            for (int i = orbs.size() - 1; i >= 0; i--) {
                if (!orbs.get(i).demo) orbs.remove(i);
            }
        }
    }

    private void pushTrail(Orb o) {
        System.arraycopy(o.tx, 0, o.tx, 1, o.tx.length - 1);
        System.arraycopy(o.ty, 0, o.ty, 1, o.ty.length - 1);
        o.tx[0] = o.x;
        o.ty[0] = o.y;
        if (o.tn < o.tx.length) o.tn++;
    }

    private void updateBackground(float dt) {
        for (Star st : stars) {
            st.y += st.v * dt * (state == PLAY ? 1.6f : 1f);
            if (st.y > 1f) {
                st.y -= 1f;
                st.x = rnd.nextFloat();
            }
        }
    }

    // ---------- отрисовка ----------

    void draw(Canvas c) {
        if (w == 0) return;
        c.save();
        if (shakeT > 0) {
            float k = Math.max(0f, shakeT);
            c.translate((rnd.nextFloat() * 2 - 1) * shakeAmp * k * 2, (rnd.nextFloat() * 2 - 1) * shakeAmp * k * 2);
        }
        drawBackground(c);
        drawOrbs(c);
        fx.draw(c);
        drawHud(c);
        c.drawRect(0, 0, w, h, shaderPaint(vignette));
        if (flashT > 0) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Fx.withAlpha(flashColor, flashT * 0.5f));
            c.drawRect(0, 0, w, h, p);
        }
        switch (state) {
            case MENU: drawMenu(c); break;
            case PAUSE: drawPause(c); break;
            case OVER: drawOver(c); break;
            default: break;
        }
        c.restore();
    }

    private Paint shaderPaint(Shader sh) {
        p.reset();
        p.setAntiAlias(true);
        p.setShader(sh);
        return p;
    }

    private void drawBackground(Canvas c) {
        p.reset();
        p.setShader(bgShader);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // туманности: медленно дышат
        for (Nebula n : nebulas) {
            float breathe = 1f + 0.08f * (float) Math.sin(time * 0.6f + n.phase);
            float cx = n.x * w + (float) Math.sin(time * 0.1f + n.phase) * 40 * s;
            float cy = n.y * h + (float) Math.cos(time * 0.08f + n.phase) * 40 * s;
            float r = n.r * Math.max(w, h) * breathe;
            p.setStyle(Paint.Style.FILL);
            for (int k = 0; k < 4; k++) {
                p.setColor(Fx.withAlpha(n.color, 0.035f + k * 0.012f));
                c.drawCircle(cx, cy, r * (1f - k * 0.2f), p);
            }
        }

        // синтвейв-сетка внизу
        float horizon = h * 0.74f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * s);
        for (int i = 0; i < 14; i++) {
            float t = ((i + gridOffset) / 14f);
            float y = horizon + (h - horizon) * t * t;
            p.setColor(Fx.withAlpha(C_MAG, 0.08f + 0.35f * t));
            c.drawLine(0, y, w, y, p);
        }
        for (int i = -10; i <= 10; i++) {
            float x0 = w / 2f + i * w * 0.02f;
            float x1 = w / 2f + i * w * 0.42f;
            p.setColor(Fx.withAlpha(C_CYAN, 0.12f));
            c.drawLine(x0, horizon, x1, h, p);
        }
        p.setStyle(Paint.Style.FILL);

        // звёзды
        for (Star st : stars) {
            p.setColor(Fx.withAlpha(C_WHITE, st.a * 0.8f));
            c.drawCircle(st.x * w, st.y * h, st.size * s * 0.6f, p);
        }
    }

    private void drawOrbs(Canvas c) {
        for (Orb o : orbs) {
            boolean target = !o.demo && state == PLAY && o.ch == needed();
            float pulse = 1f + 0.05f * (float) Math.sin(o.phase * 2f);
            float r = o.r * pulse;

            // след
            for (int i = 1; i < o.tn; i++) {
                float a = 0.25f * (1f - i / (float) o.tn);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Fx.withAlpha(o.color, a));
                c.drawCircle(o.tx[i], o.ty[i], r * (0.85f - i * 0.08f), p);
            }

            // свечение
            float glowA = target ? 0.6f : 0.4f;
            for (int k = 3; k >= 1; k--) {
                p.setColor(Fx.withAlpha(o.color, glowA * (0.06f + 0.05f * (3 - k))));
                c.drawCircle(o.x, o.y, r * (1f + 0.45f * k), p);
            }

            // тело: тёмная заливка, неоновый контур, блик
            p.setColor(Fx.withAlpha(0xFF0A0118, 0.85f));
            c.drawCircle(o.x, o.y, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * s);
            p.setColor(Fx.withAlpha(o.color, 1f));
            c.drawCircle(o.x, o.y, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Fx.withAlpha(C_WHITE, 0.22f));
            c.drawCircle(o.x - r * 0.35f, o.y - r * 0.4f, r * 0.22f, p);

            // подсветка нужной клавиши: вращающиеся дуги
            if (target) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f * s);
                p.setColor(Fx.withAlpha(C_GOLD, 0.95f));
                float start = (time * 220f) % 360f;
                for (int k = 0; k < 3; k++) {
                    path.reset();
                    c.drawArc(o.x - r * 1.35f, o.y - r * 1.35f, o.x + r * 1.35f, o.y + r * 1.35f,
                            start + k * 120f, 60f, false, p);
                }
                p.setStyle(Paint.Style.FILL);
            }

            // буква
            drawText(c, String.valueOf(o.ch), o.x, o.y + r * 0.36f, r * 1.05f,
                    C_WHITE, true, 12f * s * (target ? 1.4f : 0.9f), o.color, Paint.Align.CENTER);
        }
    }

    private void drawHud(Canvas c) {
        if (state == MENU) return;

        // очки
        drawText(c, "ОЧКИ", 22 * s, 24 * s, 11 * s, C_DIM, false, 0, 0, Paint.Align.LEFT);
        drawText(c, String.valueOf(score), 22 * s, 56 * s, 30 * s, C_WHITE, true, 14 * s, C_CYAN, Paint.Align.LEFT);
        drawText(c, "РЕКОРД " + Math.max(best, score), 22 * s, 76 * s, 11 * s, C_DIM, false, 0, 0, Paint.Align.LEFT);

        // жизни: сердечки
        for (int i = 0; i < 3; i++) {
            float x = 22 * s + i * 26 * s, y = 100 * s;
            boolean on = i < lives;
            drawHeart(c, x, y, 9 * s, on ? C_RED : 0x33FFFFFF, on);
        }

        // уровень и комбо
        drawText(c, "ЛВЛ " + level, w / 2f, 34 * s, 16 * s, C_CYAN, true, 10 * s, C_CYAN, Paint.Align.CENTER);
        if (combo >= 2) {
            drawText(c, "x" + Math.min(5, 1 + combo / 5) + "  ·  " + combo, w / 2f, 58 * s, 13 * s,
                    C_MAG, true, 8 * s, C_MAG, Paint.Align.CENTER);
        }

        // кнопка паузы
        float px = w - 34 * s, py = 42 * s, pr = 22 * s;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Fx.withAlpha(0xFF1A0530, 0.9f));
        c.drawCircle(px, py, pr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * s);
        p.setColor(Fx.withAlpha(C_CYAN, 0.9f));
        c.drawCircle(px, py, pr, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_WHITE);
        c.drawRect(px - 7 * s, py - 7 * s, px - 2 * s, py + 7 * s, p);
        c.drawRect(px + 2 * s, py - 7 * s, px + 7 * s, py + 7 * s, p);

        drawChain(c);
    }

    /** Цепочка горячих клавиш: пройденные — зелёные с галочкой, текущая — пульсирует. */
    private void drawChain(Canvas c) {
        int n = chain.length;
        if (n == 0) return;
        float cell = 46 * s, gap = 8 * s;
        float total = n * cell + (n - 1) * gap;
        float x0 = (w - total) / 2f;
        float y0 = 124 * s;
        drawText(c, "ЦЕПОЧКА ГОРЯЧИХ КЛАВИШ", w / 2f, y0 - 10 * s, 10 * s, C_DIM, true, 0, 0, Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            float x = x0 + i * (cell + gap);
            float y = y0;
            boolean done = i < chainPos;
            boolean current = i == chainPos;
            float cx = x + cell / 2f, cy = y + cell / 2f;
            float scale = current ? 1f + 0.08f * (float) Math.sin(time * 6f) : 1f;
            float half = cell / 2f * scale;
            int color = done ? C_LIME : current ? C_CYAN : C_DIM;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Fx.withAlpha(color, current ? 0.25f : done ? 0.18f : 0.06f));
            c.drawRoundRect(cx - half - 4 * s, cy - half - 4 * s, cx + half + 4 * s, cy + half + 4 * s,
                    14 * s, 14 * s, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth((current ? 2.5f : 1.5f) * s);
            p.setColor(Fx.withAlpha(color, current ? 1f : 0.7f));
            c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, 12 * s, 12 * s, p);
            p.setStyle(Paint.Style.FILL);

            if (done) {
                // галочка из линий, без зависимости от шрифтов
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f * s);
                p.setColor(C_LIME);
                path.reset();
                path.moveTo(cx - 9 * s, cy + 1 * s);
                path.lineTo(cx - 2 * s, cy + 8 * s);
                path.lineTo(cx + 10 * s, cy - 7 * s);
                c.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                drawText(c, String.valueOf(chain[i]), cx, cy + 9 * s * scale, 22 * s * scale,
                        current ? C_WHITE : C_DIM, true, current ? 10 * s : 0, color, Paint.Align.CENTER);
            }
        }
    }

    private void drawHeart(Canvas c, float x, float y, float r, int color, boolean glow) {
        path.reset();
        path.moveTo(x, y + r * 0.9f);
        path.cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.6f, y - r * 1.3f, x, y - r * 0.4f);
        path.cubicTo(x + r * 0.6f, y - r * 1.3f, x + r * 1.6f, y - r * 0.2f, x, y + r * 0.9f);
        p.setStyle(Paint.Style.FILL);
        if (glow) {
            p.setColor(Fx.withAlpha(color, 0.3f));
            c.drawCircle(x, y, r * 1.8f, p);
        }
        p.setColor(color);
        c.drawPath(path, p);
    }

    private void drawMenu(Canvas c) {
        dim(c, 0.35f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3f);
        float cy = h * 0.33f;
        drawText(c, "AIR HOTKEY", w / 2f, cy, 44 * s, C_WHITE, true, 22 * s, C_MAG, Paint.Align.CENTER);
        drawText(c, "CHALLENGE", w / 2f, cy + 44 * s, 26 * s, C_CYAN, true, 18 * s, C_CYAN, Paint.Align.CENTER);
        drawText(c, "Лови подсказанные клавиши по порядку.", w / 2f, h * 0.52f, 13 * s, C_WHITE, false, 0, 0, Paint.Align.CENTER);
        drawText(c, "Тап по клавише или нажми её на клавиатуре.", w / 2f, h * 0.52f + 20 * s, 13 * s, C_DIM, false, 0, 0, Paint.Align.CENTER);
        drawText(c, "Чужой тап — промах. Пропуск — минус жизнь.", w / 2f, h * 0.52f + 40 * s, 12 * s, C_DIM, false, 0, 0, Paint.Align.CENTER);
        if (best > 0) {
            drawText(c, "РЕКОРД " + best, w / 2f, h * 0.62f, 16 * s, C_GOLD, true, 8 * s, C_GOLD, Paint.Align.CENTER);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Fx.withAlpha(C_LIME, 0.12f + 0.12f * pulse));
        float bw = 220 * s, bh = 56 * s;
        c.drawRoundRect(w / 2f - bw / 2 - 6 * s, h * 0.8f - bh / 2 - 6 * s, w / 2f + bw / 2 + 6 * s, h * 0.8f + bh / 2 + 6 * s,
                30 * s, 30 * s, p);
        p.setColor(Fx.withAlpha(0xFF0A0118, 0.9f));
        c.drawRoundRect(w / 2f - bw / 2, h * 0.8f - bh / 2, w / 2f + bw / 2, h * 0.8f + bh / 2, 28 * s, 28 * s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * s);
        p.setColor(Fx.withAlpha(C_LIME, 0.6f + 0.4f * pulse));
        c.drawRoundRect(w / 2f - bw / 2, h * 0.8f - bh / 2, w / 2f + bw / 2, h * 0.8f + bh / 2, 28 * s, 28 * s, p);
        p.setStyle(Paint.Style.FILL);
        drawText(c, "СТАРТ", w / 2f, h * 0.8f + 9 * s, 22 * s, C_LIME, true, 12 * s, C_LIME, Paint.Align.CENTER);
    }

    private void drawPause(Canvas c) {
        dim(c, 0.6f);
        drawText(c, "ПАУЗА", w / 2f, h * 0.45f, 40 * s, C_CYAN, true, 18 * s, C_CYAN, Paint.Align.CENTER);
        drawText(c, "Тап — продолжить", w / 2f, h * 0.52f, 16 * s, C_WHITE, false, 0, 0, Paint.Align.CENTER);
        drawText(c, "«Назад» — в меню", w / 2f, h * 0.52f + 26 * s, 13 * s, C_DIM, false, 0, 0, Paint.Align.CENTER);
    }

    private void drawOver(Canvas c) {
        dim(c, 0.7f);
        float a = Math.min(1f, stateT * 2f);
        drawText(c, "КОНЕЦ ИГРЫ", w / 2f, h * 0.42f, 38 * s, Fx.withAlpha(C_RED, a), true, 18 * s, C_RED, Paint.Align.CENTER);
        drawText(c, "Очки: " + score, w / 2f, h * 0.5f, 22 * s, Fx.withAlpha(C_WHITE, a), true, 0, 0, Paint.Align.CENTER);
        drawText(c, "Рекорд: " + best, w / 2f, h * 0.5f + 30 * s, 16 * s, Fx.withAlpha(C_GOLD, a), false, 0, 0, Paint.Align.CENTER);
        drawText(c, "Макс. комбо: " + maxCombo + "   Уровень: " + level, w / 2f, h * 0.5f + 56 * s, 13 * s,
                Fx.withAlpha(C_DIM, a), false, 0, 0, Paint.Align.CENTER);
        if (stateT > 0.8f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 4f);
            drawText(c, "ТАП — ЗАНОВО", w / 2f, h * 0.66f, 18 * s, Fx.withAlpha(C_LIME, 0.5f + 0.5f * pulse),
                    true, 10 * s, C_LIME, Paint.Align.CENTER);
        }
    }

    private void dim(Canvas c, float alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Fx.withAlpha(0xFF02000A, alpha));
        c.drawRect(0, 0, w, h, p);
    }

    /** Текст с неоновым свечением. y — базовая линия. */
    private void drawText(Canvas c, String text, float x, float y, float size, int color, boolean bold,
                          float glow, int glowColor, Paint.Align align) {
        p.reset();
        p.setAntiAlias(true);
        p.setTypeface(bold ? TF_BOLD : TF_REG);
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        if (glow > 0) {
            p.setShadowLayer(glow, 0, 0, Fx.withAlpha(glowColor, 0.95f));
        }
        c.drawText(text, x, y, p);
        p.clearShadowLayer();
        p.setTextAlign(Paint.Align.LEFT);
    }

    private static float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
