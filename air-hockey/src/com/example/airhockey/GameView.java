package com.example.airhockey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Экран игры. Вся графика рисуется в логической системе 1080×1930:
 * шапка 0..130, стол 40..1040 × 130..1930 (координаты стола 0..1000 × 0..1800).
 */
public final class GameView extends View implements Table.Listener {
    static final float LW = 1080f, LH = 1930f, TX = 40f, TY = 130f;

    enum Screen { MENU, DIFF, PLAYING, PAUSED, OVER }
    enum Mode { AI, TWO, BOTS }

    private static final String[] LEVEL_NAMES = {"Лёгкий", "Средний", "Сложный", "Мастер"};
    private static final int C_RED = 0xFFFF4D6D, C_CYAN = 0xFF3DD6FF, C_GOLD = 0xFFFFC857,
            C_TEXT = 0xFFEAF2FF, C_DIM = 0xFF7F93B3, C_BTN = 0xFF1B3A66, C_BTN_SEL = 0xFF2F6FD0;

    private final Settings settings;
    private final Audio audio;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final List<Btn> buttons = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final java.util.Map<Integer, Integer> pointerSide = new java.util.HashMap<>();

    private Screen screen = Screen.MENU;
    private Mode mode = Mode.AI;
    private int levelTop = 1, levelBottom = 1;     // уровни ботов: верхний и нижний
    private Table table;
    private boolean recorded;
    private String newAchievements = "";
    private float goalFlash, shake;
    private long lastNs;
    private float scale = 1f, offX, offY;
    private Shader bgShader;
    private boolean running;
    private boolean prevPuckStill = true;

    public GameView(Context ctx) {
        super(ctx);
        settings = new Settings(ctx);
        audio = new Audio(ctx, settings);
        setBackgroundColor(0xFF02060F);
        buildButtons();
    }

    // ---------- жизненный цикл ----------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastNs = System.nanoTime();
        postOnAnimation(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    /** Освобождает звук (вызывается из Activity.onDestroy). */
    void release() {
        audio.release();
    }

    /** Вызывается при сворачивании приложения: ставим на паузу. */
    void onAppPaused() {
        if (screen == Screen.PLAYING) { screen = Screen.PAUSED; buildButtons(); }
    }

    /** Кнопка «назад»: возвращает true, если событие обработано внутри экрана. */
    boolean onBack() {
        switch (screen) {
            case PLAYING: screen = Screen.PAUSED; buildButtons(); return true;
            case PAUSED: case OVER: goMenu(); return true;
            case DIFF: screen = Screen.MENU; buildButtons(); return true;
            default: return false;
        }
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastNs) / 1e9f);
            lastNs = now;
            step(dt);
            invalidate();
            postOnAnimation(this);
        }
    };

    private void step(float dt) {
        goalFlash = Math.max(0, goalFlash - dt);
        shake = Math.max(0, shake - dt * 2);
        for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            s.life -= dt;
            if (s.life <= 0) { it.remove(); continue; }
            s.x += s.vx * dt; s.y += s.vy * dt;
            s.vx *= 0.92f; s.vy *= 0.92f;
        }
        if (screen != Screen.PLAYING || table == null) return;
        table.update(dt);
        float speed = (float) Math.hypot(table.puckVX, table.puckVY);
        // подача: шайба начала двигаться с места
        if (prevPuckStill && speed > 200f) audio.play(Audio.SERVE);
        prevPuckStill = speed < 1f;
        if (table.isOver()) finishMatch();
    }

    // ---------- игра ----------

    private void startMatch() {
        table = new Table(this);
        table.reset();
        switch (mode) {
            case AI:
                table.setBot(1, Table.LEVELS[levelTop]);
                break;
            case TWO:
                break;
            case BOTS:
                table.setBot(0, Table.LEVELS[levelBottom]);
                table.setBot(1, Table.LEVELS[levelTop]);
                break;
        }
        sparks.clear();
        recorded = false;
        newAchievements = "";
        prevPuckStill = true;
        pointerSide.clear();
        screen = Screen.PLAYING;
        buildButtons();
    }

    private void finishMatch() {
        screen = Screen.OVER;
        if (!recorded) {
            recorded = true;
            newAchievements = settings.recordMatch(0, table.score, levelBottom, levelTop, mode == Mode.AI);
        }
        boolean playerWon = mode == Mode.AI && table.winner() == 0;
        audio.play(mode == Mode.BOTS || mode == Mode.TWO || playerWon ? Audio.WIN : Audio.LOSE);
        buildButtons();
    }

    private void goMenu() {
        table = null;
        screen = Screen.MENU;
        buildButtons();
    }

    @Override
    public void onPaddleHit(float speed, int side) {
        audio.play(Audio.HIT);
        spawnSparks(table.puckX, table.puckY, speed, side == 0 ? C_RED : C_CYAN, 14);
    }

    @Override
    public void onWallHit(float speed) {
        audio.play(Audio.WALL);
        spawnSparks(table.puckX, table.puckY, speed * 0.4f, C_TEXT, 5);
    }

    @Override
    public void onGoal(int scorer) {
        audio.play(Audio.GOAL);
        goalFlash = 1.0f;
        shake = 1f;
        spawnSparks(table.puckX, table.puckY, 900, C_GOLD, 40);
    }

    private void spawnSparks(float x, float y, float speed, int color, int n) {
        int count = Math.min(n, 40);
        for (int i = 0; i < count; i++) {
            Spark s = new Spark();
            double a = Math.random() * Math.PI * 2;
            float v = (float) (80 + Math.random() * Math.min(900, speed));
            s.x = x; s.y = y;
            s.vx = (float) Math.cos(a) * v; s.vy = (float) Math.sin(a) * v;
            s.life = s.max = 0.25f + (float) Math.random() * 0.35f;
            s.color = color;
            sparks.add(s);
        }
    }

    // ---------- кнопки ----------

    private static final class Btn {
        final RectF r = new RectF();
        final String label;
        final String action;
        final boolean selected;
        Btn(String label, String action, boolean selected) { this.label = label; this.action = action; this.selected = selected; }
    }

    private Btn add(float x, float y, float w, float h, String label, String action, boolean selected) {
        Btn b = new Btn(label, action, selected);
        b.r.set(x, y, x + w, y + h);
        buttons.add(b);
        return b;
    }

    private void buildButtons() {
        buttons.clear();
        float bw = 760, bh = 120, cx = (LW - bw) / 2;
        switch (screen) {
            case MENU:
                add(cx, 520, bw, bh, "Против ИИ", "mode_ai", false);
                add(cx, 680, bw, bh, "2 игрока", "mode_two", false);
                add(cx, 840, bw, bh, "Бот против бота", "mode_bots", false);
                add(cx, 1400, bw, 100, "Звук: " + (settings.sound() ? "вкл" : "выкл"), "sound", false);
                break;
            case DIFF: {
                boolean both = mode == Mode.BOTS;
                float y = 520;
                for (int row = 0; row < (both ? 2 : 1); row++) {
                    int sel = (row == 0 && both) ? levelBottom : levelTop;
                    for (int i = 0; i < 4; i++) {
                        String act = (row == 0 && both ? "lo_" : "hi_") + i;
                        float x = 40 + i * 255;
                        add(x, y, 235, 120, LEVEL_NAMES[i], act, sel == i);
                    }
                    y += 170;
                }
                add(cx, 1100, bw, bh, "Старт", "start", false);
                add(cx, 1260, bw, 100, "Назад", "back", false);
                break;
            }
            case PLAYING:
                add(40, 40, 150, 70, "Пауза", "pause", false);
                break;
            case PAUSED:
                add(cx, 700, bw, bh, "Продолжить", "resume", false);
                add(cx, 860, bw, bh, "Рестарт", "restart", false);
                add(cx, 1020, bw, bh, "В меню", "menu", false);
                add(cx, 1180, bw, 100, "Звук: " + (settings.sound() ? "вкл" : "выкл"), "sound", false);
                break;
            case OVER:
                add(cx, 1000, bw, bh, "Реванш", "restart", false);
                add(cx, 1160, bw, bh, "В меню", "menu", false);
                break;
        }
    }

    private Btn hit(float lx, float ly) {
        for (Btn b : buttons) if (b.r.contains(lx, ly)) return b;
        return null;
    }

    private void action(String a) {
        if (a == null) return;
        audio.play(Audio.CLICK);
        if (a.equals("mode_ai")) { mode = Mode.AI; screen = Screen.DIFF; buildButtons(); }
        else if (a.equals("mode_two")) { mode = Mode.TWO; startMatch(); }
        else if (a.equals("mode_bots")) { mode = Mode.BOTS; screen = Screen.DIFF; buildButtons(); }
        else if (a.startsWith("lo_")) { levelBottom = a.charAt(3) - '0'; buildButtons(); }
        else if (a.startsWith("hi_")) { levelTop = a.charAt(3) - '0'; buildButtons(); }
        else if (a.equals("start")) startMatch();
        else if (a.equals("back")) { screen = Screen.MENU; buildButtons(); }
        else if (a.equals("pause")) { screen = Screen.PAUSED; buildButtons(); }
        else if (a.equals("resume")) { screen = Screen.PLAYING; lastNs = System.nanoTime(); buildButtons(); }
        else if (a.equals("restart")) startMatch();
        else if (a.equals("menu")) goMenu();
        else if (a.equals("sound")) { settings.setSound(!settings.sound()); buildButtons(); }
    }

    // ---------- касания ----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex();
            float lx = toLx(e.getX(i)), ly = toLy(e.getY(i));
            if (screen != Screen.PLAYING) {
                Btn b = hit(lx, ly);
                if (b != null) action(b.action);
                return true;
            }
            Btn b = hit(lx, ly);
            if (b != null) { action(b.action); return true; }
            // управление: в режиме 2 игроков половина стола определяет игрока, против ИИ — всегда нижний
            if (mode == Mode.BOTS) return true;
            int side = mode == Mode.TWO ? (ly - TY < 900 ? 1 : 0) : 0;
            pointerSide.put(e.getPointerId(i), side);
            moveTarget(e.getPointerId(i), lx, ly);
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                moveTarget(e.getPointerId(i), toLx(e.getX(i)), toLy(e.getY(i)));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            pointerSide.remove(e.getPointerId(e.getActionIndex()));
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) pointerSide.clear();
        }
        return true;
    }

    private void moveTarget(int pointerId, float lx, float ly) {
        Integer side = pointerSide.get(pointerId);
        if (side == null || table == null || screen != Screen.PLAYING) return;
        table.setTarget(side, lx - TX, ly - TY);
    }

    private float toLx(float x) { return (x - offX) / scale; }
    private float toLy(float y) { return (y - offY) / scale; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scale = Math.min(w / LW, h / LH);
        offX = (w - LW * scale) / 2f;
        offY = (h - LH * scale) / 2f;
        bgShader = new LinearGradient(0, 0, 0, LH, 0xFF0B1E3A, 0xFF02060F, Shader.TileMode.CLAMP);
    }

    // ---------- отрисовка ----------

    @Override
    protected void onDraw(Canvas c) {
        c.save();
        c.translate(offX, offY);
        c.scale(scale, scale);
        paint.setShader(bgShader);
        c.drawRect(0, 0, LW, LH, paint);
        paint.setShader(null);

        if (screen == Screen.MENU) drawMenu(c);
        else if (screen == Screen.DIFF) drawDiff(c);
        else {
            drawTable(c);
            drawHud(c);
            if (screen == Screen.PAUSED) drawOverlay(c, "Пауза");
            if (screen == Screen.OVER) drawOver(c);
        }
        drawButtons(c);
        c.restore();
    }

    private void drawMenu(Canvas c) {
        text(c, "AIR HOCKEY", LW / 2, 230, 110, C_TEXT, true, Paint.Align.CENTER);
        text(c, "Challenge", LW / 2, 310, 56, C_CYAN, false, Paint.Align.CENTER);
        text(c, "Матчей: " + settings.matches() + "   Побед: " + settings.wins(), LW / 2, 1290, 48, C_DIM, false, Paint.Align.CENTER);
        text(c, "Достижения", LW / 2, 960, 46, C_TEXT, true, Paint.Align.CENTER);
        drawAch(c, 1000, "first_win", "Первая победа");
        drawAch(c, 1060, "master", "Победа над Мастером");
        drawAch(c, 1120, "shutout", "Сухая победа 7:0");
    }

    private void drawAch(Canvas c, float y, String id, String name) {
        boolean ok = settings.has(id);
        text(c, (ok ? "[x] " : "[ ] ") + name, LW / 2, y, 44, ok ? C_GOLD : C_DIM, false, Paint.Align.CENTER);
    }

    private void drawDiff(Canvas c) {
        String title = mode == Mode.AI ? "Соперник — ИИ" : mode == Mode.BOTS ? "Бот против бота" : "";
        text(c, title, LW / 2, 300, 62, C_TEXT, true, Paint.Align.CENTER);
        if (mode == Mode.BOTS) {
            text(c, "Нижний бот (красный)", LW / 2, 480, 40, C_RED, false, Paint.Align.CENTER);
            text(c, "Верхний бот (голубой)", LW / 2, 650, 40, C_CYAN, false, Paint.Align.CENTER);
        } else {
            text(c, "Уровень соперника", LW / 2, 480, 40, C_CYAN, false, Paint.Align.CENTER);
        }
        text(c, "Лёгкий — медленный, ошибается  ·  Мастер — почти безошибочный", LW / 2, 900, 30, C_DIM, false, Paint.Align.CENTER);
    }

    private void drawTable(Canvas c) {
        c.save();
        c.translate(TX, TY);
        float W = Table.W, H = Table.H;
        // рама и поле
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0F3C6E);
        tmp.set(-22 + shakeX(), -22 + shakeY(), W + 22 + shakeX(), H + 22 + shakeY());
        c.drawRoundRect(tmp, 36, 36, paint);
        paint.setColor(0xFF134B86);
        tmp.set(0 + shakeX(), 0 + shakeY(), W + shakeX(), H + shakeY());
        c.drawRect(tmp, paint);
        // разметка
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setColor(0xCCFFFFFF);
        float gl = W / 2 - Table.GOAL_HALF, gr = W / 2 + Table.GOAL_HALF;
        c.drawLine(0 + shakeX(), 0 + shakeY(), gl + shakeX(), 0 + shakeY(), paint);
        c.drawLine(gr + shakeX(), 0 + shakeY(), W + shakeX(), 0 + shakeY(), paint);
        c.drawLine(0 + shakeX(), H + shakeY(), gl + shakeX(), H + shakeY(), paint);
        c.drawLine(gr + shakeX(), H + shakeY(), W + shakeX(), H + shakeY(), paint);
        c.drawLine(0 + shakeX(), 0 + shakeY(), 0 + shakeX(), H + shakeY(), paint);
        c.drawLine(W + shakeX(), 0 + shakeY(), W + shakeX(), H + shakeY(), paint);
        c.drawLine(0 + shakeX(), H / 2 + shakeY(), W + shakeX(), H / 2 + shakeY(), paint);
        c.drawCircle(W / 2 + shakeX(), H / 2 + shakeY(), 150, paint);
        // ворота
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(C_GOLD);
        c.drawRect(gl + shakeX(), -14 + shakeY(), gr + shakeX(), 0 + shakeY(), paint);
        c.drawRect(gl + shakeX(), H + shakeY(), gr + shakeX(), H + 14 + shakeY(), paint);
        paint.setColor(0x66FFC857);
        c.drawCircle(W / 2 + shakeX(), H / 2 + shakeY(), 12, paint);
        // частицы
        for (Spark s : sparks) {
            paint.setColor(s.color);
            paint.setAlpha((int) (255 * Math.max(0, s.life / s.max)));
            c.drawCircle(s.x + shakeX(), s.y + shakeY(), 6 + 8 * (s.life / s.max), paint);
        }
        paint.setAlpha(255);
        // ракетки
        for (int s = 0; s < 2; s++) {
            int col = s == 0 ? C_RED : C_CYAN;
            float x = table.px[s] + shakeX(), y = table.py[s] + shakeY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x55000000);
            c.drawCircle(x + 6, y + 10, Table.PADDLE_R, paint);
            paint.setColor(col);
            c.drawCircle(x, y, Table.PADDLE_R, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, Table.PADDLE_R - 6, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x44FFFFFF);
            c.drawCircle(x - 20, y - 22, 26, paint);
        }
        // шайба
        float px = table.puckX + shakeX(), py = table.puckY + shakeY();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        c.drawCircle(px + 6, py + 8, Table.PUCK_R, paint);
        paint.setColor(0xFF111111);
        c.drawCircle(px, py, Table.PUCK_R, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setColor(C_GOLD);
        c.drawCircle(px, py, Table.PUCK_R - 8, paint);
        // вспышка гола
        if (goalFlash > 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(C_GOLD);
            paint.setAlpha((int) (200 * goalFlash));
            paint.setTextSize(200);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("ГОЛ!", W / 2, H / 2 - 60, paint);
            paint.setAlpha(255);
        }
        c.restore();
    }

    private float shakeX() { return shake > 0 ? (float) (Math.sin(System.nanoTime() / 2e7) * 14 * shake) : 0; }
    private float shakeY() { return shake > 0 ? (float) (Math.cos(System.nanoTime() / 2.3e7) * 14 * shake) : 0; }

    private void drawHud(Canvas c) {
        String top = nameOf(1), bottom = nameOf(0);
        text(c, top, 300, 40, 34, C_CYAN, false, Paint.Align.CENTER);
        text(c, bottom, 800, 40, 34, C_RED, false, Paint.Align.CENTER);
        text(c, table.score[1] + "", 420, 112, 84, C_CYAN, true, Paint.Align.RIGHT);
        text(c, ":", 540, 112, 84, C_TEXT, true, Paint.Align.CENTER);
        text(c, table.score[0] + "", 660, 112, 84, C_RED, true, Paint.Align.LEFT);
        text(c, "до " + Table.TARGET, 540, 40, 30, C_DIM, false, Paint.Align.CENTER);
    }

    private String nameOf(int side) {
        switch (mode) {
            case AI: return side == 0 ? "Ты" : "ИИ · " + LEVEL_NAMES[levelTop];
            case TWO: return side == 0 ? "Игрок 1" : "Игрок 2";
            default: return "Бот " + (side == 0 ? LEVEL_NAMES[levelBottom] : LEVEL_NAMES[levelTop]);
        }
    }

    private void drawOverlay(Canvas c, String title) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC02060F);
        c.drawRect(0, 0, LW, LH, paint);
        text(c, title, LW / 2, 560, 110, C_TEXT, true, Paint.Align.CENTER);
    }

    private void drawOver(Canvas c) {
        drawOverlay(c, "");
        String head;
        if (mode == Mode.AI) head = table.winner() == 0 ? "Победа!" : "Поражение";
        else if (mode == Mode.TWO) head = table.winner() == 0 ? "Игрок 1 победил" : "Игрок 2 победил";
        else head = table.winner() == 0 ? "Нижний бот победил" : "Верхний бот победил";
        text(c, head, LW / 2, 520, 92, table.winner() == 0 ? C_RED : C_CYAN, true, Paint.Align.CENTER);
        text(c, table.score[1] + " : " + table.score[0], LW / 2, 640, 120, C_TEXT, true, Paint.Align.CENTER);
        if (newAchievements.length() > 0) {
            String[] lines = newAchievements.split("\n");
            float y = 780;
            text(c, "Новые достижения:", LW / 2, y, 46, C_GOLD, true, Paint.Align.CENTER);
            for (String l : lines) { y += 60; text(c, l, LW / 2, y, 44, C_GOLD, false, Paint.Align.CENTER); }
        }
    }

    private void drawButtons(Canvas c) {
        for (Btn b : buttons) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(b.selected ? C_BTN_SEL : C_BTN);
            c.drawRoundRect(b.r, 28, 28, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            paint.setColor(0x66FFFFFF);
            c.drawRoundRect(b.r, 28, 28, paint);
            float size = b.r.height() > 110 ? 50 : 40;
            if (b.label.length() > 14) size = 36;
            if (b.r.height() <= 110 && b.r.width() < 300) size = 34;
            text(c, b.label, b.r.centerX(), b.r.centerY() + size * 0.35f, size, C_TEXT, b.selected, Paint.Align.CENTER);
        }
    }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(align);
        c.drawText(s, x, y, paint);
    }

    private static final class Spark {
        float x, y, vx, vy, life, max;
        int color;
    }
}
