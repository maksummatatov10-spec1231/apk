package com.example.junkil;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Экран боя с Джанклимом, ЛАНДШАФТ. Логические координаты 854×480 (масштаб под экран).
 * Сверху — сцена с сеткой помойки и спрайт; внизу — белая рамка (диалог / арена) и оранжевые кнопки,
 * как в Undertale. Слева — джойстик, справа — Z / X / C.
 */
final class BattleView extends View {

    static final float LW = 854f;
    static final float LH = 480f;

    // Рамка боя (арена и диалог)
    static final float BOX_L = 187f, BOX_T = 214f, BOX_R = 667f, BOX_B = 398f;
    // Внутреннее поле арены (ArenaSim.W × ArenaSim.H)
    static final float AX = 195f, AY = 222f;

    static final float JOY_X = 100f, JOY_Y = 330f, JOY_R = 52f;
    static final float BTN_Y = 436f, BTN_W = 170f, BTN_H = 40f;
    static final float[] BTN_X = {47f, 237f, 427f, 617f};
    static final float ZX_R = 30f;
    static final float Z_X = 800f, Z_Y = 334f, X_X = 738f, X_Y = 268f, C_X = 800f, C_Y = 200f;

    static final int BG = 0xFF0C0A16;
    static final int BORDER = 0xFFFFFFFF;
    static final int ORANGE = 0xFFFF8A00;
    static final int ORANGE_DIM = 0xFF7A4A00;
    static final int GREEN_GRID = 0xFF2FA84F;
    static final int YELLOW = 0xFFFFE600;
    static final int RED = 0xFFFF2A2A;
    static final int GREY = 0xFF555555;
    static final int TEXT = 0xFFFFFFFF;
    static final int DIM = 0xFF9A9A9A;
    static final int HP_BG = 0xFF7A0000;

    private static final String SUB_NONE = "", SUB_ACT = "ACT", SUB_ITEM = "ITEM";

    private final Paint paint = new Paint();
    private final RectF dst = new RectF();
    private final Bitmap[] sprites;            // [NORMAL, ANGRY, SAD]; null — берётся NORMAL
    private final Bitmap[] textures;           // бесшовные текстуры атак
    private final BitmapShader[] shaders;
    private final Random rng;
    BattleLogic logic;
    ArenaSim sim;
    private String sub = SUB_NONE;
    private float s = 1f, ox, oy;
    private float fightT, fightMarker;
    private float shake, flash;
    private boolean joyActive;
    private int lastAccuracyPct = -1;

    BattleView(Context context, Bitmap[] sprites, Bitmap[] textures, long seed) {
        super(context);
        this.sprites = sprites;
        this.textures = textures;
        this.shaders = new BitmapShader[textures.length];
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != null) {
                shaders[i] = new BitmapShader(textures[i], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            }
        }
        this.rng = new Random(seed);
        this.logic = newLogic();
    }

    private BattleLogic newLogic() {
        BattleLogic l = new BattleLogic(rng);
        l.line = Phrases.INTRO;
        return l;
    }

    /** Кнопка: id — действие касания; l/t/r/b — логические координаты. */
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

    private static Btn slot(String id, String label, int slot, boolean enabled) {
        float l = BTN_X[slot];
        return new Btn(id, label, null, l, BTN_Y, l + BTN_W, BTN_Y + BTN_H, enabled);
    }

    private List<Btn> buttons() {
        List<Btn> bs = new ArrayList<>();
        if (logic.phase == BattleLogic.Phase.MENU) {
            if (SUB_ACT.equals(sub)) {
                bs.add(slot("LOOK", Phrases.ACT_LOOK, 0, true));
                bs.add(slot("MORSE", Phrases.ACT_MORSE, 1, true));
                bs.add(slot("HAGGLE", Phrases.ACT_HAGGLE, 2, true));
                bs.add(slot("BACK", Phrases.BACK, 3, true));
            } else if (SUB_ITEM.equals(sub)) {
                bs.add(slot("BATTERY", Phrases.ITEM_BATTERY + " ×" + logic.batteries, 0, logic.batteries > 0));
                bs.add(slot("BACK", Phrases.BACK, 3, true));
            } else {
                bs.add(slot("FIGHT", Phrases.BTN_FIGHT, 0, true));
                bs.add(slot("ACT", Phrases.BTN_ACT, 1, true));
                bs.add(slot("ITEM", Phrases.BTN_ITEM, 2, true));
                bs.add(slot("MERCY", Phrases.BTN_MERCY, 3, true));
            }
        } else if (logic.phase == BattleLogic.Phase.MORSE) {
            for (int i = 0; i < 3; i++) {
                float l = BTN_X[i];
                String code = MorseCode.encode(logic.morseOptions[i]);
                bs.add(new Btn("OPT" + i, logic.morseOptions[i], code, l, BTN_Y, l + BTN_W, BTN_Y + BTN_H, true));
            }
        }
        return bs;
    }

    // ---------- рисование: хелперы в логических координатах ----------

    private float X(float v) { return ox + v * s; }
    private float Y(float v) { return oy + v * s; }

    private void rect(Canvas c, float l, float t, float r, float b, int col) {
        if (r <= l || b <= t) return;
        paint.setShader(null);
        paint.setColor(col);
        c.drawRect(X(l), Y(t), X(r), Y(b), paint);
    }

    /** Рамка: белый контур толщиной w, внутри заливка fill. */
    private void frame(Canvas c, float l, float t, float r, float b, int border, int fill, float w) {
        rect(c, l - w, t - w, r + w, b + w, border);
        rect(c, l, t, r, b, fill);
    }

    private void text(Canvas c, String str, float x, float y, float size, int col, boolean bold, boolean center) {
        paint.setShader(null);
        paint.setColor(col);
        paint.setTextSize(size * s);
        paint.setFakeBoldText(bold);
        float xx = center ? X(x) - paint.measureText(str) / 2f : X(x);
        c.drawText(str, xx, Y(y), paint);
    }

    private void circle(Canvas c, float cx, float cy, float r, int col) {
        paint.setShader(null);
        paint.setColor(col);
        c.drawCircle(X(cx), Y(cy), r * s, paint);
    }

    /** Многоугольник в логических координатах, опционально с текстурой. */
    private void poly(Canvas c, float[] pts, int tex, int color) {
        Path p = new Path();
        for (int i = 0; i < pts.length; i += 2) {
            if (i == 0) p.moveTo(X(pts[0]), Y(pts[1]));
            else p.lineTo(X(pts[i]), Y(pts[i + 1]));
        }
        p.close();
        if (tex >= 0 && tex < shaders.length && shaders[tex] != null) {
            paint.setShader(shaders[tex]);
        } else {
            paint.setShader(null);
            paint.setColor(color);
        }
        c.drawPath(p, paint);
        paint.setShader(null);
    }

    /** Фигура из ArenaSim в позиции (cx, cy) с радиусом r и поворотом rot (логические координаты поля). */
    private void drawShape(Canvas c, float[] base, float cx, float cy, float r, float rot, int tex) {
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float[] pts = new float[base.length];
        for (int i = 0; i < base.length; i += 2) {
            float x = base[i] * r, y = base[i + 1] * r;
            pts[i] = cx + x * cs - y * sn;
            pts[i + 1] = cy + x * sn + y * cs;
        }
        poly(c, pts, tex, RED);
    }

    private static final float[] HEART_SHAPE = heartShape();

    private static float[] heartShape() {
        float[] out = new float[32];
        for (int i = 0; i < 16; i++) {
            double t = i * 2 * Math.PI / 16;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            out[2 * i] = (float) (x / 17.0);
            out[2 * i + 1] = (float) (-y / 17.0);
        }
        return out;
    }

    /** Перенос текста по ширине (логические единицы). */
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

    private void drawWrapped(Canvas c, String str, float x, float y, float size) {
        List<String> lines = wrap(str, 430, size);
        for (int i = 0; i < lines.size() && i < 4; i++) {
            text(c, lines.get(i), x, y + i * (size + 6), size, TEXT, false, false);
        }
    }

    // ---------- кадр ----------

    @Override
    protected void onDraw(Canvas c) {
        updateScale();
        rect(c, -10, -10, LW + 10, LH + 10, 0xFF000000);
        rect(c, 0, 0, LW, LH, BG);
        drawStage(c);
        drawSprite(c);
        drawTop(c);
        boolean arena = logic.phase == BattleLogic.Phase.ARENA && sim != null;
        if (arena) {
            drawArena(c);
        } else {
            drawDialog(c);
        }
        drawPlayer(c);
        if (!arena) drawButtons(c);
        drawControls(c, arena);
        if (flash > 0) {
            rect(c, 0, 0, LW, 4, RED);
            rect(c, 0, LH - 4, LW, LH, RED);
        }
    }

    private void updateScale() {
        float vw = getWidth() > 0 ? getWidth() : LW;
        float vh = getHeight() > 0 ? getHeight() : LH;
        s = Math.min(vw / LW, vh / LH);
        ox = (vw - LW * s) / 2f;
        oy = (vh - LH * s) / 2f;
    }

    /** Сцена: тёмная помойка с зелёной сеткой (как стена из референса Undertale). */
    private void drawStage(Canvas c) {
        rect(c, 0, 0, LW, 200, 0xFF0A0812);
        for (float x : new float[] {14f, 120f, 170f, 240f, 600f, 690f, 760f, 840f}) {
            rect(c, x, 0, x + 2, 200, GREEN_GRID);
        }
        for (float y : new float[] {60f, 136f}) {
            rect(c, 0, y, LW, y + 2, GREEN_GRID);
        }
        rect(c, 14, 0, 842, 2, GREEN_GRID);
        rect(c, 14, 198, 842, 200, GREEN_GRID);
        rect(c, 350, 40, 504, 200, 0xFF0A0812);   // подложка под спрайт
    }

    private void drawSprite(Canvas c) {
        float dx = shake > 0 ? (float) Math.sin(shake * 60f) * 4f : 0f;
        Bitmap bm = sprites[logic.mood().ordinal()];
        if (bm == null) bm = sprites[0];
        if (bm == null) return;
        dst.set(X(364 + dx), Y(6), X(489 + dx), Y(196));
        c.drawBitmap(bm, new Rect(0, 0, bm.getWidth(), bm.getHeight()), dst, paint);
    }

    private void drawTop(Canvas c) {
        text(c, Phrases.NAME, 20, 32, 20, TEXT, true, false);
        rect(c, 20, 40, 240, 52, HP_BG);
        rect(c, 20, 40, 20 + 220 * logic.junkHp / (float) BattleLogic.JUNK_MAX, 52, YELLOW);
        text(c, logic.junkHp + "/" + BattleLogic.JUNK_MAX, 250, 52, 13, DIM, false, false);

        text(c, Phrases.SATISFY_LABEL + " " + logic.satisfy + "/" + BattleLogic.SATISFY_MAX, 560, 32, 14, DIM, false, false);
        rect(c, 560, 40, 830, 52, 0xFF2B2B2B);
        rect(c, 560, 40, 560 + 270 * logic.satisfy / (float) BattleLogic.SATISFY_MAX, 52, ORANGE);
        boolean arena = logic.phase == BattleLogic.Phase.ARENA && sim != null;
        String turnLabel = arena
                ? "ХОД " + logic.turn + " · " + Math.max(0, (int) Math.ceil(ArenaSim.DURATION - sim.time)) + " с"
                : "ХОД " + logic.turn;
        text(c, turnLabel, 560, 74, 13, DIM, false, false);
    }

    private void drawDialog(Canvas c) {
        BattleLogic.Phase ph = logic.phase;
        frame(c, BOX_L, BOX_T, BOX_R, BOX_B, BORDER, 0xFF000000, 4);
        if (ph == BattleLogic.Phase.FIGHT) {
            text(c, Phrases.FIGHT_LABEL, 207, 246, 18, YELLOW, true, false);
            rect(c, 207, 300, 647, 326, 0xFF2B2B2B);
            rect(c, 207 + 440 * 0.4f, 300, 207 + 440 * 0.6f, 326, 0xFF2E7D32);
            float mx = 207 + 440 * fightMarker;
            rect(c, mx - 2, 292, mx + 2, 334, TEXT);
            text(c, "нажми Z или тапни, когда маркер в зелёной зоне", 207, 364, 13, DIM, false, false);
            return;
        }
        if (ph == BattleLogic.Phase.MORSE) {
            text(c, Phrases.MORSE_TITLE, 207, 246, 18, YELLOW, true, false);
            drawSignal(c, logic.morseWord, 207, 272);
            text(c, "выбери слово, которое передано", 207, 330, 14, DIM, false, false);
            return;
        }
        if (ph == BattleLogic.Phase.WON || ph == BattleLogic.Phase.SPARED || ph == BattleLogic.Phase.LOST) {
            String head = ph == BattleLogic.Phase.WON ? "ПОБЕДА"
                    : ph == BattleLogic.Phase.SPARED ? "МИЛОСТЬ: СВОБОДНЫЙ ТОРГ" : "ПОРАЖЕНИЕ";
            text(c, head, 207, 246, 20, YELLOW, true, false);
            drawWrapped(c, logic.line, 207, 280, 17);
            text(c, Phrases.TAP_RESTART, 427, 384, 13, DIM, false, true);
            return;
        }
        drawWrapped(c, logic.line, 207, 246, 18);
        if (ph == BattleLogic.Phase.JUNK_TALK) {
            text(c, Phrases.TAP_CONTINUE, 427, 384, 13, DIM, false, true);
        }
    }

    /** Сигнал Морзе: точка — круг, тире — прямоугольник. */
    private void drawSignal(Canvas c, String word, float x, float y) {
        String code = MorseCode.encode(word);
        float cx = x;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '.') {
                circle(c, cx + 5, y + 5, 5, YELLOW);
                cx += 16;
            } else if (ch == '-') {
                rect(c, cx, y, cx + 20, y + 10, YELLOW);
                cx += 26;
            } else {
                cx += 12;
            }
        }
    }

    private void drawArena(Canvas c) {
        frame(c, BOX_L, BOX_T, BOX_R, BOX_B, BORDER, 0xFF000000, 4);
        for (ArenaSim.Item it : sim.items) {
            float px = AX + it.x, py = AY + it.y;
            switch (it.kind) {
                case DOT:
                    circle(c, px, py, 3, TEXT);
                    break;
                case DASH:
                    rect(c, px - 6, py - 2, px + 6, py + 2, TEXT);
                    break;
                default:
                    drawShape(c, ArenaSim.polygon(it.kind), px, py, it.r, it.rot, it.tex);
                    break;
            }
        }
        drawShape(c, HEART_SHAPE, AX + sim.heartX, AY + sim.heartY, 13f, 0f, -1);   // сердце
    }

    private void drawPlayer(Canvas c) {
        int hp = logic.playerHp;
        text(c, Phrases.YOU_LABEL + "  LV 1", 47, 428, 16, TEXT, true, false);
        text(c, "HP", 230, 428, 14, TEXT, true, false);
        rect(c, 262, 414, 262 + 20 * 8, 430, RED);
        rect(c, 262, 414, 262 + 8 * hp, 430, YELLOW);
        text(c, hp + " / " + BattleLogic.PLAYER_MAX, 280 + 20 * 8 - 10, 428, 14, TEXT, true, false);
        text(c, "батарейки ×" + logic.batteries, 560, 428, 14, DIM, false, false);
    }

    private void drawButtons(Canvas c) {
        if (logic.phase == BattleLogic.Phase.FIGHT || logic.isOver() || logic.phase == BattleLogic.Phase.JUNK_TALK) {
            return;
        }
        for (Btn b : buttons()) {
            int fill = 0xFF000000;
            int border = b.enabled ? ORANGE : GREY;
            int textCol = b.enabled ? ORANGE : ORANGE_DIM;
            frame(c, b.l, b.t, b.r, b.b, border, fill, 2);
            if (b.hint != null) {
                text(c, b.label, (b.l + b.r) / 2f, b.t + 18, 15, textCol, true, true);
                text(c, b.hint, (b.l + b.r) / 2f, b.t + 34, 11, DIM, false, true);
            } else {
                text(c, b.label, (b.l + b.r) / 2f, b.t + 26, 16, textCol, true, true);
            }
        }
    }

    /** Джойстик (слева) и кнопки Z / X / C (справа) — как на экранном управлении. */
    private void drawControls(Canvas c, boolean arena) {
        circle(c, JOY_X, JOY_Y, JOY_R + 3, 0xFF555555);
        circle(c, JOY_X, JOY_Y, JOY_R, 0xFF161616);
        float kx = JOY_X + (arena ? sim.stickX : 0f) * (JOY_R - 12);
        float ky = JOY_Y + (arena ? sim.stickY : 0f) * (JOY_R - 12);
        circle(c, kx, ky, 12, 0xFF8A8A8A);
        circle(c, kx, ky, 8, 0xFF2B2B2B);
        drawKey(c, C_X, C_Y, "C");
        drawKey(c, X_X, X_Y, "X");
        drawKey(c, Z_X, Z_Y, "Z");
    }

    private void drawKey(Canvas c, float x, float y, String label) {
        circle(c, x, y, ZX_R + 2, 0xFF666666);
        circle(c, x, y, ZX_R, 0xFF1A1A1A);
        text(c, label, x, y + 7, 20, 0xFFAAAAAA, true, true);
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

    private static boolean near(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private void onDown(float lx, float ly) {
        if (logic.isOver()) {
            restart();
            return;
        }
        if (near(lx, ly, Z_X, Z_Y, ZX_R + 6)) { pressZ(); return; }
        if (near(lx, ly, X_X, X_Y, ZX_R + 6)) { pressX(); return; }
        if (near(lx, ly, C_X, C_Y, ZX_R + 6)) { pressC(); return; }
        if (logic.phase == BattleLogic.Phase.ARENA && sim != null && near(lx, ly, JOY_X, JOY_Y, JOY_R + 30)) {
            joyActive = true;
            setStickFrom(lx, ly);
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

    /** Z: подтвердить — остановить полоску, продолжить текст, начать заново. */
    private void pressZ() {
        switch (logic.phase) {
            case FIGHT: stopFight(); break;
            case JUNK_TALK: continueTalk(); break;
            default: break;
        }
    }

    /** X: назад из подменю. */
    private void pressX() {
        sub = SUB_NONE;
    }

    /** C: показать состояние в диалоге (без изменения боя). */
    private void pressC() {
        if (logic.phase != BattleLogic.Phase.MENU) return;
        logic.line = "ТЫ: HP " + logic.playerHp + "/" + BattleLogic.PLAYER_MAX
                + ", батареек " + logic.batteries + ". ДЖАНКИЛ: " + logic.junkHp + "/" + BattleLogic.JUNK_MAX
                + ", ДОВОЛЬСТВО " + logic.satisfy + "/" + BattleLogic.SATISFY_MAX + ".";
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
