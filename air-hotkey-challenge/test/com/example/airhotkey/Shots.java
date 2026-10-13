package com.example.airhotkey;

import android.content.Context;
import android.graphics.Canvas;

import java.io.File;
import java.io.PrintWriter;
import java.util.Random;

/** Снимки экранов игры в PNG (для ручной проверки визуала без устройства). */
final class Shots {
    private static final int W = 720, H = 1280;
    private static final float DT = 1f / 60f;

    static void run(String dir) {
        try {
            new File(dir).mkdirs();
            Canvas c = new Canvas();
            Context ctx = new Context();
            Random rnd = new Random(11);
            Canvas.ops.clear();

            World w = new World(ctx);
            w.layout(W, H);
            for (int i = 0; i < 180; i++) { w.update(DT); }
            Canvas.ops.clear();
            w.draw(c);
            save(dir, "01-menu.png");

            w.tap(0, 0);
            Bot bot = new Bot(w, rnd);
            for (int i = 0; i < 60 * 6; i++) { bot.step(); w.update(DT); }
            w.draw(c);
            save(dir, "02-play.png");

            // кадр сразу после успешного тапа — вспышки, кольца, всплывающий текст
            int before = w.score;
            for (int i = 0; i < 60 * 60 && w.score == before; i++) { bot.step(); w.update(DT); }
            for (int i = 0; i < 6; i++) w.update(DT);
            w.draw(c);
            save(dir, "03-hit.png");

            // цепочка: тап по последней клавише — вспышка и бонус
            int chains = w.chainsDone;
            for (int i = 0; i < 60 * 90 && w.chainsDone == chains; i++) { bot.step(); w.update(DT); }
            for (int i = 0; i < 12; i++) w.update(DT);
            w.draw(c);
            save(dir, "04-chain.png");

            w.back();
            w.draw(c);
            save(dir, "05-pause.png");

            w.back();
            w.tap(0, 0);
            Bot idle = new Bot(w, rnd);
            for (int i = 0; i < 60 * 120 && w.state != World.OVER; i++) w.update(DT);
            for (int i = 0; i < 60 * 2; i++) w.update(DT);
            w.draw(c);
            save(dir, "06-over.png");
            System.out.println("снимки сохранены в " + new File(dir).getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Пишет накопленные команды кадра в build/screens/NAME.txt; PNG рисует tools/render_frames.py. */
    private static void save(String dir, String name) throws Exception {
        PrintWriter pw = new PrintWriter(new File(dir, name.replace(".png", ".txt")), "UTF-8");
        for (String op : Canvas.ops) pw.println(op);
        pw.close();
        Canvas.ops.clear();
        System.out.println("  " + name.replace(".png", ".txt"));
    }

    /** Простой игрок: иногда тапает нужную клавишу, иногда жмёт её на клавиатуре. */
    static final class Bot {
        private final World w;
        private final Random rnd;

        Bot(World w, Random rnd) {
            this.w = w;
            this.rnd = rnd;
        }

        void step() {
            if (w.state != World.PLAY || rnd.nextFloat() > 0.1f) return;
            char need = w.needed();
            World.Orb best = null;
            for (World.Orb o : w.orbs) {
                if (o.demo || o.ch != need) continue;
                if (best == null || o.y < best.y) best = o;
            }
            if (best != null && best.y > 0) w.tap(best.x, best.y);
        }
    }
}
