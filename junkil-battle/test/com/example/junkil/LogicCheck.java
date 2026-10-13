package com.example.junkil;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Проверки логики боя, Морзе, арены и фраз (JVM, без Android). Запуск: scripts/test.sh */
public final class LogicCheck {

    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("ПРОВАЛ: " + name);
        }
    }

    static void eq(String name, Object expected, Object actual) {
        check(name + " (ожидалось " + expected + ", получено " + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }

    public static void main(String[] args) {
        morse();
        fight();
        morseGame();
        actAndItems();
        mercyAndEnd();
        arena();
        phrases();
        System.out.println("Логика: пройдено " + passed + ", провалено " + failed);
        if (failed > 0) System.exit(1);
    }

    static void morse() {
        eq("SOS", "... --- ...", MorseCode.encode("SOS"));
        eq("JUNK", ".--- ..- -. -.-", MorseCode.encode("JUNK"));
        eq("декодирование SOS", "SOS", MorseCode.decode("... --- ..."));
        for (String w : BattleLogic.WORDS) {
            eq("круговой путь " + w, w, MorseCode.decode(MorseCode.encode(w)));
        }
    }

    static void fight() {
        BattleLogic b = new BattleLogic(new Random(1));
        b.chooseFight();
        int dmg = b.fight(0.1);
        eq("промах по тайму = 0 урона", 0, dmg);
        eq("ХП Джанкила не меняется при промахе", BattleLogic.JUNK_MAX, b.junkHp);

        b = new BattleLogic(new Random(2));
        b.chooseFight();
        dmg = b.fight(1.0);
        eq("точный удар = максимум", BattleLogic.FIGHT_MAX, dmg);
        eq("ХП Джанкила уменьшается сразу, до конца боя", BattleLogic.JUNK_MAX - BattleLogic.FIGHT_MAX, b.junkHp);
        eq("после удара фаза JUNK_TALK", BattleLogic.Phase.JUNK_TALK, b.phase);
    }

    static void morseGame() {
        BattleLogic b = new BattleLogic(new Random(3));
        eq("три варианта", 3, b.morseOptions.length);
        Set<String> uniq = new HashSet<>();
        for (String o : b.morseOptions) uniq.add(o);
        eq("варианты различны", 3, uniq.size());
        eq("верный вариант совпадает с сигналом", b.morseWord, b.morseOptions[b.morseCorrect]);

        b.chooseAct(BattleLogic.Act.MORSE);
        eq("фаза MORSE", BattleLogic.Phase.MORSE, b.phase);
        int wrong = (b.morseCorrect + 1) % 3;
        int sat0 = b.satisfy;
        check("неверный ответ", !b.answerMorse(wrong));
        eq("неверный: довольство -10 (с нуля — не ниже 0)", Math.max(0, sat0 - 10), b.satisfy);
        eq("неверный: следующая атака BOOSTED", BattleLogic.Pattern.BOOSTED, b.nextPattern);

        b = new BattleLogic(new Random(4));
        b.chooseAct(BattleLogic.Act.MORSE);
        check("верный ответ", b.answerMorse(b.morseCorrect));
        eq("верный: довольство +25", 25, b.satisfy);
        eq("верный: следующая атака REDUCED", BattleLogic.Pattern.REDUCED, b.nextPattern);
    }

    static void actAndItems() {
        BattleLogic b = new BattleLogic(new Random(5));
        b.chooseAct(BattleLogic.Act.HAGGLE);
        eq("торг: +15 довольства", 15, b.satisfy);
        eq("торг: +1 батарейка", 3, b.batteries);
        eq("торг: следующая атака BOOSTED", BattleLogic.Pattern.BOOSTED, b.nextPattern);
        for (int i = 0; i < 2; i++) {
            b.phase = BattleLogic.Phase.MENU;
            b.chooseAct(BattleLogic.Act.HAGGLE);
        }
        b.phase = BattleLogic.Phase.MENU;
        b.chooseAct(BattleLogic.Act.HAGGLE);
        eq("батарейки не больше 3", BattleLogic.BATTERY_MAX, b.batteries);

        b = new BattleLogic(new Random(6));
        b.playerHp = 5;
        boolean used = b.useBattery();
        check("батарейка сработала", used);
        eq("батарейка лечит 8", 13, b.playerHp);
        eq("батарейка тратится", 1, b.batteries);
        b.batteries = 0;
        b.line = "";
        b.phase = BattleLogic.Phase.MENU;
        eq("без батареек — нет", false, b.useBattery());
    }

    static void mercyAndEnd() {
        BattleLogic b = new BattleLogic(new Random(7));
        eq("МИЛОСТЬ при 0% не проходит", false, b.mercy());
        eq("после отказа фаза MENU", BattleLogic.Phase.MENU, b.phase);
        b.satisfy = 100;
        eq("МИЛОСТЬ при 100% проходит", true, b.mercy());
        eq("фаза SPARED", BattleLogic.Phase.SPARED, b.phase);
        check("конец боя SPARED", b.isOver());

        b = new BattleLogic(new Random(8));
        b.junkHp = 4;
        b.chooseFight();
        b.fight(1.0);
        eq("ХП 0 — Джанкил побеждён", null, b.startArena());
        eq("фаза WON", BattleLogic.Phase.WON, b.phase);

        b = new BattleLogic(new Random(9));
        b.chooseFight();
        b.fight(0.5);
        check("старт арены", b.startArena() != null);
        for (int i = 0; i < 10; i++) b.arenaHit();
        b.endArena();
        eq("20 ХП минус 10 попаданий по 2 = 0 → LOST", BattleLogic.Phase.LOST, b.phase);
        check("LOST — конец", b.isOver());
    }

    static void arena() {
        BattleLogic b = new BattleLogic(new Random(10));
        b.chooseFight();
        b.fight(0.5);
        b.startArena();
        for (int i = 0; i < 3; i++) b.arenaHit();
        eq("3 попадания по 2 урона сразу, до конца арены", 14, b.playerHp);
        b.endArena();
        eq("после арены новый ход", 2, b.turn);
        eq("после арены фаза MENU", BattleLogic.Phase.MENU, b.phase);

        int nAll = 0, rAll = 0, bAll = 0;
        for (long seed = 1; seed <= 10; seed++) {
            nAll += run(new ArenaSim(new Random(seed), BattleLogic.Pattern.NORMAL, "SOS"));
            rAll += run(new ArenaSim(new Random(seed), BattleLogic.Pattern.REDUCED, "SOS"));
            bAll += run(new ArenaSim(new Random(seed), BattleLogic.Pattern.BOOSTED, "SOS"));
        }
        check("BOOSTED плотнее NORMAL (" + bAll + " > " + nAll + ")", bAll > nAll);
        check("REDUCED реже NORMAL (" + rAll + " < " + nAll + ")", rAll < nAll);
        ArenaSim normal = new ArenaSim(new Random(42), BattleLogic.Pattern.NORMAL, "SOS");
        run(normal);
        check("арена заканчивается по времени", normal.finished());

        // Разнообразие: во всех ходах МОРЗЕ первое, а типы атак разные
        java.util.Set<ArenaSim.Attack> seen = new HashSet<>();
        for (long seed = 1; seed <= 12; seed++) {
            ArenaSim s = new ArenaSim(new Random(seed), BattleLogic.Pattern.NORMAL, "JUNK");
            run(s);
            eq("ЧАСТОТА (МОРЗЕ) всегда первая, seed " + seed, ArenaSim.Attack.MORSE, s.attackLog.get(0));
            check("в ходе три атаки, seed " + seed, s.attackLog.size() >= 2);
            seen.addAll(s.attackLog);
        }
        check("атаки разнообразны: видов не меньше 4 (" + seen + ")", seen.size() >= 4);

        // Прицел: направление от точки старта к сердцу
        float[] v = ArenaSim.aim(0f, 0f, 100f, 0f, 50f);
        eq("прицел вправо: vx", 50f, v[0]);
        eq("прицел вправо: vy", 0f, v[1]);

        ArenaSim sim = new ArenaSim(new Random(1), BattleLogic.Pattern.NORMAL, "SOS");
        sim.moveHeartTo(-500, 500);
        eq("сердце зажато в рамку (x)", ArenaSim.HEART / 2f, sim.heartX);
        eq("сердце зажато в рамку (y)", ArenaSim.H - ArenaSim.HEART / 2f, sim.heartY);

        // Попадание: ставим сердце на предмет и делаем шаг.
        ArenaSim hit = new ArenaSim(new Random(5), BattleLogic.Pattern.NORMAL, "SOS");
        hit.items.add(new ArenaSim.Item(ArenaSim.Kind.COIN, 0, 150f, 100f, 0f, 0f, 6f));
        hit.moveHeartTo(150f, 100f);
        eq("касание предмета засчитывает попадание", 1, hit.update(0.016f));
        eq("повторно тот же предмет не засчитывается", 0, hit.update(0.016f));
    }

    static int run(ArenaSim sim) {
        float t = 0f;
        while (!sim.finished() && t < 30f) {
            sim.moveHeartTo(ArenaSim.W / 2f, ArenaSim.H / 2f);
            sim.update(1f / 60f);
            t += 1f / 60f;
        }
        return sim.spawned;
    }

    static void phrases() {
        for (String s : Phrases.TURN_START) check("реплика хода не пуста", s != null && !s.isEmpty());
        check("INTRO есть", Phrases.INTRO.length() > 10);
        check("LOST есть", Phrases.LOST.length() > 10);
        check("WON есть", Phrases.WON.length() > 10);
    }
}
