package com.example.junkil;

import java.util.Arrays;
import java.util.List;

/** Проверки интерфейса: касания, переходы экранов, тайминг, арена, джойстик. */
public final class UiCheck {

    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("ПРОВАЛ: " + name);
        }
    }

    static void eq(String name, Object expected, Object actual) {
        check(name + " (ожидалось " + expected + ", получено " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }

    public static void main(String[] args) {
        menuFlow();
        morseFlow();
        fightFlow();
        arenaFlow();
        endings();
        System.out.println("Интерфейс: пройдено " + passed + ", провалено " + failed);
        if (failed > 0) System.exit(1);
    }

    static void menuFlow() {
        Env e = new Env(21);
        eq("старт: фаза MENU", BattleLogic.Phase.MENU, e.logic().phase);
        eq("старт: кнопки меню", Arrays.asList("FIGHT", "ACT", "ITEM", "MERCY"), e.view.buttonIdsForTest());
        eq("старт: вступление", Phrases.INTRO, e.logic().line);
        e.tapButton("ACT");
        eq("ДЕЙСТВИЕ открывает подменю", Arrays.asList("LOOK", "MORSE", "HAGGLE", "BACK"), e.view.buttonIdsForTest());
        e.tapButton("BACK");
        eq("НАЗАД возвращает меню", Arrays.asList("FIGHT", "ACT", "ITEM", "MERCY"), e.view.buttonIdsForTest());
        e.tapButton("ITEM");
        eq("ПРЕДМЕТ: батарейка и назад", Arrays.asList("BATTERY", "BACK"), e.view.buttonIdsForTest());
        e.tapButton("BATTERY");
        eq("батарейка тратится", 1, e.logic().batteries);
        eq("батарейка лечит", 20, e.logic().playerHp);
        eq("после батарейки фаза JUNK_TALK", BattleLogic.Phase.JUNK_TALK, e.logic().phase);
        e.tapLogical(180, 400);
        eq("тап по тексту продолжает к арене", BattleLogic.Phase.ARENA, e.logic().phase);
        check("арена создана", e.view.sim != null);
        e.advance(11f);
        check("арена закончилась по таймеру", e.logic().phase != BattleLogic.Phase.ARENA);
    }

    static void morseFlow() {
        Env e = new Env(22);
        e.tapButton("ACT");
        e.tapButton("MORSE");
        eq("СИГНАЛ переводит в MORSE", BattleLogic.Phase.MORSE, e.logic().phase);
        eq("три варианта слов", Arrays.asList("OPT0", "OPT1", "OPT2"), e.view.buttonIdsForTest());
        int wrong = (e.logic().morseCorrect + 1) % 3;
        e.tapButton("OPT" + wrong);
        eq("неверный ответ не даёт довольства", 0, e.logic().satisfy);
        eq("неверный: следующая атака BOOSTED", BattleLogic.Pattern.BOOSTED, e.logic().nextPattern);
        eq("после ответа JUNK_TALK", BattleLogic.Phase.JUNK_TALK, e.logic().phase);
        eq("Джанкил злится", BattleLogic.Mood.ANGRY, e.logic().mood());

        Env ok = new Env(23);
        ok.tapButton("ACT");
        ok.tapButton("MORSE");
        ok.tapButton("OPT" + ok.logic().morseCorrect);
        eq("верный ответ: +25", 25, ok.logic().satisfy);
        eq("верный ответ: атака REDUCED", BattleLogic.Pattern.REDUCED, ok.logic().nextPattern);
    }

    static void fightFlow() {
        Env e = new Env(24);
        e.tapButton("FIGHT");
        eq("БОЙ: фаза FIGHT", BattleLogic.Phase.FIGHT, e.logic().phase);
        e.advance(0.5555f);  // маркер почти в центре
        int before = e.logic().junkHp;
        e.tapLogical(180, 384);
        eq("попадание сразу уменьшает ХП Джанкила", true, e.logic().junkHp < before);
        int acc = e.view.lastAccuracyPctForTest();
        check("точность записана (" + acc + "%)", acc >= 0 && acc <= 100);
        eq("после удара JUNK_TALK", BattleLogic.Phase.JUNK_TALK, e.logic().phase);

        Env miss = new Env(25);
        miss.tapButton("FIGHT");
        miss.advance(0.0f);       // маркер у левого края
        miss.tapLogical(40, 384);
        eq("промах по краю не наносит урона", 90, miss.logic().junkHp);
    }

    static void arenaFlow() {
        Env e = new Env(26);
        e.logic().satisfy = 0;
        e.tapButton("FIGHT");
        e.advance(0.5555f);
        e.tapLogical(180, 384);
        e.tapLogical(180, 400);
        eq("арена после текста", BattleLogic.Phase.ARENA, e.logic().phase);
        e.advance(1.5f);
        check("на арене есть предметы", e.view.sim.items.size() > 0 || e.view.sim.spawned > 0);

        // Сердце двигается по джойстику вправо
        Env j = new Env(27);
        j.tapButton("FIGHT");
        j.advance(0.5555f);
        j.tapLogical(180, 384);
        j.tapLogical(180, 400);
        float hx0 = j.view.sim.heartX;
        j.view.onTouchEvent(android.view.MotionEvent.obtain(android.view.MotionEvent.ACTION_DOWN,
                j.pxX(BattleView.JOY_X), j.pxY(BattleView.JOY_Y)));
        j.view.onTouchEvent(android.view.MotionEvent.obtain(android.view.MotionEvent.ACTION_MOVE,
                j.pxX(BattleView.JOY_X + 25), j.pxY(BattleView.JOY_Y)));
        j.advance(0.5f);
        check("тяга джойстика вправо сдвигает сердце вправо", j.view.sim.heartX > hx0);
        j.view.onTouchEvent(android.view.MotionEvent.obtain(android.view.MotionEvent.ACTION_UP,
                j.pxX(BattleView.JOY_X + 25), j.pxY(BattleView.JOY_Y)));
        eq("отпускание сбрасывает стик", 0f, j.view.sim.stickX);
    }

    static void endings() {
        Env w = new Env(28);
        w.logic().junkHp = 4;
        w.tapButton("FIGHT");
        w.advance(0.5555f);
        w.tapLogical(180, 384);
        w.tapLogical(180, 400);
        eq("ХП Джанкила 0 — победа", BattleLogic.Phase.WON, w.logic().phase);
        check("победа — конец", w.logic().isOver());
        w.tapLogical(180, 300);
        eq("тап после конца — новая игра", BattleLogic.Phase.MENU, w.logic().phase);
        eq("новая игра: ход 1", 1, w.logic().turn);

        Env s = new Env(29);
        s.logic().satisfy = 100;
        s.tapButton("MERCY");
        eq("МИЛОСТЬ при 100 — SPARED", BattleLogic.Phase.SPARED, s.logic().phase);
        eq("мирный конец — спрайт обычный", BattleLogic.Mood.NORMAL, s.logic().mood());

        Env l = new Env(30);
        l.logic().playerHp = 2;
        l.tapButton("FIGHT");
        l.advance(0.5555f);
        l.tapLogical(180, 384);
        l.tapLogical(180, 400);
        l.advance(12f);
        eq("HP игрока 0 — поражение", BattleLogic.Phase.LOST, l.logic().phase);
    }
}
