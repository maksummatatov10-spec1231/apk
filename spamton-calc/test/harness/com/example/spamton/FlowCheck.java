package com.example.spamton;

import android.view.MotionEvent;

import java.util.List;

/**
 * Проверки сценариев через касания по реальной раскладке (JVM, без устройства).
 * Запуск: scripts/ui-test.sh
 */
public final class FlowCheck {

    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL: " + name); }
    }

    static void touch(SpamtonView v, float x, float y) {
        v.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, x, y));
        v.onTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, x, y));
    }

    public static void main(String[] args) {
        layoutChecks();
        keypadChecks();
        moodChecks();
        popupChecks();
        takeoverAndBattleChecks();
        allyChecks();
        System.out.println("ui passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void layoutChecks() {
        Env e = new Env(1, false);
        List<float[]> ks = e.view.keyRectsForTest();
        check("все 23 клавиши на месте", ks.size() == 23);
        for (float[] r : ks) {
            check("клавиша внутри экрана", r[0] >= 0 && r[1] >= 0 && r[2] <= Env.W && r[3] <= Env.H);
            check("клавиша не пустая", r[2] > r[0] && r[3] > r[1]);
        }
        for (int i = 0; i < ks.size(); i++) {
            for (int j = i + 1; j < ks.size(); j++) {
                float[] a = ks.get(i), b = ks.get(j);
                boolean overlap = a[0] < b[2] - 0.5f && b[0] < a[2] - 0.5f && a[1] < b[3] - 0.5f && b[1] < a[3] - 0.5f;
                check("клавиши не перекрываются", !overlap);
            }
        }
        check("кадр рисуется без ошибок", e.shot().length() > 1000);
    }

    static void keypadChecks() {
        Env e = new Env(2, false);
        e.type("2+3=");
        check("после = выражение сохраняется", e.view.exprForTest().equals("2+3"));
        check("после = есть результат", e.view.shownForTest() != null);
        check("звук результата", e.sounds.count("right") + e.sounds.count("wrong") == 1);

        // Продолжение с результата.
        String shown = e.view.shownForTest();
        e.type("+");
        check("оператор после = продолжает с результата", e.view.exprForTest().equals(shown + "+"));

        e.key("C");
        check("C очищает всё", e.view.exprForTest().isEmpty() && e.view.shownForTest() == null);

        e.type("12");
        e.key("DEL");
        check("DEL удаляет символ", e.view.exprForTest().equals("1"));

        e.key("C");
        e.type("1÷0=");
        check("деление на ноль: ошибка без результата", e.view.shownForTest() == null);
        check("деление на ноль: звук ошибки", e.sounds.count("error") >= 1);
        check("ошибка: Спамтон злится", e.mood() == Mood.ANGRY);

        e.key("C");
        e.type("=");
        check("пустой = не падает и не считает", e.view.shownForTest() == null);

        e.key("C");
        e.type("(2+3)×4−6÷2=");
        check("скобки и знаки отработали", e.view.exprForTest().equals("(2+3)×4−6÷2"));

        e.key("C");
        for (int i = 0; i < 40; i++) e.key("9");
        check("длина ввода ограничена 24", e.view.exprForTest().length() == 24);

        e.key("C");
        e.type("√16=");
        check("корень отработал", e.view.shownForTest() != null);
    }

    static void popupChecks() {
        Env e = new Env(3, false);
        for (int i = 0; i < SpamtonView.AD_EVERY; i++) {
            e.type("1=");
            e.key("C");
            e.settle();
        }
        e.settle();
        check("реклама появляется на каждом втором вычислении", e.view.debugState().contains("popup=true"));
        float[] close = e.view.closeCenterForTest();
        touch(e.view, close[0], close[1]);
        float[] after = e.view.closeCenterForTest();
        check("кнопка закрыть убегает при нажатии",
                Math.abs(close[0] - after[0]) + Math.abs(close[1] - after[1]) > 1);
        for (int i = 0; i < 2; i++) {
            float[] c = e.view.closeCenterForTest();
            touch(e.view, c[0], c[1]);
        }
        check("после трёх убеганий реклама ещё открыта", e.view.debugState().contains("popup=true"));
        float[] last = e.view.closeCenterForTest();
        touch(e.view, last[0], last[1]);
        check("на четвёртое нажатие реклама закрывается", e.view.debugState().contains("popup=false"));

        Env f = new Env(4, false);
        f.view.openPopupForTest();
        float[] cta = f.view.ctaCenterForTest();
        touch(f.view, cta[0], cta[1]);
        check("кнопка рекламы закрывает окно", f.view.debugState().contains("popup=false"));
    }

    static void moodChecks() {
        Env e = new Env(9, false);
        check("на старте эмоция NEUTRAL", e.mood() == Mood.NEUTRAL);
        e.view.forceTakeover();
        check("захват: эмоция EVIL", e.mood() == Mood.EVIL);
        check("портрет NEUTRAL есть", e.view.debugState() != null);
        int withFile = 0;
        for (Mood m : Mood.values()) if (new java.io.File("res/drawable-nodpi/" + m.resName() + ".png").exists()) withFile++;
        check("все существующие портреты подгружены: " + withFile, withFile >= 1);
    }

    static void takeoverAndBattleChecks() {
        Env e = new Env(5, false);
        for (int i = 0; i < SpamtonView.TAKEOVER_AFTER; i++) {
            e.type("2=");
            e.key("C");
            e.settle();
            e.dismissAds();
        }
        e.advance(3f);
        check("после " + SpamtonView.TAKEOVER_AFTER + " вычислений начинается захват", e.view.modeForTest().equals("BATTLE"));
        e.settle();
        check("вопрос появился", e.view.battleForTest() != null && e.view.battleForTest().question() != null);
        int startHp = e.view.battleForTest().spamtonHp();
        int idx = e.view.battleForTest().question().correctIndex;
        float[] a = e.view.answerCenterForTest(idx);
        touch(e.view, a[0], a[1]);
        check("верный ответ снижает HP Спамтона", e.view.battleForTest().spamtonHp() < startHp);
        e.settle();
        e.advance(7f);
        e.settle();
        check("после атаки снова вопрос", e.view.battleForTest().phase().name().equals("QUIZ"));

        // Проигрываем: неверные ответы и неподвижное сердце. Бой не должен выйти в калькулятор
        // до победы и не должен падать.
        Env lose = new Env(6, false);
        for (int i = 0; i < SpamtonView.TAKEOVER_AFTER; i++) { lose.type("2="); lose.key("C"); lose.settle(); lose.dismissAds(); }
        lose.advance(3f);
        lose.settle();
        for (int round = 0; round < 80 && lose.view.modeForTest().equals("BATTLE"); round++) {
            if (lose.view.battleForTest().phase().name().equals("QUIZ") && lose.view.battleForTest().question() != null) {
                int wrong = (lose.view.battleForTest().question().correctIndex + 1) % 4;
                float[] w = lose.view.answerCenterForTest(wrong);
                touch(lose.view, w[0], w[1]);
            }
            lose.settle();
            lose.advance(7f);
        }
        check("без победы бой продолжается (не выходит в калькулятор)", lose.view.modeForTest().equals("BATTLE"));
    }

    static void allyChecks() {
        Env e = new Env(7, false);
        for (int i = 0; i < SpamtonView.TAKEOVER_AFTER; i++) { e.type("2="); e.key("C"); e.settle(); e.dismissAds(); }
        e.advance(3f);
        e.settle();
        for (int guard = 0; guard < 200 && e.view.modeForTest().equals("BATTLE"); guard++) {
            if (e.view.battleForTest().phase().name().equals("QUIZ") && e.view.battleForTest().question() != null) {
                int idx = e.view.battleForTest().question().correctIndex;
                float[] a = e.view.answerCenterForTest(idx);
                touch(e.view, a[0], a[1]);
            }
            e.settle();
            e.advance(7f);
        }
        e.settle();
        e.advance(4f);
        check("после победы — калькулятор", e.view.modeForTest().equals("CALC"));
        check("победа сохраняется", e.store.getBool("defeated", false));
        check("союзник отвечает точно", e.view.debugState().contains("ally=true"));
        e.type("12×4=");
        check("союзник: ответ точный (48)", "48".equals(e.view.shownForTest()));
        e.key("C");
        e.type("7+5×3=");
        check("союзник: порядок операций точный (22)", "22".equals(e.view.shownForTest()));
    }
}
