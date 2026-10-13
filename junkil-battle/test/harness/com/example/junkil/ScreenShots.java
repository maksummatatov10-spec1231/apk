package com.example.junkil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/** Снимает экраны в docs/screens/ (журнал команд → PNG через scripts/render_ops.py). */
public final class ScreenShots {

    static void save(String ops, String dir, String name) throws Exception {
        File f = new File(dir, name.replace(".png", ".ops"));
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            w.write(ops);
        }
        System.out.println("ops " + f.getPath());
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "docs/screens";
        new File(dir).mkdirs();

        Env menu = new Env(101);
        menu.advance(0.2f);
        save(menu.shot(), dir, "01-menu.png");

        Env act = new Env(102);
        act.tapButton("ACT");
        save(act.shot(), dir, "02-act.png");

        Env morse = new Env(103);
        morse.tapButton("ACT");
        morse.tapButton("MORSE");
        save(morse.shot(), dir, "03-morse.png");

        Env wrong = new Env(104);
        wrong.tapButton("ACT");
        wrong.tapButton("MORSE");
        wrong.tapButton("OPT" + ((wrong.logic().morseCorrect + 1) % 3));
        save(wrong.shot(), dir, "04-morse-wrong.png");

        Env fight = new Env(105);
        fight.tapButton("FIGHT");
        fight.advance(0.3f);
        save(fight.shot(), dir, "05-fight.png");

        Env hit = new Env(106);
        hit.tapButton("FIGHT");
        hit.advance(0.5555f);
        hit.tapLogical(180, 384);
        save(hit.shot(), dir, "06-fight-hit.png");

        Env arena = new Env(107);
        arena.tapButton("FIGHT");
        arena.advance(0.5555f);
        arena.tapLogical(180, 384);
        arena.tapLogical(180, 400);
        arena.advance(3.2f);
        save(arena.shot(), dir, "07-arena.png");

        Env won = new Env(108);
        won.logic().junkHp = 4;
        won.tapButton("FIGHT");
        won.advance(0.5555f);
        won.tapLogical(180, 384);
        won.tapLogical(180, 400);
        save(won.shot(), dir, "08-won.png");

        Env spared = new Env(109);
        spared.logic().satisfy = 100;
        spared.tapButton("MERCY");
        save(spared.shot(), dir, "09-spared.png");

        Env lost = new Env(110);
        lost.logic().playerHp = 2;
        lost.tapButton("FIGHT");
        lost.advance(0.5555f);
        lost.tapLogical(180, 384);
        lost.tapLogical(180, 400);
        lost.advance(12f);
        save(lost.shot(), dir, "10-lost.png");
    }
}
