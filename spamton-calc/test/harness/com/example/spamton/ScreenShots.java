package com.example.spamton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/** Снимает экраны приложения в PNG (docs/screens/). Запуск: scripts/screenshots.sh */
public final class ScreenShots {

    /** Пишет журнал команд рисования; PNG строит scripts/render_ops.py. */
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

        Env start = new Env(11, false);
        start.settle();
        save(start.shot(), dir, "01-calc-start.png");

        Env wrong = new Env(12, false);
        wrong.settle();
        wrong.type("7+5×3=");
        wrong.settle();
        save(wrong.shot(), dir, "02-calc-answer.png");

        Env ad = new Env(13, false);
        ad.settle();
        ad.view.openPopupForTest();
        ad.advance(0.5f);
        save(ad.shot(), dir, "03-popup.png");

        Env take = new Env(14, false);
        take.settle();
        take.view.forceTakeover();
        take.advance(1.2f);
        save(take.shot(), dir, "04-takeover.png");

        Env quiz = new Env(15, false);
        quiz.settle();
        quiz.view.forceTakeover();
        quiz.advance(3f);
        quiz.settle();
        save(quiz.shot(), dir, "05-battle-quiz.png");

        Env attack = new Env(16, false);
        attack.settle();
        attack.view.forceTakeover();
        attack.advance(3f);
        attack.settle();
        int idx = attack.view.battleForTest().question().correctIndex;
        float[] a = attack.view.answerCenterForTest(idx);
        attack.tap(a[0], a[1]);
        attack.advance(3.5f);
        save(attack.shot(), dir, "06-battle-attack.png");

        Env ally = new Env(17, true);
        ally.settle();
        ally.type("12×4=");
        ally.settle();
        save(ally.shot(), dir, "07-ally-calc.png");
    }
}
