package com.example.junkil;

import java.util.Random;

/**
 * Логика боя с Джанклимом (без Android).
 *
 * Ход: МЕНЮ → (ФАЙТ с таймингом | ДЕЙСТВИЕ | ПРЕДМЕТ | МИЛОСТЬ) → реплика Джанклима → АРЕНА → следующий ход.
 * Особенность Джанклима — АНАЛОГОВЫЙ СИГНАЛ: перед каждым ходом он передаёт слово азбукой Морзе.
 * Расшифровка верная — его следующая атака слабее, неверная — сильнее. Ещё есть ДОВОЛЬСТВО:
 * при 100 открывается МИЛОСТЬ (концовка «Свободный торг»).
 */
final class BattleLogic {

    static final int PLAYER_MAX = 20;
    static final int JUNK_MAX = 90;
    static final int SATISFY_MAX = 100;
    static final int HIT_DAMAGE = 2;          // урон за одно попадание на арене
    static final int BATTERY_HEAL = 8;
    static final int BATTERY_MAX = 3;
    static final int FIGHT_MIN = 4;           // урон при промахе по тайму — 0, при точном ударе — FIGHT_MAX
    static final int FIGHT_MAX = 12;
    static final double MISS_BELOW = 0.2;     // точность ниже этой — промах
    static final int MORSE_RIGHT = 25;
    static final int MORSE_WRONG = -10;
    static final int HAGGLE_GAIN = 15;

    /** Слова для сигнала. Должны состоять из букв A–Z. */
    static final String[] WORDS = {"SOS", "JUNK", "FREE", "BUY", "SALE", "HELP", "YES", "NO"};

    enum Phase { MENU, FIGHT, ACT, ITEM, MORSE, JUNK_TALK, ARENA, WON, SPARED, LOST }

    enum Act { LOOK, MORSE, HAGGLE }

    /** Тип следующей атаки Джанклима. */
    enum Pattern { NORMAL, REDUCED, BOOSTED }

    enum Mood { NORMAL, ANGRY, SAD }

    final Random rng;
    Phase phase = Phase.MENU;
    int playerHp = PLAYER_MAX;
    int junkHp = JUNK_MAX;
    int satisfy = 0;
    int batteries = 2;
    int turn = 1;
    String morseWord = WORDS[0];
    String[] morseOptions = new String[3];
    int morseCorrect;
    Pattern nextPattern = Pattern.NORMAL;
    int lastDamage;
    String line = "";

    BattleLogic(Random rng) {
        this.rng = rng;
        newSignal();
    }

    /** Новый сигнал на ход: верное слово и два неверных варианта. */
    void newSignal() {
        morseWord = WORDS[rng.nextInt(WORDS.length)];
        int a = indexOfOther(-1, morseWord);
        int b = indexOfOther(a, morseWord);
        morseCorrect = rng.nextInt(3);
        String[] wrong = {WORDS[a], WORDS[b]};
        int w = 0;
        for (int i = 0; i < 3; i++) {
            morseOptions[i] = (i == morseCorrect) ? morseWord : wrong[w++];
        }
        line = Phrases.pick(Phrases.TURN_START, rng);
    }

    private int indexOfOther(int exclude, String not) {
        while (true) {
            int i = rng.nextInt(WORDS.length);
            if (i != exclude && !WORDS[i].equals(not)) return i;
        }
    }

    boolean isOver() {
        return phase == Phase.WON || phase == Phase.SPARED || phase == Phase.LOST;
    }

    Mood mood() {
        if (junkHp <= JUNK_MAX / 4) return Mood.SAD;
        if (nextPattern == Pattern.BOOSTED || lastDamage >= 10) return Mood.ANGRY;
        return Mood.NORMAL;
    }

    /** Открыть тайминг-удар. */
    void chooseFight() {
        requireMenu();
        phase = Phase.FIGHT;
    }

    /** Итог тайминга: accuracy 0..1 (1 — точно в центре). Возвращает нанесённый урон. */
    int fight(double accuracy) {
        if (phase != Phase.FIGHT) throw new IllegalStateException("не фаза FIGHT: " + phase);
        double acc = Math.max(0, Math.min(1, accuracy));
        int dmg = 0;
        if (acc >= MISS_BELOW) {
            dmg = FIGHT_MIN + (int) Math.round((FIGHT_MAX - FIGHT_MIN) * acc);
        }
        lastDamage = dmg;
        junkHp = Math.max(0, junkHp - dmg);
        line = dmg == 0 ? Phrases.pick(Phrases.FIGHT_MISS, rng)
                : (acc > 0.9 ? Phrases.pick(Phrases.FIGHT_CRIT, rng) : Phrases.pick(Phrases.FIGHT_HIT, rng));
        phase = Phase.JUNK_TALK;
        return dmg;
    }

    void chooseAct(Act act) {
        requireMenu();
        switch (act) {
            case LOOK:
                line = Phrases.pick(Phrases.LOOK, rng);
                phase = Phase.JUNK_TALK;
                break;
            case HAGGLE:
                satisfy = Math.min(SATISFY_MAX, satisfy + HAGGLE_GAIN);
                batteries = Math.min(BATTERY_MAX, batteries + 1);
                nextPattern = Pattern.BOOSTED;
                line = Phrases.pick(Phrases.HAGGLE, rng);
                phase = Phase.JUNK_TALK;
                break;
            case MORSE:
                phase = Phase.MORSE;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    /** Ответ на сигнал. Возвращает true, если расшифровка верная. */
    boolean answerMorse(int index) {
        if (phase != Phase.MORSE) throw new IllegalStateException("не фаза MORSE: " + phase);
        boolean right = index == morseCorrect;
        if (right) {
            satisfy = Math.min(SATISFY_MAX, satisfy + MORSE_RIGHT);
            nextPattern = Pattern.REDUCED;
            line = Phrases.pick(Phrases.MORSE_RIGHT, rng);
        } else {
            satisfy = Math.max(0, satisfy + MORSE_WRONG);
            nextPattern = Pattern.BOOSTED;
            line = Phrases.pick(Phrases.MORSE_WRONG, rng);
        }
        phase = Phase.JUNK_TALK;
        return right;
    }

    void openItems() {
        requireMenu();
        phase = Phase.ITEM;
    }

    /** Батарейка: лечит, тратит ход. Возвращает false, если батареек нет. */
    boolean useBattery() {
        if (phase != Phase.ITEM && phase != Phase.MENU) throw new IllegalStateException("не меню: " + phase);
        if (batteries <= 0) return false;
        batteries--;
        playerHp = Math.min(PLAYER_MAX, playerHp + BATTERY_HEAL);
        line = Phrases.pick(Phrases.BATTERY, rng);
        phase = Phase.JUNK_TALK;
        return true;
    }

    void closeItems() {
        if (phase == Phase.ITEM) phase = Phase.MENU;
    }

    /** Милость. Возвращает true и заканчивает бой, если Довольство полное. */
    boolean mercy() {
        requireMenu();
        if (satisfy >= SATISFY_MAX) {
            // милость: сначала финальный залп «Тёмного бака», потом отпускает
            finaleOutcome = Phase.SPARED;
            phase = Phase.ARENA;
            line = Phrases.SPARED;
            return true;
        }
        line = Phrases.pick(Phrases.MERCY_FAIL, rng);
        return false;
    }

    /** Конец текста Джанклима: либо победа (HP 0), либо арена. Возвращает тип атаки или null. */
    Pattern startArena() {
        if (phase != Phase.JUNK_TALK) throw new IllegalStateException("не фаза JUNK_TALK: " + phase);
        phase = Phase.ARENA;
        if (junkHp <= 0) {
            // геноцид: сначала финальный залп «Тёмного бака», потом победа
            finaleOutcome = Phase.WON;
            return Pattern.NORMAL;
        }
        Pattern p = nextPattern;
        nextPattern = Pattern.NORMAL;
        return p;
    }

    /** Исход, который наступит после финального залпа «Тёмного бака» (WON или SPARED), иначе null. */
    private Phase finaleOutcome;

    /** Идёт финальный залп перед концовкой. */
    boolean isFinale() {
        return finaleOutcome != null;
    }

    /** Попадание на арене: урон игроку применяется сразу, в момент касания. */
    void arenaHit() {
        if (phase != Phase.ARENA) throw new IllegalStateException("не фаза ARENA: " + phase);
        playerHp = Math.max(0, playerHp - HIT_DAMAGE);
    }

    /** Конец арены: поражение, если HP 0, иначе следующий ход. */
    void endArena() {
        if (phase != Phase.ARENA) throw new IllegalStateException("не фаза ARENA: " + phase);
        if (playerHp <= 0) {
            phase = Phase.LOST;
            line = Phrases.LOST;
            finaleOutcome = null;
            return;
        }
        if (finaleOutcome != null) {
            phase = finaleOutcome;
            finaleOutcome = null;
            line = phase == Phase.WON ? Phrases.WON : Phrases.SPARED;
            return;
        }
        turn++;
        phase = Phase.MENU;
        newSignal();
    }

    /** Тайминг-удар и арена в общем коде не используют состояние меню. */
    private void requireMenu() {
        if (phase != Phase.MENU) throw new IllegalStateException("не меню: " + phase);
    }
}
