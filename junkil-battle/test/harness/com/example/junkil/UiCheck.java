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
        joystickMenu();
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
        e.tapLogical(400, 400);
        eq("тап по тексту продолжает к арене", BattleLogic.Phase.ARENA, e.logic().phase);
        check("арена создана", e.view.sim != null);
        e.advance(11f);
        check("арена закончилась по таймеру", e.logic().phase != BattleLogic.Phase.ARENA);
    }

    /** Undertale: левый джойстик выбирает пункт, Z подтверждает, X возвращает назад. */
    static void joystickMenu() {
        Env e = new Env(31);
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X + 60, BattleView.JOY_Y);
        e.joyRelease();
        eq("джойстик вправо: курсор на ДЕЙСТВИЕ", 1, e.view.cursorForTest());
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X + 60, BattleView.JOY_Y);
        e.joyRelease();
        eq("второй шаг вправо: курсор на ПРЕДМЕТ", 2, e.view.cursorForTest());
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X - 60, BattleView.JOY_Y);
        e.joyRelease();
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X - 60, BattleView.JOY_Y);
        e.joyRelease();
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X - 60, BattleView.JOY_Y);
        e.joyRelease();
        eq("влево за границу: курсор не уходит ниже 0", 0, e.view.cursorForTest());
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X + 60, BattleView.JOY_Y);
        e.joyRelease();
        e.tapZ();
        eq("Z на ДЕЙСТВИИ открывает подменю", "ACT", e.view.subForTest());
        eq("в подменю курсор сбрасывается", 0, e.view.cursorForTest());
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X, BattleView.JOY_Y + 60);
        e.joyRelease();
        eq("вниз в подменю: курсор 1 (СИГНАЛ)", 1, e.view.cursorForTest());
        e.tapZ();
        eq("Z на СИГНАЛе переводит в MORSE", BattleLogic.Phase.MORSE, e.logic().phase);
        check("морзе-тоны для слова играют при входе в сигнал",
                e.sounds.count("morse_" + e.logic().morseWord.toLowerCase()) == 1);
        e.joyDrag(BattleView.JOY_X, BattleView.JOY_Y, BattleView.JOY_X, BattleView.JOY_Y + 60);
        e.joyRelease();
        eq("в MORSE вниз: курсор 1", 1, e.view.cursorForTest());
        e.tapZ();
        check("Z в MORSE отвечает на вариант", e.logic().phase == BattleLogic.Phase.JUNK_TALK);

        Env x = new Env(32);
        x.tapButton("ACT");
        x.tapLogical(BattleView.X_X, BattleView.X_Y);
        eq("X возвращает из подменю", "", x.view.subForTest());
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
        e.tapLogical(400, 384);
        eq("попадание сразу уменьшает ХП Джанкила", true, e.logic().junkHp < before);
        int acc = e.view.lastAccuracyPctForTest();
        check("точность записана (" + acc + "%)", acc >= 0 && acc <= 100);
        eq("после удара JUNK_TALK", BattleLogic.Phase.JUNK_TALK, e.logic().phase);

        Env miss = new Env(25);
        miss.tapButton("FIGHT");
        miss.advance(0.0f);       // маркер у левого края
        miss.tapLogical(400, 384);
        eq("промах по краю не наносит урона", 90, miss.logic().junkHp);
    }

    static void arenaFlow() {
        Env e = new Env(26);
        e.logic().satisfy = 0;
        e.tapButton("FIGHT");
        e.advance(0.5555f);
        e.tapLogical(400, 384);
        e.tapLogical(400, 400);
        eq("арена после текста", BattleLogic.Phase.ARENA, e.logic().phase);
        e.advance(1.5f);
        check("на арене есть предметы", e.view.sim.items.size() > 0 || e.view.sim.spawned > 0);
        // регрессия: обрезка арены должна быть в тех же координатах, что и фигуры (иначе снаряды невидимы)
        boolean clipFound = false;
        for (String line : e.shot().split("\n")) {
            if (!line.startsWith("C ")) continue;
            String[] p = line.split(" ");
            float l = Float.parseFloat(p[1]), t = Float.parseFloat(p[2]);
            float r = Float.parseFloat(p[3]), b = Float.parseFloat(p[4]);
            clipFound = true;
            check("обрезка арены шире 20% экрана (" + (r - l) + " px)", r - l > 0.2f * Env.W);
            check("обрезка арены внутри экрана", l > 0 && t > 0 && r < Env.W && b < Env.H);
        }
        check("обрезка арены есть в кадре", clipFound);
        check("звук атаки (whoosh) играл", e.sounds.count("whoosh") > 0);

        // Урон сразу: фигура попадает в сердце — ХП игрока меняется в тот же кадр, арена не кончается
        Env hit = new Env(33);
        hit.tapButton("FIGHT");
        hit.advance(0.5555f);
        hit.tapLogical(400, 384);
        hit.tapLogical(400, 400);
        int hp0 = hit.logic().playerHp;
        float hx = hit.view.sim.heartX, hy = hit.view.sim.heartY;
        ArenaSim.Item fake = new ArenaSim.Item(ArenaSim.Kind.COIN, 0, hx, hy, 0f, 0f, 6f);
        hit.view.sim.items.add(fake);
        hit.advance(0.02f);
        eq("попадание: ХП игрока уменьшилось сразу", hp0 - BattleLogic.HIT_DAMAGE, hit.logic().playerHp);
        eq("попадание: фаза арены не закончилась", BattleLogic.Phase.ARENA, hit.logic().phase);
        check("звук удара (hurt) играл", hit.sounds.count("hurt") > 0);

        // Сердце двигается по джойстику вправо
        Env j = new Env(27);
        j.tapButton("FIGHT");
        j.advance(0.5555f);
        j.tapLogical(400, 384);
        j.tapLogical(400, 400);
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
        w.tapLogical(400, 384);
        w.tapLogical(400, 400);
        eq("ХП Джанкила 0 — победа", BattleLogic.Phase.WON, w.logic().phase);
        check("победа — конец", w.logic().isOver());
        w.tapLogical(400, 300);
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
        l.tapLogical(400, 384);
        l.tapLogical(400, 400);
        l.advance(12f);
        eq("HP игрока 0 — поражение", BattleLogic.Phase.LOST, l.logic().phase);
    }
}
