package com.example.airhotkey;

import android.content.Context;
import android.graphics.Canvas;
import android.view.KeyEvent;

import java.util.Random;

/** Симулятор без устройства: игровое время, бот, отрисовка каждого кадра. Падает при любом исключении. */
public final class WorldSim {
    private static int failed = 0;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  OK   " : "  FAIL ") + what);
        if (!ok) failed++;
    }

    private static final float DT = 1f / 60f;

    /** Один кадр: логика + отрисовка, как в GameView. */
    private static void frame(World w, Canvas c) {
        w.update(DT);
        w.draw(c);
    }

    public static void main(String[] args) {
        Canvas c = new Canvas();
        if (args.length > 0 && args[0].equals("--shots")) {
            Shots.run(args.length > 1 ? args[1] : "build/screens");
            return;
        }
        Context ctx = new Context();

        System.out.println("1. Меню и старт");
        World w = new World(ctx);
        w.layout(1080, 2400);
        for (int i = 0; i < 120; i++) frame(w, c);
        check(w.state == World.MENU, "в меню держит фон и демо-клавиши");
        check(w.orbs.isEmpty() || allDemo(w), "в меню только декоративные клавиши");
        w.tap(540, 1200);
        check(w.state == World.PLAY, "тап в меню запускает игру");
        check(w.lives == 3 && w.score == 0, "старт: 3 жизни, 0 очков");
        check(w.chain.length >= 3, "цепочка из 3+ клавиш");

        System.out.println("2. Бот играет 3 минуты игрового времени");
        Random rnd = new Random(7);
        int hits = 0, keyHits = 0, minLives = 3, maxLevel = 1;
        int frames = 60 * 180;
        for (int i = 0; i < frames && w.state != World.OVER; i++) {
            frame(w, c);
            maxLevel = Math.max(maxLevel, w.level);
            minLives = Math.min(minLives, w.lives);
            if (w.state == World.PLAY && rnd.nextFloat() < 0.12f) {
                char need = w.needed();
                World.Orb best = null;
                for (World.Orb o : w.orbs) {
                    if (o.demo || o.ch != need) continue;
                    if (best == null || o.y < best.y) best = o;
                }
                if (best != null && best.y > 0) {
                    if (rnd.nextBoolean()) {
                        w.tap(best.x, best.y);
                        hits++;
                    } else {
                        int code = need >= 'A' && need <= 'Z' ? KeyEvent.KEYCODE_A + (need - 'A')
                                : KeyEvent.KEYCODE_0 + (need - '0');
                        if (w.key(code)) keyHits++;
                    }
                }
            }
        }
        System.out.println("     очки=" + w.score + " уровень=" + maxLevel + " цепочек=" + w.chainsDone
                + " макс.комбо=" + w.maxCombo + " тапов=" + hits + " клавиш=" + keyHits + " мин.жизней=" + minLives);
        check(w.score > 100, "бот набирает очки");
        check(w.chainsDone >= 2, "бот проходит цепочки");
        check(maxLevel >= 2, "уровень растёт");
        check(w.maxCombo >= 3, "комбо работает");
        check(keyHits > 0, "горячие клавиши на клавиатуре работают");

        System.out.println("3. Без игрока — жизни заканчиваются, наступает конец игры");
        World idle = new World(ctx);
        idle.layout(1080, 2400);
        idle.tap(0, 0);
        int used = 0;
        while (idle.state != World.OVER && used < 60 * 600) {
            frame(idle, c);
            used++;
        }
        check(idle.state == World.OVER, "игра завершилась после пропусков (" + used / 60 + " с)");
        check(idle.lives == 0, "жизни = 0");
        check(idle.score >= 0, "очки не отрицательные");
        frame(idle, c);
        idle.tap(540, 1200);
        check(idle.state == World.OVER, "тап сразу после конца не перезапускает (защита от случайного тапа)");
        for (int i = 0; i < 60 * 2; i++) frame(idle, c);
        idle.tap(540, 1200);
        check(idle.state == World.PLAY, "тап через секунду — новая игра");

        System.out.println("4. Промах и пауза");
        World m = new World(ctx);
        m.layout(1080, 2400);
        m.tap(0, 0);
        World.Orb wrong = null;
        for (int i = 0; i < 60 * 20 && wrong == null; i++) {
            frame(m, c);
            char need = m.needed();
            for (World.Orb o : m.orbs) if (!o.demo && o.ch != need && o.y > 0 && o.y < 2000) { wrong = o; break; }
        }
        m.combo = 5;   // комбо есть до промаха
        if (wrong != null) {
            m.tap(wrong.x, wrong.y);
            check(m.combo == 0 && m.score >= 0, "промах сбрасывает комбо, очки не уходят в минус");
            check(m.state == World.PLAY, "промах не заканчивает игру");
        } else {
            System.out.println("  (пропуск промаха: нет чужой клавиши на экране)");
        }
        m.tap(1080 - 34 * (1080f / 360f) / 1f * 0 + 1080 - 34f * 3, 42f * 3);
        check(m.state == World.PAUSE || m.state == World.PLAY, "тап по паузе не падает");
        if (m.state != World.PAUSE) {
            // кнопка паузы — в координатах масштаба s = min(w,h)/360 = 3
            m.tap(1080 - 34 * 3, 42 * 3);
        }
        check(m.state == World.PAUSE, "кнопка паузы ставит игру на паузу");
        check(m.back() && m.state == World.MENU, "«Назад» из паузы — в меню");

        System.out.println("5. Другие размеры экрана и ландшафт");
        for (int[] sz : new int[][]{{320, 480}, {1440, 3200}, {2400, 1080}, {720, 1280}}) {
            World r = new World(ctx);
            r.layout(sz[0], sz[1]);
            r.tap(0, 0);
            for (int i = 0; i < 60 * 20; i++) frame(r, c);
            check(true, "экран " + sz[0] + "x" + sz[1] + " — 20 с без исключений");
        }

        System.out.println(failed == 0 ? "ИТОГ: все проверки пройдены" : "ИТОГ: провалено " + failed);
        if (failed > 0) System.exit(1);
    }

    private static boolean allDemo(World w) {
        for (World.Orb o : w.orbs) if (!o.demo) return false;
        return true;
    }
}
