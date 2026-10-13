package com.example.junkil;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Экран боя с Джанклимом. Рисует всё в логических координатах 360×660 (масштаб под экран).
 * Касания: меню боя, тайминг (тап в полоску), сигнал Морзе, джойстик на арене.
 */
final class BattleView extends View {

    static final float LW = 360f;
    static final float LH = 660f;
    static final float ARENA_L = 30f, ARENA_T = 336f, ARENA_R = 330f, ARENA_B = 556f;
    static final float JOY_X = 180f, JOY_Y = 618f, JOY_R = 30f;

    static final int BG = 0xFF0C0A16;
    static final int PANEL = 0xFF000000;
    static final int BORDER = 0xFFFFFFFF;
    static final int PURPLE = 0xFF8B5CF6;
    static final int PURPLE_DARK = 0xFF2A1B4A;
    static final int YELLOW = 0xFFFFD400;
    static final int RED = 0xFFFF3B3B;
    static final int GREY = 0xFF555555;
    static final int TEXT = 0xFFFFFFFF;
    static final int DIM = 0xFF8A8A8A;

    private static final String SUB_NONE = "", SUB_ACT = "ACT", SUB_ITEM = "ITEM";

    private final Paint paint = new Paint();
    private final Bitmap[] sprites;            // [NORMAL, ANGRY, SAD]
    private final Random rng;
    private final RectF dst = new RectF();
    BattleLogic logic;
    ArenaSim sim;
    private String sub = SUB_NONE;
    private float s = 1f, ox, oy;
    private float fightT, fightMarker;
    private float shake, flash;
    private boolean joyActive;
    private int lastAccuracyPct = -1;

    BattleView(Context context, Bitmap[] sprites, long seed) {
        super(context);
        this.sprites = sprites;
        this.rng = new Random(seed);
        this.logic = newLogic();
    }

    private BattleLogic newLogic() {
        BattleLogic l = new BattleLogic(rng);
        l.line = Phrases.INTRO;
        return l;
    }

    /** Одна кнопка: id — что делает касание, l/t/r/b — в логических координатах. */
    private static final class Btn {
        final String id, label, hint;
        final float l, t, r, b;
        final boolean enabled;

        Btn(String id, String label, String hint, float l, float t, float r, float b, boolean enabled) {
            this.id = id;
            this.label = label;
            this.hint = hint;
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
            this.enabled = enabled;
        }

        boolean hit(float x, float y) {
            return enabled && x >= l && x <= r && y >= t && y <= b;
        }
    }

    // ---------- раскладка ----------

    private static float gx(int col) { return 40f + col * 150f; }
    private static float gy(int row) { return 486f + row * 46f; }

    private List<Btn> buttons() {
        List<Btn> bs = new ArrayList<>();
        if (logic.phase == BattleLogic.Phase.MENU) {
            if (SUB_ACT.equals(sub)) {
                bs.add(new Btn("LOOK", Phrases.ACT_LOOK, null, gx(0), gy(0), gx(0) + 140, gy(0) + 38, true));
                bs.add(new Btn("MORSE", Phrases.ACT_MORSE, null, gx(1), gy(0), gx(1) + 140, gy(0) + 38, true));
                bs.add(new Btn("HAGGLE", Phrases.ACT_HAGGLE, null, gx(0), gy(1), gx(0) + 140, gy(1) + 38, true));
                bs.add(new Btn("BACK", Phrases.BACK, null, gx(1), gy(1), gx(1) + 140, gy(1) + 38, true));
            } else if (SUB_ITEM.equals(sub)) {
                bs.add(new Btn("BATTERY", Phrases.ITEM_BATTERY + " x" + logic.batteries, null,
                        gx(0), gy(0), gx(0) + 140, gy(0) + 38, logic.batteries > 0));
                bs.add(new Btn("BACK", Phrases.BACK, null, gx(1), gy(0), gx(1) + 140, gy(0) + 38, true));
            } else {
                bs.add(new Btn("FIGHT", Phrases.BTN_FIGHT, null, gx(0), gy(0), gx(0) + 140, gy(0) + 38, true));
                bs.add(new Btn("ACT", Phrases.BTN_ACT, null, gx(1), gy(0), gx(1) + 140, gy(0) + 38, true));
                bs.add(new Btn("ITEM", Phrases.BTN_ITEM, null, gx(0), gy(1), gx(0) + 140, gy(1) + 38, true));
                bs.add(new Btn("MERCY", Phrases.BTN_MERCY + " " + logic.satisfy + "%", null,
                        gx(1), gy(1), gx(1) + 140, gy(1) + 38, true));
            }
        } else if (logic.phase == BattleLogic.Phase.MORSE) {
            for (int i = 0; i < 3; i++) {
                int col = i % 2, row = i / 2;
                String code = MorseCode.encode(logic.morseOptions[i]);
                bs.add(new Btn("OPT" + i, logic.morseOptions[i], code,
                        gx(col), gy(row), gx(col) + 140, gy(row) + 38, true));
            }
        }
        return bs;
    }

    // ---------- рисование ----------

    private float X(float v) { return ox + v * s; }
    private float Y(float v) { return oy + v * s; }

    private void rect(Canvas c, float l, float t, float r, float b, int col) {
        paint.setColor(col);
        c.drawRect(X(l), Y(t), X(r), Y(b), paint);
    }

    private void frame(Canvas c, float l, float t, float r, float b, int border, int fill, float w) {
        rect(c, l - w, t - w, r + w, b + w, border);
        rect(c, l, t, r, b, fill);
    }

    private void text(Canvas c, String str, float x, float y, float size, int col, boolean bold, boolean center) {
        paint.setColor(col);
        paint.setTextSize(size * s);
        paint.setFakeBoldText(bold);
        float xx = center ? X(x) - paint.measureText(str) / 2f : X(x);
        c.drawText(str, xx, Y(y), paint);
    }

    private void circle(Canvas c, float cx, float cy, float r, int col) {
        paint.setColor(col);
        c.drawCircle(X(cx), Y(cy), r * s, paint);
    }

    /** Перенос текста по ширине панели (в логических единицах, шрифт size). */
    private List<String> wrap(String str, float maxW, float size) {
        List<String> out = new ArrayList<>();
        paint.setTextSize(size * s);
        paint.setFakeBoldText(false);
        StringBuilder cur = new StringBuilder();
        for (String w : str.split(" ")) {
            String trial = cur.length() == 0 ? w : cur + " " + w;
            if (paint.measureText(trial) / s > maxW && cur.length() > 0) {
                out.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(trial);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** Масштаб и поля: считаются и при отрисовке, и при касании (касание может прийти раньше кадра). */
    private void updateScale() {
        float vw = getWidth() > 0 ? getWidth() : LW;
        float vh = getHeight() > 0 ? getHeight() : LH;
        s = Math.min(vw / LW, vh / LH);
        ox = (vw - LW * s) / 2f;
        oy = (vh - LH * s) / 2f;
    }

    @Override
    protected void onDraw(Canvas c) {
        updateScale();
        rect(c, -10, -10, LW + 10, LH + 10, 0xFF000000);
        rect(c, 0, 0, LW, LH, BG);
        drawBackdrop(c);
        drawTop(c);
        drawPanel(c);
        boolean arena = logic.phase == BattleLogic.Phase.ARENA && sim != null;
        drawPlayer(c, arena ? 572f : 466f);
        if (arena) {
            drawArena(c);
        } else {
            drawButtons(c);
        }
        if (flash > 0) {
            rect(c, 0, 0, LW, 4, RED);
            rect(c, 0, LH - 4, LW, LH, RED);
        }
    }

    /** Фон: «помойка» на задворках Cyber City — полосы, мусорный бак, неоновый значок. */
    private void drawBackdrop(Canvas c) {
        for (int i = 0; i < 6; i++) {
            rect(c, 0, 90 + i * 40, LW, 92 + i * 40, 0xFF120E22);
        }
        rect(c, 260, 60, 340, 110, 0xFF1C1733);            // мусорный бак
        rect(c, 254, 54, 346, 62, 0xFF2A2440);
        text(c, "$", 318, 98, 26, 0xFF3A2F66, true, true);
    }

    private void drawTop(Canvas c) {
        text(c, Phrases.NAME, 20, 26, 16, TEXT, true, false);
        boolean arena = logic.phase == BattleLogic.Phase.ARENA && sim != null;
        String turnLabel = arena
                ? "ХОД " + logic.turn + " · " + Math.max(0, (int) Math.ceil(ArenaSim.DURATION - sim.time)) + " с"
                : "ХОД " + logic.turn;
        text(c, turnLabel, 270, 26, 12, DIM, false, false);
        // полоска HP Джанкила
        rect(c, 20, 34, 340, 44, 0xFF2B2B2B);
        float f = logic.junkHp / (float) BattleLogic.JUNK_MAX;
        rect(c, 20, 34, 20 + 320 * f, 44, PURPLE);
        text(c, logic.junkHp + "/" + BattleLogic.JUNK_MAX, 300, 56, 11, DIM, false, false);

        // спрайт (тряска при уроне)
        float dx = shake > 0 ? (float) Math.sin(shake * 60f) * 4f : 0f;
        Bitmap bm = sprites[logic.mood().ordinal()];
        dst.set(X(97 + dx), Y(60), X(263 + dx), Y(308));
        if (bm != null) {
            c.drawBitmap(bm, new Rect(0, 0, bm.getWidth(), bm.getHeight()), dst, paint);
        }

        // Довольство
        text(c, Phrases.SATISFY_LABEL + " " + logic.satisfy + "/" + BattleLogic.SATISFY_MAX, 20, 322, 11, DIM, false, false);
        rect(c, 20, 326, 340, 332, 0xFF2B2B2B);
        rect(c, 20, 326, 20 + 320 * logic.satisfy / (float) BattleLogic.SATISFY_MAX, 332, YELLOW);
    }

    private void drawPanel(Canvas c) {
        BattleLogic.Phase ph = logic.phase;
        if (ph == BattleLogic.Phase.ARENA) return;
        frame(c, 20, 336, 340, 452, BORDER, PANEL, 2);
        if (ph == BattleLogic.Phase.FIGHT) {
            text(c, Phrases.FIGHT_LABEL, 30, 358, 13, YELLOW, true, false);
            rect(c, 30, 372, 330, 396, 0xFF2B2B2B);
            rect(c, 30 + 300 * 0.4f, 372, 30 + 300 * 0.6f, 396, 0xFF2E7D32);   // зона попадания
            float mx = 30 + 300 * fightMarker;
            rect(c, mx - 2, 366, mx + 2, 402, TEXT);
            text(c, "нажми в полоску", 180, 432, 12, DIM, false, true);
            return;
        }
        if (ph == BattleLogic.Phase.MORSE) {
            text(c, Phrases.MORSE_TITLE, 30, 356, 13, YELLOW, true, false);
            drawSignal(c, logic.morseWord, 30, 378);
            text(c, "выбери слово, которое передано", 30, 430, 11, DIM, false, false);
            return;
        }
        if (ph == BattleLogic.Phase.WON || ph == BattleLogic.Phase.SPARED || ph == BattleLogic.Phase.LOST) {
            String head = ph == BattleLogic.Phase.WON ? "ПОБЕДА"
                    : ph == BattleLogic.Phase.SPARED ? "МИЛОСТЬ: СВОБОДНЫЙ ТОРГ" : "ПОРАЖЕНИЕ";
            text(c, head, 30, 360, 15, YELLOW, true, false);
            drawWrapped(c, logic.line, 30, 384, 14);
            text(c, Phrases.TAP_RESTART, 180, 442, 11, DIM, false, true);
            return;
        }
        // МЕНЮ и ДЖАНК-ТЕКСТ
        drawWrapped(c, logic.line, 30, 360, 14);
        if (ph == BattleLogic.Phase.JUNK_TALK) {
            text(c, Phrases.TAP_CONTINUE, 180, 442, 11, DIM, false, true);
        }
    }

    private void drawWrapped(Canvas c, String str, float x, float y, float size) {
        List<String> lines = wrap(str, 300, size);
        for (int i = 0; i < lines.size() && i < 5; i++) {
            text(c, lines.get(i), x, y + i * (size + 5), size, TEXT, false, false);
        }
    }

    /** Сигнал Морзе из точек и тире (точка — круг, тире — прямоугольник). */
    private void drawSignal(Canvas c, String word, float x, float y) {
        String code = MorseCode.encode(word);
        float cx = x;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '.') {
                circle(c, cx + 4, y + 4, 4, YELLOW);
                cx += 14;
            } else if (ch == '-') {
                rect(c, cx, y, cx + 16, y + 8, YELLOW);
                cx += 22;
            } else {
                cx += 10;
            }
        }
    }

    private void drawPlayer(Canvas c, float y) {
        int hp = logic.playerHp;
        text(c, Phrases.YOU_LABEL + "  HP " + hp + "/" + BattleLogic.PLAYER_MAX, 20, y, 12, TEXT, true, false);
        text(c, "батарейки: " + logic.batteries, 230, y, 11, DIM, false, false);
        rect(c, 20, y + 4, 340, y + 12, 0xFF2B2B2B);
        rect(c, 20, y + 4, 20 + 320 * hp / (float) BattleLogic.PLAYER_MAX, y + 12, RED);
    }

    private void drawButtons(Canvas c) {
        if (logic.phase == BattleLogic.Phase.FIGHT || logic.isOver() || logic.phase == BattleLogic.Phase.JUNK_TALK) {
            return;
        }
        for (Btn b : buttons()) {
            int fill = b.enabled ? PURPLE_DARK : 0xFF1A1A1A;
            frame(c, b.l, b.t, b.r, b.b, b.enabled ? BORDER : GREY, fill, 2);
            if (b.hint != null) {
                text(c, b.label, b.l + 70, b.t + 16, 14, TEXT, true, true);
                text(c, b.hint, b.l + 70, b.t + 31, 10, YELLOW, false, true);
            } else {
                text(c, b.label, b.l + 70, b.t + 24, 13, b.enabled ? TEXT : DIM, true, true);
            }
        }
    }

    private void drawArena(Canvas c) {
        frame(c, ARENA_L, ARENA_T, ARENA_R, ARENA_B, BORDER, PANEL, 2);
        float sc = 1f;
        float ax = ARENA_L + 0f, ay = ARENA_T + 0f;
        // содержимое арены — в координатах ArenaSim (W×H) внутри рамки
        for (ArenaSim.Item it : sim.items) {
            float px = ax + it.x * sc, py = ay + it.y * sc;
            switch (it.kind) {
                case CAP:
                    circle(c, px, py, it.r + 2, 0xFF000000);
                    circle(c, px, py, it.r, YELLOW);
                    break;
                case BATTERY:
                    rect(c, px - 3, py - 7, px + 3, py + 7, 0xFF7CFC00);
                    break;
                case BOARD:
                    drawBoard(c, px, py, it.r, it.rot);
                    break;
                case DOT:
                    circle(c, px, py, 3, TEXT);
                    break;
                case DASH:
                    rect(c, px - 6, py - 2, px + 6, py + 2, TEXT);
                    break;
                default:
                    break;
            }
        }
        float hx = ax + sim.heartX, hy = ay + sim.heartY;
        rect(c, hx - 6, hy - 6, hx + 6, hy + 6, RED);
        // джойстик
        circle(c, JOY_X, JOY_Y, JOY_R + 3, 0xFF555555);
        circle(c, JOY_X, JOY_Y, JOY_R, 0xFF161616);
        float kx = JOY_X + sim.stickX * (JOY_R - 10), ky = JOY_Y + sim.stickY * (JOY_R - 10);
        circle(c, kx, ky, 10, 0xFF8A8A8A);
        circle(c, kx, ky, 7, 0xFF2B2B2B);
        text(c, Phrases.JOY_HINT, 180, 658, 10, DIM, false, true);

    }

    /** Плата: квадрат, повёрнутый на rot (Path, как в BulletField). */
    private void drawBoard(Canvas c, float cx, float cy, float r, float rot) {
        Path p = new Path();
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int i = 0; i < 4; i++) {
            float x = corners[i][0] * cs - corners[i][1] * sn + cx;
            float y = corners[i][0] * sn + corners[i][1] * cs + cy;
            if (i == 0) p.moveTo(X(x), Y(y)); else p.lineTo(X(x), Y(y));
        }
        p.close();
        paint.setColor(0xFF3FA7D6);
        c.drawPath(p, paint);
    }

    // ---------- вход и такт ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        updateScale();
        float lx = (e.getX() - ox) / s, ly = (e.getY() - oy) / s;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onDown(lx, ly);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (joyActive && sim != null) setStickFrom(lx, ly);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                joyActive = false;
                if (sim != null) sim.setStick(0f, 0f);
                return true;
            default:
                return false;
        }
    }

    private void onDown(float lx, float ly) {
        if (logic.isOver()) {
            restart();
            return;
        }
        switch (logic.phase) {
            case FIGHT:
                stopFight();
                return;
            case JUNK_TALK:
                continueTalk();
                return;
            case ARENA:
                if (sim != null && lx >= JOY_X - JOY_R - 20 && lx <= JOY_X + JOY_R + 20 && ly >= JOY_Y - JOY_R - 20) {
                    joyActive = true;
                    setStickFrom(lx, ly);
                }
                return;
            default:
                break;
        }
        for (Btn b : buttons()) {
            if (b.hit(lx, ly)) {
                handle(b.id);
                return;
            }
        }
    }

    private void setStickFrom(float lx, float ly) {
        float dx = (lx - JOY_X) / (JOY_R - 6), dy = (ly - JOY_Y) / (JOY_R - 6);
        sim.setStick(clamp(dx), clamp(dy));
    }

    private static float clamp(float v) {
        return v < -1 ? -1 : (v > 1 ? 1 : v);
    }

    private void handle(String id) {
        switch (id) {
            case "FIGHT":
                logic.chooseFight();
                fightT = 0f;
                sub = SUB_NONE;
                break;
            case "ACT":
                sub = SUB_ACT;
                break;
            case "ITEM":
                sub = SUB_ITEM;
                break;
            case "BACK":
                sub = SUB_NONE;
                break;
            case "MERCY":
                logic.mercy();
                break;
            case "LOOK":
                logic.chooseAct(BattleLogic.Act.LOOK);
                sub = SUB_NONE;
                break;
            case "MORSE":
                logic.chooseAct(BattleLogic.Act.MORSE);
                sub = SUB_NONE;
                break;
            case "HAGGLE":
                logic.chooseAct(BattleLogic.Act.HAGGLE);
                sub = SUB_NONE;
                break;
            case "BATTERY":
                logic.useBattery();
                sub = SUB_NONE;
                break;
            case "OPT0":
            case "OPT1":
            case "OPT2":
                logic.answerMorse(id.charAt(3) - '0');
                break;
            default:
                break;
        }
    }

    private void stopFight() {
        double acc = 1.0 - Math.abs(fightMarker - 0.5) * 2.0;
        lastAccuracyPct = (int) Math.round(acc * 100);
        int dmg = logic.fight(acc);
        if (dmg > 0) shake = 0.4f;
    }

    private void continueTalk() {
        BattleLogic.Pattern p = logic.startArena();
        if (p == null) return;                // победа: экран конца
        sim = new ArenaSim(rng, p, logic.morseWord);
    }

    private void restart() {
        logic = newLogic();
        sub = SUB_NONE;
        sim = null;
        joyActive = false;
    }

    /** Шаг времени (вызывается из MainActivity ~60 раз в секунду, в тестах — вручную). */
    void tick(float dt) {
        if (logic.phase == BattleLogic.Phase.FIGHT) {
            fightT += dt;
            float p = (fightT * 0.9f) % 2f;
            fightMarker = p <= 1f ? p : 2f - p;
        }
        if (shake > 0) shake = Math.max(0, shake - dt);
        if (flash > 0) flash = Math.max(0, flash - dt);
        if (logic.phase == BattleLogic.Phase.ARENA && sim != null) {
            int newHits = sim.update(dt);
            if (newHits > 0) flash = 0.15f;
            if (sim.finished()) {
                logic.endArena(sim.hits);
                sim = null;
                joyActive = false;
            }
        }
    }

    // ---------- для тестов ----------

    String subForTest() { return sub; }

    int lastAccuracyPctForTest() { return lastAccuracyPct; }

    List<String> buttonIdsForTest() {
        List<String> ids = new ArrayList<>();
        for (Btn b : buttons()) ids.add(b.id);
        return ids;
    }

    /** Центр кнопки в логических координатах (для тестов касаний). */
    float[] buttonCenterForTest(String id) {
        for (Btn b : buttons()) {
            if (b.id.equals(id)) return new float[] {(b.l + b.r) / 2f, (b.t + b.b) / 2f};
        }
        throw new IllegalArgumentException("нет кнопки: " + id);
    }

    /** Касание в логических координатах, как если бы пользователь тапнул. */
    void tapLogicalForTest(float lx, float ly) {
        onDown(lx, ly);
    }

    void setFightMarkerForTest(float m) { fightMarker = m; }

    void setStickForTest(float dx, float dy) {
        if (sim != null) sim.setStick(dx, dy);
    }
}
