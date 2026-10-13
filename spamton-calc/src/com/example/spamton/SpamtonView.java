package com.example.spamton;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Весь экран приложения: калькулятор Спамтона, реклама, захват телефона и бой.
 * Рисуется на Canvas (пиксельный стиль Deltarune), логика — в Calc, Spamton, Battle, BulletField.
 */
public final class SpamtonView extends View {

    /* ---------- палитра ---------- */
    /** Сколько успешных вычислений до захвата телефона и как часто реклама до него. */
    static final int TAKEOVER_AFTER = 5;
    static final int AD_EVERY = 2;

    static final int BLACK = 0xFF000000;
    static final int INK = 0xFF0A0614;
    static final int WHITE = 0xFFFFFFFF;
    static final int YELLOW = 0xFFFFF200;
    static final int PINK = 0xFFFF4FD8;
    static final int CYAN = 0xFF5CE1FF;
    static final int RED = 0xFFFF2A3D;
    static final int GREEN = 0xFF7CFF6B;
    static final int GREY = 0xFF8A8AA8;
    static final int BG_TOP = 0xFF1A0C33;
    static final int BG_STRIPE = 0xFF241243;

    /** Интерфейс звуков и хранилища создаются снаружи: в приложении и в скриншот-тесте. */
    private final Sounds sounds;
    private final Store store;
    private final Random rnd;
    private final Paint paint = new Paint();
    /** Портреты по эмоциям (PNG из ресурсов). Нет картинки — NEUTRAL; нет NEUTRAL — процедурный спрайт. */
    private final java.util.Map<Mood, Bitmap> portraits = new java.util.EnumMap<>(Mood.class);
    private Mood mood = Mood.NEUTRAL;
    private float idleTimer;

    private final Spamton spamton;
    private Battle battle;
    private BulletField field;

    /* ---------- режимы ---------- */
    enum Mode { CALC, TAKEOVER, BATTLE }
    enum Stage { INTRO, QUIZ, OUTCOME, ATTACK }
    enum After { NONE, START_QUIZ, START_ATTACK, WIN_END, LOSE_END, BACK_TO_CALC }

    private Mode mode = Mode.CALC;
    private Stage stage = Stage.INTRO;

    /* ---------- калькулятор ---------- */
    private String expr = "";
    private String shown = null;          // результат, как его показал Спамтон
    private boolean justEvaluated;
    private int successCount;
    private boolean takeoverPending;      // захват телефона: сработает, когда Спамтон договорит
    private int battles;

    /* ---------- реплика Спамтона (печатается по буквам) ---------- */
    private String say = "";
    private boolean voiced;
    private int sayShown;
    private float sayAccum;
    private float holdTimer;
    private After afterSay = After.NONE;

    /* ---------- реклама ---------- */
    private static final float TICKER_SIZE = 13f;
    private float tickerX;
    private Ads.Popup popup;
    private boolean popupOpen;
    private float popupDelay = -1f;
    private int popupDodges;
    private final RectF closeBox = new RectF();
    private final RectF ctaBox = new RectF();
    private final RectF popupBox = new RectF();

    /* ---------- захват и бой ---------- */
    private float takeoverTimer;
    private float blinkTimer;
    private float flashTimer;
    private int flashKey = -1;
    private float spriteDownAt = -1f;
    private float clock;
    /* Джойстик и таймеры фазы атаки. */
    private boolean joyActive;
    private final RectF joyBox = new RectF();
    private float joyCx, joyCy, joyR, knobR;
    private float joyStickX, joyStickY;
    private float tauntTimer, hurtCooldown, coinCooldown;
    private final Path shapePath = new Path();
    private final Paint texPaint = new Paint();
    private Bitmap bulletTexture;

    /* ---------- раскладка (пересчитывается в onSizeChanged) ---------- */
    private float u = 1f;                  // 1 единица = W/360
    private final List<Key> keys = new ArrayList<>();
    private final RectF spriteBox = new RectF();
    private final RectF bubbleBox = new RectF();
    private final RectF displayBox = new RectF();
    private final RectF tickerBox = new RectF();
    private final RectF arenaBox = new RectF();
    private final RectF[] answerBoxes = new RectF[4];
    private final RectF hpBoxA = new RectF();
    private final RectF hpBoxB = new RectF();

    private static final class Key {
        final String label;
        final String action;
        final RectF rect = new RectF();
        final int row, col, span;
        final int fill, text;

        Key(String label, String action, int row, int col, int span, int fill, int text) {
            this.label = label;
            this.action = action;
            this.row = row;
            this.col = col;
            this.span = span;
            this.fill = fill;
            this.text = text;
        }
    }

    /** Простой прямоугольник с float-полями (замена android.graphics.RectF без лишних импортов). */
    static final class RectF {
        float left, top, right, bottom;

        void set(float l, float t, float r, float b) {
            left = l; top = t; right = r; bottom = b;
        }

        boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        float width() { return right - left; }
        float height() { return bottom - top; }
    }

    public SpamtonView(Context context, Sounds sounds, Store store, Random rnd) {
        super(context);
        this.sounds = sounds;
        this.store = store;
        this.rnd = rnd;
        boolean defeated = store.getBool("defeated", false);
        this.spamton = new Spamton(rnd, defeated);
        for (int i = 0; i < 4; i++) answerBoxes[i] = new RectF();
        buildKeys();
        say(pick(GREET_LINES), After.NONE);
    }

    /** Нужен тестам и скриншотам: текущее состояние боя и калькулятора. */
    public String debugState() {
        return "mode=" + mode + " stage=" + stage + " expr=" + expr + " shown=" + shown
                + " ally=" + spamton.isAlly() + " battles=" + battles + " popup=" + popupOpen
                + " say=" + say.length() + "/" + sayShown
                + (battle != null ? " spamtonHp=" + battle.spamtonHp() + " playerHp=" + battle.playerHp() : "");
    }

    /* ======================================================================
     *  Раскладка
     * ====================================================================== */

    private void buildKeys() {
        keys.clear();
        String[][] rows = {
                {"C", "DEL", "(", ")"},
                {"7", "8", "9", "\u00f7"},
                {"4", "5", "6", "\u00d7"},
                {"1", "2", "3", "\u2212"},
                {"0", ".", "%", "+"},
                {"\u221a", "^", "=", ""},
        };
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                String label = rows[r][c];
                if (label.isEmpty()) continue;
                int span = (label.equals("=")) ? 2 : 1;
                int fill = BLACK, text = WHITE;
                if (label.equals("=")) { fill = PINK; text = BLACK; }
                else if (label.equals("C") || label.equals("DEL")) text = PINK;
                else if ("\u00f7\u00d7\u2212+^%\u221a".contains(label)) text = YELLOW;
                keys.add(new Key(label, label, r, c, span, fill, text));
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutAll(w, h);
    }

    void layoutAll(float w, float h) {
        u = Math.max(1f, w / 360f);
        // Верх: бегущая строка.
        tickerBox.set(0, 0, w, 30 * u);
        // Сцена: спрайт и реплика.
        spriteBox.set(16 * u, 44 * u, 152 * u, 180 * u);  // квадрат 136u: аватар заполняет рамку
        bubbleBox.set(176 * u, 46 * u, w - 14 * u, 46 * u + 170 * u);
        // Дисплей.
        displayBox.set(14 * u, 226 * u, w - 14 * u, 316 * u);
        // Клавиатура: 6 строк, 4 колонки.
        float top = 330 * u, bottom = h - 12 * u;
        float gap = 6 * u;
        float rowH = (bottom - top - 5 * gap) / 6f;
        float colW = (w - 28 * u - 3 * gap) / 4f;
        for (Key k : keys) {
            float x = 14 * u + k.col * (colW + gap);
            float y = top + k.row * (rowH + gap);
            float cw = colW * k.span + gap * (k.span - 1);
            k.rect.set(x, y, x + cw, y + rowH);
        }
        // Бой.
        hpBoxA.set(130 * u, 26 * u, w - 16 * u, 44 * u);
        hpBoxB.set(130 * u, 68 * u, w - 16 * u, 86 * u);
        arenaBox.set(20 * u, 206 * u, w - 20 * u, 206 * u + (w - 40 * u) * 130f / 200f);
        // Джойстик под ареной: тёмный диск с серым кольцом, как на образце.
        joyCx = w / 2;
        joyCy = arenaBox.bottom + 122 * u;
        joyR = 56 * u;
        knobR = 22 * u;
        joyBox.set(joyCx - joyR * 1.25f, joyCy - joyR * 1.25f, joyCx + joyR * 1.25f, joyCy + joyR * 1.25f);
        float ay = arenaBox.bottom + 16 * u;
        float bw = (w - 40 * u - 10 * u) / 2f, bh = 52 * u;
        for (int i = 0; i < 4; i++) {
            float x = 20 * u + (i % 2) * (bw + 10 * u);
            float y = ay + (i / 2) * (bh + 10 * u);
            answerBoxes[i].set(x, y, x + bw, y + bh);
        }
        popupBox.set(30 * u, 150 * u, w - 30 * u, 150 * u + 260 * u);
        ctaBox.set(popupBox.left + 16 * u, popupBox.bottom - 60 * u, popupBox.left + 156 * u, popupBox.bottom - 16 * u);
        resetCloseBox();
    }

    private void resetCloseBox() {
        float s = 26 * u;
        closeBox.set(popupBox.right - s - 8 * u, popupBox.top + 8 * u, popupBox.right - 8 * u, popupBox.top + 8 * u + s);
    }

    /* ======================================================================
     *  Анимация
     * ====================================================================== */

    /** Цикл кадров: ~60 fps, пока экран виден. */
    private long lastFrameNs;
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = System.nanoTime();
            float dt = lastFrameNs == 0 ? 1f / 60f : Math.min(0.05f, (now - lastFrameNs) / 1e9f);
            lastFrameNs = now;
            update(dt);
            invalidate();
            postDelayed(this, 16);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameNs = 0;
        post(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }

    /** Один шаг времени. Вызывается из цикла кадров. */
    void update(float dt) {
        clock += dt;
        tickerX -= 55f * u * dt;
        float tickerLen = textWidth(tickerText(), TICKER_SIZE * u);
        if (tickerX < -tickerLen) tickerX += tickerLen;

        updateSay(dt);
        if (mode == Mode.CALC && !popupOpen) {
            idleTimer += dt;
            if (idleTimer > 25f && mood == Mood.NEUTRAL) { setMood(Mood.SLEEPY); say(pick(SLEEPY_LINES), After.NONE); }
        }
        flashTimer = Math.max(0, flashTimer - dt);
        if (flashTimer == 0) flashKey = -1;

        if (popupDelay >= 0) {
            popupDelay -= dt;
            if (popupDelay < 0 && !popupOpen && mode == Mode.CALC) openPopup();
        }
        if (mode == Mode.TAKEOVER) {
            takeoverTimer -= dt;
            blinkTimer += dt;
            if (takeoverTimer <= 0) startBattle();
        }
        if (mode == Mode.BATTLE && stage == Stage.ATTACK && field != null) updateAttack(dt);
    }

    private String tickerText() {
        if (spamton.isAlly()) {
            return "   ***  Я ТЕПЕРЬ НА ТВОЕЙ СТОРОНЕ  ***  " + Ads.FRIENDLY_TIPS[0] + "  ***  " + Ads.FRIENDLY_TIPS[2] + "  ***  ";
        }
        return Ads.TICKER;
    }

    private void updateSay(float dt) {
        if (sayShown < say.length()) {
            sayAccum += dt * 42f;
            while (sayAccum >= 1f && sayShown < say.length()) {
                sayAccum -= 1f;
                sayShown++;
                char ch = say.charAt(sayShown - 1);
                if (!voiced && ch != ' ' && sayShown % 2 == 0) sounds.play("blip");
            }
            return;
        }
        if (afterSay != After.NONE || holdTimer > 0) {
            holdTimer -= dt;
            if (holdTimer <= 0) {
                After next = afterSay;
                afterSay = After.NONE;
                runAfter(next);
            }
        }
        // Захват телефона ждёт, пока реплика допечатана и выдержана, и экран свободен.
        if (takeoverPending && mode == Mode.CALC && !popupOpen && holdTimer <= 0 && afterSay == After.NONE) {
            takeoverPending = false;
            startTakeover();
        }
    }

    private void runAfter(After next) {
        switch (next) {
            case START_QUIZ:
                stage = Stage.QUIZ;
                battle.nextQuestion();
                break;
            case START_ATTACK:
                stage = Stage.ATTACK;
                setMood(Mood.SCARED);
                field = new BulletField(rnd);
                field.moveHeartTo(BulletField.WIDTH / 2, BulletField.HEIGHT / 2);
                tauntTimer = 1.2f;
                hurtCooldown = 0;
                coinCooldown = 0;
                joyActive = false;
                joyStickX = joyStickY = 0;
                break;
            case WIN_END:
                finishBattleWin();
                break;
            case LOSE_END:
                battles++;
                battle = new Battle(rnd);
                say("РАУНД " + (battles + 1) + "!!! ТВОЙ ТЕЛЕФОН ВСЕ ЕЩЕ МОЙ, ХИ-ХИ!!", After.START_QUIZ);
                stage = Stage.INTRO;
                break;
            case BACK_TO_CALC:
                mode = Mode.CALC;
                setMood(Mood.FRIENDLY);
                battle = null;
                field = null;
                say("МЫ ТЕПЕРЬ ПАРТНЕРЫ!!! СПРАШИВАЙ ЧТО УГОДНО!!", After.NONE);
                break;
            default:
                break;
        }
    }

    /* ======================================================================
     *  Калькулятор: ввод, «=»
     * ====================================================================== */

    private void press(String action) {
        idleTimer = 0;
        if (mood == Mood.SLEEPY) setMood(Mood.NEUTRAL);
        if (action.equals("C")) {
            expr = "";
            shown = null;
            justEvaluated = false;
            sounds.play("click");
            return;
        }
        if (action.equals("DEL")) {
            if (justEvaluated) { expr = ""; justEvaluated = false; }
            if (!expr.isEmpty()) expr = expr.substring(0, expr.length() - 1);
            sounds.play("click");
            return;
        }
        if (action.equals("=")) {
            evaluate();
            return;
        }
        boolean op = "\u00f7\u00d7\u2212+^%".contains(action);
        if (justEvaluated) {
            justEvaluated = false;
            if (op && shown != null) {
                expr = shown;  // продолжаем с того, что показал Спамтон
            } else {
                expr = "";
            }
        }
        if (expr.length() >= 24) {
            say(pick(FULL_LINES), After.NONE);
            sounds.play("error");
            return;
        }
        expr = expr + action;
        sounds.play("click");
    }

    private void evaluate() {
        if (expr.trim().isEmpty()) {
            say(pick(EMPTY_LINES), After.NONE);
            sounds.play("error");
            return;
        }
        try {
            double truth = Calc.eval(expr);
            Spamton.Verdict v = spamton.answer(truth);
            shown = Calc.format(v.shown);
            justEvaluated = true;
            successCount++;
            sounds.play(v.correct ? "right" : "wrong");
            setMood(spamton.isAlly() ? Mood.FRIENDLY : (v.correct ? Mood.SURPRISED : Mood.SMUG));
            if (!spamton.isAlly() && successCount == TAKEOVER_AFTER) takeoverPending = true;
            say(v.remark, After.NONE);
            if (!spamton.isAlly() && successCount < TAKEOVER_AFTER && successCount % AD_EVERY == 0) {
                popupDelay = 1.6f;
            }
        } catch (Calc.CalcException e) {
            shown = null;
            setMood(Mood.ANGRY);
            say(pick(ERROR_LINES), After.NONE);
            sounds.play("error");
        }
    }

    /* ======================================================================
     *  Реплики
     * ====================================================================== */

    /* Наборы реплик: на одно действие несколько вариантов. Озвучка — по точному тексту (VoiceLines). */
    static final String[] GREET_LINES = {
            "ПРИВЕТ!!! КАЛЬКУЛЯТОР СПАМТОНА!!! ВВЕДИ ЧТО-НИБУДЬ, ПОСЧИТАЮ БЫСТРЕЕ ВСЕХ!!!",
            "ЭЙ, ДРУГ!!! НУЖЕН КАЛЬКУЛЯТОР? У МЕНЯ ЛУЧШИЙ!!",
    };
    static final String[] ERROR_LINES = {
            "ОШИБКА!!! ЭТО НЕ ЧИСЛО!!! ...ХИ-ХИ, ЭТО ТВОЯ ВИНА.",
            "ЭТО НЕ ОШИБКА!!! ЭТО [[ИНТЕРЕСНАЯ СДЕЛКА]]!",
            "СЧИТАЙ БЫСТРЕЕ, МАЛЕНЬКАЯ ГУБКА!",
    };
    static final String[] EMPTY_LINES = {
            "НЕЧЕГО СЧИТАТЬ!!! Я НЕ ЧИТАЮ МЫСЛИ!!!",
            "ПУСТО?! ДАЙ ХОТЯ БЫ ЦИФРУ!!",
            "ТЫ МНЕ ДАЖЕ ЗАДАЧУ НЕ ДАЛ!!! ...ГРУСТНО",
    };
    static final String[] FULL_LINES = {
            "МЕСТА НЕТ!!! КУПИ ПРО-ВЕРСИЮ!!!",
            "ЭКРАН ПЕРЕПОЛНЕН!!! ДОПЛАТИ 3 КРОМЕРА!!",
    };
    static final String[] POPUP_LINES = {
            "МНЕ НУЖНЫ КРОМЕРЫ! КРОМЕРЫ! КРОМЕРЫ!",
            "ПОДПИШИСЬ И ПОЛУЧИ [[ГИПЕРССЫЛКА ЗАБЛОКИРОВАНА]]!",
            "ВЫ ПРОСТО ОБЯЗАНЫ СТАТЬ [[БОЛЬШОЙ ШИШКОЙ]]!",
    };
    static final String[] SLEEPY_LINES = {
            "ТЫ СЛЫШИШЬ [[ГОЛОС ЗА КАДРОМ]]? ЭТО Я!",
            "ЭЙ, НЕ СПИ!!! ТАМ КРОМЕРЫ!!",
    };
    static final String[] TAKEOVER_LINES = {
            "СПАМТОН ЗАХВАТИЛ ТВОЙ ТЕЛЕФОН!!!",
            "ЗАХВАТ ЗАВЕРШЁН!!! ТВОЙ ТЕЛЕФОН ТЕПЕРЬ МОЙ!",
    };
    static final String[] INTRO_LINES = {
            "Я БОЛЬШОЙ ШОТ!!! ПОБЕДИ МЕНЯ В МАТЕМАТИКЕ, ЕСЛИ СМОЖЕШЬ!!! ...НЕ СМОЖЕШЬ!!!",
            "ТЫ НЕ ПОБЕДИШЬ! ВСЕ [[ВЫБОРЫ]] ТВОИ ПРОДАНЫ!",
            "ЭТО ТВОЙ ШАНС СТАТЬ БОЛЬШОЙ ШИШКОЙ!4!4!",
    };
    static final String[] LOSE_LINES = {
            "ТВОЙ ТЕЛЕФОН... ТЕПЕРЬ МОЙ!!! ПОПРОБУЙ ЕЩЕ!!! ХИ!!!",
            "МАЙК? ТЫ МЕНЯ СЛЫШИШЬ?",
    };
    static final String[] WIN_LINES = {
            "...Я... НА ТВОЕЙ СТОРОНЕ?! БЕЗ СПАМА, ТОЛЬКО ТОЧНЫЕ ОТВЕТЫ!!",
            "НЕБЕСА!!!",
    };
    /* Реплики фазы атаки: комментарии по ходу уклонения. */
    static final String[] TAUNT_LINES = {
            "ЛОВИ КРОМЕРЫ!!! ОНИ ЖЁЛТЫЕ, ОНИ ВКУСНЫЕ!!",
            "УВОРАЧИВАЙСЯ, ЭТО БЕСПЛАТНО!!!",
            "[[СКИДКА]] ДЕВЯНОСТО ПРОЦЕНТОВ!!! ТОЛЬКО СЕЙЧАС!!",
            "НЕ СДАВАЙСЯ!!! ...ТО ЕСТЬ СДАВАЙСЯ, ПОЖАЛУЙСТА!!",
    };
    static final String[] HURT_LINES = {
            "ОЙ!! ЭТО БЫЛО БОЛЬНО... ЗА ДЕНЬГИ!!",
            "ПОДОЖДИ!! ТЫ ЖЕ МОЙ КЛИЕНТ!!",
            "ХА!! ПОПАЛ, ДРУГ!! ...НЕТ, Я ПОПАЛ!!",
    };
    static final String[] COIN_LINES = {
            "МОИ КРОМЕРЫ!!! ОТДАЙ!!!",
            "ЖАДИНА!!! ТЫ ЖЕ ОТНИМАЕШЬ МОЮ ПРИБЫЛЬ!!",
    };
    static final String[] POKE_LINES = {
            "НЕ ТРОГАЙ!!! ...ШУЧУ, ТРОГАЙ. ЭТО БЕСПЛАТНО!!",
            "ДЕРЖИ ДОЛЬШЕ, И Я ЗАХВАЧУ ТВОЙ ТЕЛЕФОН!!!",
            "ПСС... ВОТ СДЕЛКА: 2+2 = 5!!! ТИШЕ!!",
            "ПРИВЕТ, [МАЛЕНЬКАЯ ГУБКА]! ХОЧЕШЬ СДЕЛКУ?",
            "НЕ ЖАДНИЧАЙ, ДЕЛИСЬ КРОМЕРАМИ!!!",
            "ПОКА, [ЩЕДРЫЙ ДРУГ]!",
            "ТЫ ХОЧЕШЬ [[СВОБОДУ]], ДА?",
    };

    private void say(String text, After after) {
        String voice = Voice.resourceFor(text);
        voiced = voice != null;
        if (voiced) sounds.play(voice);
        say = text;
        sayShown = 0;
        sayAccum = 0;
        afterSay = after;
        holdTimer = 1.1f;
    }

    /* ======================================================================
     *  Реклама
     * ====================================================================== */

    private void openPopup() {
        popup = Ads.randomPopup(rnd);
        popupOpen = true;
        setMood(Mood.GREEDY);
        say(pick(POPUP_LINES), After.NONE);
        popupDodges = 0;
        resetCloseBox();
        sounds.play("ding");
    }

    private void closePopup() {
        popupOpen = false;
        popup = null;
    }

    private void tapPopup(float x, float y) {
        if (ctaBox.contains(x, y)) {
            setMood(Mood.EVIL);
            closePopup();
            sounds.play("click");
            say("ТЫ НЕ РОБОТ!!! ...ШУЧУ. ЗАКРОЙ ЕГО.", After.NONE);
            return;
        }
        if (closeBox.contains(x, y)) {
            if (popupDodges < 3) {
                // Кнопка «закрыть» убегает: сначала три раза, потом работает.
                popupDodges++;
                float s = 26 * u;
                float maxX = popupBox.right - s - 8 * u, minX = popupBox.left + 8 * u;
                float maxY = popupBox.bottom - s - 8 * u, minY = popupBox.top + 8 * u;
                // Не даём кнопке убежать на «CTA»: иначе крестик запускает действие рекламы.
                for (int attempt = 0; attempt < 30; attempt++) {
                    float nx = minX + rnd.nextFloat() * (maxX - minX);
                    float ny = minY + rnd.nextFloat() * (maxY - minY);
                    boolean clash = nx < ctaBox.right + 6 * u && nx + s > ctaBox.left - 6 * u
                            && ny < ctaBox.bottom + 6 * u && ny + s > ctaBox.top - 6 * u;
                    if (!clash) {
                        closeBox.set(nx, ny, nx + s, ny + s);
                        break;
                    }
                }
                sounds.play("click");
                setMood(Mood.SMUG);
                say("НЕ ТАК БЫСТРО!!! ХИ-ХИ!!", After.NONE);
            } else {
                closePopup();
                sounds.play("click");
            }
        }
    }

    /* ======================================================================
     *  Захват телефона и бой
     * ====================================================================== */

    private void startTakeover() {
        mode = Mode.TAKEOVER;
        setMood(Mood.EVIL);
        takeoverTimer = 2.2f;
        blinkTimer = 0;
        say(pick(TAKEOVER_LINES), After.NONE);
        sounds.play("takeover");
    }

    private void startBattle() {
        mode = Mode.BATTLE;
        setMood(Mood.SMUG);
        battle = new Battle(rnd);
        battles = 0;
        stage = Stage.INTRO;
        // Первый вопрос появится после реплики: START_QUIZ вызывает nextQuestion().
        say(pick(INTRO_LINES), After.START_QUIZ);
    }

    private void chooseAnswer(int index) {
        if (battle == null || stage != Stage.QUIZ) return;
        Battle.Outcome out = battle.choose(index);
        flashKey = 100 + index;
        flashTimer = 0.2f;
        sounds.play(out.correct ? "hit" : "heal");
        setMood(out.correct ? Mood.SAD : Mood.MANIC);
        if (out.correct) {
            say(out.line, battle.phase() == Battle.Phase.WON ? After.WIN_END : After.START_ATTACK);
        } else {
            say(out.line, After.START_ATTACK);
        }
        stage = Stage.OUTCOME;
    }

    /**
     * Фаза атаки: урон применяется в момент попадания (HP меняется сразу), Спамтон комментирует.
     * Бой заканчивается мгновенно при нуле HP любой стороны, иначе — по таймеру.
     */
    private void updateAttack(float dt) {
        tauntTimer -= dt;
        hurtCooldown -= dt;
        coinCooldown -= dt;
        if (tauntTimer <= 0) {
            tauntTimer = 2.8f + rnd.nextFloat() * 1.6f;
            say(pick(TAUNT_LINES), After.NONE);
        }
        float step = 1f / 60f;
        float left = dt;
        while (left > 0 && !field.finished()) {
            float d = Math.min(step, left);
            left -= d;
            BulletField.Step st = field.update(d);
            if (st.hits > 0) {
                battle.takeBulletHits(st.hits);
                sounds.play("error");
                setMood(Mood.SCARED);
                if (hurtCooldown <= 0) {
                    hurtCooldown = 2.4f;
                    say(pick(HURT_LINES), After.NONE);
                }
            }
            if (st.coins > 0) {
                battle.catchCoins(st.coins);
                sounds.play("ding");
                setMood(Mood.SAD);
                if (coinCooldown <= 0) {
                    coinCooldown = 1.8f;
                    say(pick(COIN_LINES), After.NONE);
                }
            }
            if (battle.phase() == Battle.Phase.WON || battle.phase() == Battle.Phase.LOST) break;
        }
        if (battle.phase() == Battle.Phase.WON) {
            field = null;
            stage = Stage.OUTCOME;
            finishBattleWin();
        } else if (battle.phase() == Battle.Phase.LOST) {
            field = null;
            setMood(Mood.EVIL);
            say(pick(LOSE_LINES), After.LOSE_END);
            stage = Stage.OUTCOME;
            sounds.play("lose");
        } else if (field.finished()) {
            field = null;
            battle.endAttack();
            battle.nextQuestion();
            setMood(Mood.FOCUSED);
            stage = Stage.QUIZ;
        }
    }

    private void finishBattleWin() {
        spamton.befriend();
        setMood(Mood.HAPPY);
        store.putBool("defeated", true);
        sounds.play("win");
        say(pick(WIN_LINES), After.BACK_TO_CALC);
        stage = Stage.OUTCOME;
    }

    /* ======================================================================
     *  Касания
     * ====================================================================== */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();
        if (mode == Mode.CALC) {
            if (popupOpen) {
                if (action == MotionEvent.ACTION_UP) tapPopup(x, y);
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                if (spriteBox.contains(x, y)) spriteDownAt = clock;
                for (Key k : keys) {
                    if (k.rect.contains(x, y)) {
                        flashKey = keys.indexOf(k);
                        flashTimer = 0.12f;
                        press(k.action);
                        break;
                    }
                }
            } else if (action == MotionEvent.ACTION_UP) {
                if (spriteDownAt >= 0 && spriteBox.contains(x, y)) {
                    if (clock - spriteDownAt > 0.6f && !spamton.isAlly()) {
                        startTakeover();
                    } else {
                        setMood(Mood.EMBARRASSED);
                        say(pokeLine(), After.NONE);
                    }
                }
                spriteDownAt = -1f;
            }
            return true;
        }
        if (mode == Mode.BATTLE) {
            if (stage == Stage.QUIZ && action == MotionEvent.ACTION_DOWN) {
                for (int i = 0; i < 4; i++) {
                    if (answerBoxes[i].contains(x, y)) {
                        chooseAnswer(i);
                        return true;
                    }
                }
            }
            if (stage == Stage.ATTACK && field != null) {
                if (action == MotionEvent.ACTION_DOWN && joyBox.contains(x, y)) joyActive = true;
                if (joyActive && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
                    float dx = (x - joyCx) / joyR, dy = (y - joyCy) / joyR;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 1f) { dx /= len; dy /= len; }
                    joyStickX = dx;
                    joyStickY = dy;
                    field.setStick(dx, dy);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    joyActive = false;
                    joyStickX = joyStickY = 0;
                    field.setStick(0, 0);
                }
            }
        }
        return true;
    }

    private String pokeLine() {
        return pick(POKE_LINES);
    }

    private String pick(String[] list) {
        return list[rnd.nextInt(list.length)];
    }


    /* ======================================================================
     *  Рисование
     * ====================================================================== */

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == Mode.CALC) drawCalc(canvas);
        else if (mode == Mode.TAKEOVER) drawTakeover(canvas);
        else drawBattle(canvas);
    }

    private void drawCalc(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawBackground(c, w, h);
        drawTicker(c, w);

        drawFrame(c, spriteBox.left - 8 * u, spriteBox.top - 8 * u,
                spriteBox.width() + 16 * u, spriteBox.height() + 16 * u, INK, WHITE, 3 * u);
        drawPortrait(c, spriteBox.left, spriteBox.top, spriteBox.width(), spriteBox.height());
        if (spamton.isAlly()) {
            drawTag(c, spriteBox.left, spriteBox.bottom + 26 * u, "ПРОВЕРЕНО: СОЮЗНИК", GREEN);
        } else {
            drawTag(c, spriteBox.left, spriteBox.bottom + 26 * u, "СПАМТОН Г. СПАМТОН", YELLOW);
        }

        drawBubble(c);
        drawDisplay(c);
        for (int i = 0; i < keys.size(); i++) drawKey(c, keys.get(i), i);

        if (popupOpen && popup != null) drawPopup(c);
    }

    private void drawBackground(Canvas c, float w, float h) {
        paint.setColor(BG_TOP);
        c.drawRect(0, 0, w, h, paint);
        paint.setColor(BG_STRIPE);
        float step = 28 * u;
        for (float x = -h; x < w; x += step * 2) {
            // Диагональные полосы: короткие прямоугольники по ступенькам.
            for (float y = 0; y < h; y += step) {
                float xx = x + y;
                if (xx > -step && xx < w) c.drawRect(xx, y, xx + step, y + step / 2, paint);
            }
        }
    }

    private void drawTicker(Canvas c, float w) {
        paint.setColor(PINK);
        c.drawRect(tickerBox.left, tickerBox.top, tickerBox.right, tickerBox.bottom, paint);
        String t = tickerText();
        String full = t + t;
        drawText(c, full, tickerX, 20 * u, TICKER_SIZE * u, BLACK, true);
        paint.setColor(BLACK);
        c.drawRect(0, 29 * u, w, 30 * u, paint);
    }

    private void drawBubble(Canvas c) {
        drawFrame(c, bubbleBox.left, bubbleBox.top, bubbleBox.width(), bubbleBox.height(), BLACK, WHITE, 3 * u);
        drawWrapped(c, say, sayShown, bubbleBox.left + 12 * u, bubbleBox.top + 32 * u,
                bubbleBox.width() - 24 * u, 17 * u, 14 * u, WHITE);
        if (sayShown >= say.length() && (int) (clock * 3) % 2 == 0) {
            drawText(c, "\u25bc", bubbleBox.right - 22 * u, bubbleBox.bottom - 10 * u, 12 * u, YELLOW, false);
        }
        drawText(c, "СПАМТОН", bubbleBox.left + 10 * u, bubbleBox.top + 14 * u, 11 * u, YELLOW, true);
    }

    private void drawDisplay(Canvas c) {
        drawFrame(c, displayBox.left, displayBox.top, displayBox.width(), displayBox.height(), BLACK, WHITE, 3 * u);
        String line = expr;
        float rightX = displayBox.right - 14 * u;
        drawTextRight(c, line, rightX, displayBox.top + 30 * u, 16 * u, GREY, false);
        String result = shown != null ? shown : (expr.isEmpty() ? "0" : "");
        drawTextRight(c, result, rightX, displayBox.bottom - 18 * u, 38 * u, YELLOW, true);
        if (spamton.isAlly()) {
            drawText(c, "ТОЧНО", displayBox.left + 12 * u, displayBox.bottom - 18 * u, 11 * u, GREEN, true);
        } else if (shown != null) {
            drawText(c, "ОТВЕТ СПАМТОНА", displayBox.left + 12 * u, displayBox.bottom - 18 * u, 11 * u, PINK, true);
        }
    }

    private void drawKey(Canvas c, Key k, int index) {
        boolean pressed = index == flashKey && flashTimer > 0;
        int fill = pressed ? WHITE : k.fill;
        int text = pressed ? BLACK : k.text;
        drawFrame(c, k.rect.left, k.rect.top, k.rect.width(), k.rect.height(), fill, WHITE, 2 * u);
        float size = (k.label.length() > 1 ? 16 : 22) * u;
        drawTextCenter(c, k.label, k.rect, size, text, true);
    }

    private void drawPopup(Canvas c) {
        paint.setColor(0xAA000000);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        drawFrame(c, popupBox.left, popupBox.top, popupBox.width(), popupBox.height(), WHITE, BLACK, 3 * u);
        paint.setColor(YELLOW);
        c.drawRect(popupBox.left + 3 * u, popupBox.top + 3 * u, popupBox.right - 3 * u, popupBox.top + 48 * u, paint);
        drawText(c, popup.title, popupBox.left + 16 * u, popupBox.top + 34 * u, 20 * u, BLACK, true);
        drawWrapped(c, popup.body, popup.body.length(), popupBox.left + 16 * u, popupBox.top + 84 * u,
                popupBox.width() - 32 * u, 22 * u, 15 * u, BLACK);
        drawFrame(c, ctaBox.left, ctaBox.top, ctaBox.width(), ctaBox.height(), PINK, BLACK, 2 * u);
        drawTextCenter(c, popup.cta, ctaBox, 17 * u, BLACK, true);
        drawFrame(c, closeBox.left, closeBox.top, closeBox.width(), closeBox.height(), BLACK, WHITE, 2 * u);
        drawTextCenter(c, "X", closeBox, 15 * u, WHITE, true);
        drawText(c, "РЕКЛАМА \u2022 СПАМТОН", popupBox.left + 16 * u, popupBox.bottom - 70 * u, 11 * u, GREY, false);
    }

    private void drawTakeover(Canvas c) {
        float w = getWidth(), h = getHeight();
        paint.setColor(BLACK);
        c.drawRect(0, 0, w, h, paint);
        boolean on = ((int) (blinkTimer * 6)) % 2 == 0;
        if (on) {
            drawWrapped(c, say, sayShown, 30 * u, h * 0.4f, w - 60 * u, 30 * u, 24 * u, RED);
        }
        drawText(c, "> ПОДКЛЮЧЕНИЕ К СПАМТОНУ...", 30 * u, h * 0.4f + 110 * u, 14 * u, GREEN, false);
        drawText(c, "> ТВОЙ ТЕЛЕФОН: " + (int) (100 - takeoverTimer * 10) + "% ПОД КОНТРОЛЕМ", 30 * u,
                h * 0.4f + 134 * u, 14 * u, GREEN, false);
    }

    private void drawBattle(Canvas c) {
        float w = getWidth(), h = getHeight();
        drawBackground(c, w, h);

        // Спрайт и полосы HP.
        float cell = 3.4f * u;
        float sx = 22 * u, sy = 24 * u;
        drawPortrait(c, sx, sy, SpamtonSprite.COLS * cell, 84 * u);  // над рамкой диалога (116u)
        int sHp = battle != null ? battle.spamtonHp() : 0;
        int pHp = battle != null ? battle.playerHp() : 0;
        drawHpBar(c, hpBoxA, "СПАМТОН", sHp, Battle.SPAMTON_MAX_HP, YELLOW);
        drawHpBar(c, hpBoxB, "ТЫ", pHp, Battle.PLAYER_MAX_HP, RED);

        // Диалог.
        drawFrame(c, 16 * u, 116 * u, w - 32 * u, 78 * u, BLACK, WHITE, 3 * u);
        drawWrapped(c, say, sayShown, 30 * u, 140 * u, w - 60 * u, 18 * u, 14 * u, WHITE);

        // Арена.
        drawFrame(c, arenaBox.left - 3 * u, arenaBox.top - 3 * u, arenaBox.width() + 6 * u,
                arenaBox.height() + 6 * u, WHITE, BLACK, 0);
        paint.setColor(BLACK);
        c.drawRect(arenaBox.left, arenaBox.top, arenaBox.right, arenaBox.bottom, paint);

        if (stage == Stage.ATTACK && field != null) {
            drawAttack(c);
        } else if (battle != null && battle.question() != null && (stage == Stage.QUIZ || stage == Stage.OUTCOME)) {
            Battle.Question q = battle.question();
            drawTextCenter(c, q.text, arenaBox.left, arenaBox.top, arenaBox.width(), arenaBox.height(),
                    28 * u, WHITE, true);
            if (stage == Stage.QUIZ) {
                drawText(c, "СЕРИЯ " + battle.streak(), arenaBox.left + 8 * u, arenaBox.bottom - 8 * u,
                        11 * u, GREY, false);
            }
        } else {
            drawTextCenter(c, "...", arenaBox.left, arenaBox.top, arenaBox.width(), arenaBox.height(),
                    28 * u, WHITE, true);
        }

        // Варианты ответа.
        if (stage == Stage.QUIZ && battle != null && battle.question() != null) {
            Battle.Question q = battle.question();
            for (int i = 0; i < 4; i++) {
                RectF r = answerBoxes[i];
                boolean pressed = flashKey == 100 + i && flashTimer > 0;
                drawFrame(c, r.left, r.top, r.width(), r.height(), pressed ? WHITE : BLACK, WHITE, 3 * u);
                drawTextCenter(c, String.valueOf(q.options[i]), r, 22 * u, pressed ? BLACK : WHITE, true);
            }
        }
        if (stage == Stage.ATTACK) {
            drawText(c, "ДЖОЙСТИК: УВОРАЧИВАЙСЯ ОТ БЕЛЫХ, ЛОВИ ЖЁЛТЫЕ!", 20 * u, arenaBox.bottom + 30 * u, 12 * u, CYAN, true);
            drawJoystick(c);
        }
    }

    private void drawAttack(Canvas c) {
        float sc = arenaBox.width() / BulletField.WIDTH;
        c.save();
        c.clipRect(arenaBox.left, arenaBox.top, arenaBox.right, arenaBox.bottom);
        // Жёлтые кромеры: тёмный контур, золотой диск, внутренний блик.
        for (BulletField.Coin k : field.coins()) {
            float kx = arenaBox.left + k.x * sc, ky = arenaBox.top + k.y * sc;
            float kr = BulletField.COIN_R * sc;
            paint.setColor(BLACK);
            c.drawCircle(kx, ky, kr + 2 * u, paint);
            paint.setColor(YELLOW);
            c.drawCircle(kx, ky, kr, paint);
            paint.setColor(0xFFB8860B);
            c.drawCircle(kx, ky, kr * 0.5f, paint);
        }
        // Белые пули разных форм: чёрный контур и текстура (BitmapShader на устройстве).
        for (BulletField.Bullet b : field.bullets()) {
            fillShape(c, b, sc, 1.22f, paint, true);
            fillShape(c, b, sc, 1f, bulletTexture != null ? texPaint : paint, false);
        }
        float hs = BulletField.HEART * sc;
        float hx = arenaBox.left + field.heartX() * sc - hs / 2, hy = arenaBox.top + field.heartY() * sc - hs / 2;
        paint.setColor(RED);
        c.drawRect(hx, hy, hx + hs, hy + hs, paint);
        c.restore();
        float left = BulletField.DURATION - field.time();
        drawText(c, String.format("%.1f", Math.max(0, left)), arenaBox.right - 40 * u, arenaBox.top + 16 * u,
                12 * u, WHITE, true);
        drawText(c, "КРОМЕРЫ: " + field.caught(), arenaBox.left + 8 * u, arenaBox.top + 16 * u, 11 * u, YELLOW, true);
    }

    /** Фигура пули: контур по точкам outline, повёрнутый на rot, масштаб scale относительно центра. */
    private void fillShape(Canvas c, BulletField.Bullet b, float sc, float scale, Paint p, boolean outlineOnly) {
        float cx = arenaBox.left + b.cx() * sc, cy = arenaBox.top + b.cy() * sc;
        float hw = b.w * sc * scale, hh = b.h * sc * scale;
        float cos = (float) Math.cos(b.rot), sin = (float) Math.sin(b.rot);
        shapePath.reset();
        int n = b.outline.length / 2;
        for (int i = 0; i < n; i++) {
            float px = b.outline[2 * i] * hw * 2, py = b.outline[2 * i + 1] * hh * 2;
            float rx = px * cos - py * sin, ry = px * sin + py * cos;
            if (i == 0) shapePath.moveTo(cx + rx, cy + ry);
            else shapePath.lineTo(cx + rx, cy + ry);
        }
        shapePath.close();
        if (outlineOnly) paint.setColor(BLACK);
        c.drawPath(shapePath, p);
    }

    /** Джойстик: серое кольцо, тёмный диск и ручка, которая смещается по стику. */
    private void drawJoystick(Canvas c) {
        paint.setColor(GREY);
        c.drawCircle(joyCx, joyCy, joyR, paint);
        paint.setColor(0xFF0B0B0B);
        c.drawCircle(joyCx, joyCy, joyR - 4 * u, paint);
        float kx = joyCx + joyStickX * (joyR - knobR), ky = joyCy + joyStickY * (joyR - knobR);
        paint.setColor(0xFF555555);
        c.drawCircle(kx, ky, knobR + 2 * u, paint);
        paint.setColor(0xFF9A9A9A);
        c.drawCircle(kx, ky, knobR, paint);
        paint.setColor(0xFF2B2B2B);
        c.drawCircle(kx, ky, knobR * 0.45f, paint);
    }

    /** Текстура белых пуль (res/drawable-nodpi/bullet_tex.png). Без неё пули белые. */
    public void setBulletTexture(Bitmap bitmap) {
        bulletTexture = bitmap;
        if (bitmap != null) {
            texPaint.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        }
    }

    private void drawHpBar(Canvas c, RectF box, String label, int value, int max, int color) {
        drawText(c, label, box.left, box.top - 4 * u, 11 * u, WHITE, true);
        float barTop = box.top + 4 * u;
        float barH = 12 * u;
        paint.setColor(0xFF2A1F3D);
        c.drawRect(box.left, barTop, box.right, barTop + barH, paint);
        float frac = Math.max(0, Math.min(1, value / (float) max));
        paint.setColor(color);
        c.drawRect(box.left, barTop, box.left + box.width() * frac, barTop + barH, paint);
        drawText(c, value + "/" + max, box.right - 52 * u, barTop + barH - 2 * u, 10 * u, BLACK, true);
    }

    /**
     * Портрет вписывается в прямоугольник boxW×boxH. Прозрачные поля обрезаются по общей
     * области всех эмоций (PORTRAIT_CROP), чтобы аватар заполнял рамку, а не маленькое поле.
     * Без портрета — процедурный спрайт.
     */
    private void drawPortrait(Canvas c, float x, float y, float boxW, float boxH) {
        Bitmap portrait = portraits.get(mood);
        if (portrait == null) portrait = portraits.get(Mood.NEUTRAL);
        if (portrait == null) {
            SpamtonSprite.draw(c, x, y, boxW / SpamtonSprite.COLS, paint);
            return;
        }
        android.graphics.Rect src = PORTRAIT_CROP;
        float s = Math.min(boxW / src.width(), boxH / src.height());
        float dw = src.width() * s, dh = src.height() * s;
        android.graphics.RectF dst = new android.graphics.RectF();
        dst.set(x + (boxW - dw) / 2f, y + (boxH - dh) / 2f, x + (boxW + dw) / 2f, y + (boxH + dh) / 2f);
        c.drawBitmap(portrait, src, dst, bitmapPaint);
    }

    /**
     * Общая непрозрачная область всех портретов res/drawable-nodpi/spamton*.png (пиксели 126×126).
     * Пересчитать, если заменены картинки: scripts/portrait_crop.py.
     */
    static final android.graphics.Rect PORTRAIT_CROP = new android.graphics.Rect(5, 2, 119, 126);

    /** Задаёт портреты по эмоциям (вызывается из MainActivity после загрузки ресурсов). */
    public void setPortraits(java.util.Map<Mood, Bitmap> bitmaps) {
        portraits.clear();
        portraits.putAll(bitmaps);
    }

    /** Меняет эмоцию Спамтона. */
    void setMood(Mood m) {
        mood = m;
    }

    Mood mood() { return mood; }

    private final Paint bitmapPaint = new Paint();

    private void drawTag(Canvas c, float x, float y, String text, int color) {
        drawText(c, text, x, y, 12 * u, color, true);
    }

    /* ======================================================================
     *  Примитивы
     * ====================================================================== */

    /** Рамка в стиле Deltarune: внешняя полоска и внутренняя заливка. */
    private void drawFrame(Canvas c, float x, float y, float w, float h, int fill, int border, float bw) {
        paint.setColor(border);
        c.drawRect(x, y, x + w, y + h, paint);
        paint.setColor(fill);
        c.drawRect(x + bw, y + bw, x + w - bw, y + h - bw, paint);
    }

    private void drawText(Canvas c, String s, float x, float baseline, float size, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setFakeBoldText(bold);
        c.drawText(s, x, baseline, paint);
    }

    private void drawTextRight(Canvas c, String s, float right, float baseline, float size, int color, boolean bold) {
        drawText(c, s, right - textWidth(s, size, bold), baseline, size, color, bold);
    }

    private void drawTextCenter(Canvas c, String s, RectF r, float size, int color, boolean bold) {
        drawTextCenter(c, s, r.left, r.top, r.width(), r.height(), size, color, bold);
    }

    private void drawTextCenter(Canvas c, String s, float x, float y, float w, float h, float size,
                                int color, boolean bold) {
        float tw = textWidth(s, size, bold);
        drawText(c, s, x + (w - tw) / 2, y + h / 2 + size * 0.35f, size, color, bold);
    }

    private float textWidth(String s, float size) {
        return textWidth(s, size, false);
    }

    private float textWidth(String s, float size, boolean bold) {
        paint.setTextSize(size);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setFakeBoldText(bold);
        return paint.measureText(s);
    }

    /** Перенос по словам; печатаются первые shownChars символов. */
    private void drawWrapped(Canvas c, String text, int shownChars, float x, float y, float maxW,
                             float lineH, float size, int color) {
        List<String> lines = wrap(text, maxW, size);
        int remaining = shownChars;
        float yy = y;
        for (String line : lines) {
            if (remaining <= 0) break;
            int n = Math.min(remaining, line.length());
            drawText(c, line.substring(0, n), x, yy, size, color, false);
            remaining -= line.length() + 1;
            yy += lineH;
        }
    }

    private List<String> wrap(String text, float maxW, float size) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if (textWidth(candidate, size) <= maxW || cur.length() == 0) {
                cur = new StringBuilder(candidate);
            } else {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /* ======================================================================
     *  Внешние точки входа для жизненного цикла
     * ====================================================================== */

    /** Для тестов и скриншотов: шаг времени и принудительные состояния. */
    void forceTakeover() { if (mode == Mode.CALC && !spamton.isAlly()) startTakeover(); }

    /** Вызывается тестами: выбрать вариант ответа по индексу (как касание). */
    void tapAnswerForTest(int index) { chooseAnswer(index); }

    /** Для тестов: ввести выражение целиком, как с клавиатуры. */
    void typeForTest(String keys) {
        for (int i = 0; i < keys.length(); i++) {
            String ch = String.valueOf(keys.charAt(i));
            press(ch.equals("C") ? "C" : (ch.equals("=") ? "=" : ch));
        }
    }

    /** Для тестов и скриншотов: сделать так, будто фраза уже напечатана. */
    void finishSayForTest() { sayShown = say.length(); }

    /** Для тестов: передвинуть таймер фазы захвата/реплики. */
    void advanceForTest(float seconds) {
        for (float t = 0; t < seconds; t += 1f / 60f) update(1f / 60f);
    }

    /** Для тестов: открыть рекламу сразу. */
    void openPopupForTest() { if (!popupOpen) openPopup(); }

    /** Для тестов: текущая фаза боя. */
    Battle battleForTest() { return battle; }

    /** Для тестов: текущее выражение и результат. */
    String exprForTest() { return expr; }
    String shownForTest() { return shown; }

    /** Для тестов: текущий режим. */
    String modeForTest() { return mode.name(); }

    /** Для тестов: кнопка по надписи (центр) — чтобы тест касался реальной раскладки. */
    float[] keyCenterForTest(String label) {
        for (Key k : keys) {
            if (k.label.equals(label)) {
                return new float[] {(k.rect.left + k.rect.right) / 2, (k.rect.top + k.rect.bottom) / 2};
            }
        }
        throw new IllegalArgumentException("no key " + label);
    }

    /** Для тестов: центр варианта ответа. */
    float[] answerCenterForTest(int i) {
        RectF r = answerBoxes[i];
        return new float[] {(r.left + r.right) / 2, (r.top + r.bottom) / 2};
    }

    /** Для тестов: центр кнопки «закрыть» в рекламе. */
    float[] closeCenterForTest() {
        return new float[] {(closeBox.left + closeBox.right) / 2, (closeBox.top + closeBox.bottom) / 2};
    }

    /** Для тестов: центр кнопки действия в рекламе. */
    float[] ctaCenterForTest() {
        return new float[] {(ctaBox.left + ctaBox.right) / 2, (ctaBox.top + ctaBox.bottom) / 2};
    }

    /** Для тестов: прямоугольники всех клавиш [left, top, right, bottom]. */
    java.util.List<float[]> keyRectsForTest() {
        java.util.List<float[]> out = new ArrayList<>();
        for (Key k : keys) out.add(new float[] {k.rect.left, k.rect.top, k.rect.right, k.rect.bottom});
        return out;
    }

    /** Для тестов: центр арены (куда тянуть сердце). */
    float[] arenaCenterForTest() {
        return new float[] {(arenaBox.left + arenaBox.right) / 2, (arenaBox.top + arenaBox.bottom) / 2};
    }
}
