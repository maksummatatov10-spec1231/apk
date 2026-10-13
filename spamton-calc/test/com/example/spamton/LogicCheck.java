package com.example.spamton;

import java.util.Random;

/** Проверки логики без Android: калькулятор, Спамтон, бой, пули. Запуск: scripts/test.sh */
public final class LogicCheck {

    private static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL: " + name); }
    }

    static void eq(String name, double expected, double actual) {
        check(name + " (ожидалось " + expected + ", получено " + actual + ")", Math.abs(expected - actual) < 1e-9);
    }

    static void throwsCalc(String name, String expr) {
        try {
            Calc.eval(expr);
            check(name + " — ожидалась ошибка для «" + expr + "»", false);
        } catch (Calc.CalcException e) {
            check(name, true);
        }
    }

    public static void main(String[] args) {
        calcTests();
        spamtonTests();
        battleTests();
        bulletTests();
        System.out.println("passed " + passed + ", failed " + failed);
        if (failed > 0) System.exit(1);
    }

    static void calcTests() {
        eq("сложение", 5, Calc.eval("2+3"));
        eq("приоритет", 14, Calc.eval("2+3×4"));
        eq("скобки", 20, Calc.eval("(2+3)×4"));
        eq("деление", 2.5, Calc.eval("5÷2"));
        eq("минус знаком U+2212", -1, Calc.eval("2−3"));
        eq("степень правоассоциативна", 512, Calc.eval("2^3^2"));
        eq("унарный минус", -4, Calc.eval("-2×2"));
        eq("процент", 0.5, Calc.eval("50%"));
        eq("корень", 12, Calc.eval("√144"));
        eq("корень из выражения", 5, Calc.eval("√(3+22)"));
        eq("незакрытые скобки", 6, Calc.eval("(2+2×2"));
        throwsCalc("деление на ноль", "1÷0");
        throwsCalc("корень из отрицательного", "√(-4)");
        throwsCalc("пусто", "   ");
        throwsCalc("оператор без операнда", "2+");
        throwsCalc("лишние символы", "2 3");

        check("format целого", Calc.format(42).equals("42"));
        check("format дроби", Calc.format(2.5).equals("2.5"));
        check("format 1/3", Calc.format(1.0 / 3).equals("0.3333333333"));
        check("format -0 → 0", Calc.format(-0.0).equals("0"));
        check("format большого", Calc.format(1e15).equals("1000000000000000"));
    }

    static void spamtonTests() {
        // До победы большинство ответов неверные.
        Spamton s = new Spamton(42L);
        int wrong = 0;
        int n = 2000;
        for (int i = 0; i < n; i++) {
            Spamton.Verdict v = s.answer(100);
            if (!v.correct) wrong++;
            check("неверный ответ действительно отличается", v.correct || Math.abs(v.shown - 100) > 1e-9);
        }
        double rate = wrong / (double) n;
        check("неверных ответов примерно 70%, получено " + rate, rate > 0.62 && rate < 0.78);

        // После победы — всегда точно.
        Spamton ally = new Spamton(new Random(7), false);
        ally.befriend();
        check("союзник", ally.isAlly());
        for (int i = 0; i < 200; i++) {
            Spamton.Verdict v = ally.answer(i * 3.5);
            check("союзник отвечает точно", v.correct && v.shown == i * 3.5);
        }
        // Побеждённый сохраняет флаг из хранилища.
        Spamton saved = new Spamton(new Random(1), true);
        check("сохранённый союзник", saved.isAlly());
    }

    static void battleTests() {
        Battle b = new Battle(new Random(3));
        check("старт: HP полные", b.spamtonHp() == Battle.SPAMTON_MAX_HP && b.playerHp() == Battle.PLAYER_MAX_HP);
        Battle.Question q = b.nextQuestion();
        check("вопрос из 4 вариантов", q.options.length == 4);
        check("вопросы без повторов", distinct(q.options));
        check("верный вариант на позиции correctIndex", q.options[q.correctIndex] == q.answer());
        check("ответ неотрицателен", q.answer() >= 0);

        // Вопросы в разных раундах корректны (сумма/произведение/корень).
        Battle r = new Battle(new Random(11));
        for (int i = 0; i < 300; i++) {
            Battle.Question qq = r.nextQuestion();
            check("вопрос раунда " + r.round() + " имеет уникальные варианты", distinct(qq.options));
            if (r.phase() == Battle.Phase.QUIZ) {
                // Отвечаем верно и неверно, чтобы цикл фаз работал.
                Battle.Outcome o = r.choose(qq.correctIndex);
                check("верный ответ наносит урон не меньше 8", o.damageToSpamton >= 8);
                if (r.phase() == Battle.Phase.ATTACK) r.finishAttack(0);
                if (r.phase() == Battle.Phase.WON) break;
            }
        }

        // Неверный ответ лечит Спамтона и обнуляет серию.
        Battle w = new Battle(new Random(5));
        Battle.Question first = w.nextQuestion();
        int wrongIdx = (first.correctIndex + 1) % 4;
        // На полном HP лечить нечего.
        Battle.Outcome o0 = w.choose(wrongIdx);
        check("на полном HP неверный ответ не лечит", o0.healedSpamton == 0 && !o0.correct);
        w.finishAttack(0);
        // Сначала верный ответ наносит урон, потом неверный лечит.
        Battle.Question qq2 = w.nextQuestion();
        Battle.Outcome hit = w.choose(qq2.correctIndex);
        check("верный ответ — урон", hit.damageToSpamton >= 8 && hit.correct);
        w.finishAttack(0);
        Battle.Question qq3 = w.nextQuestion();
        Battle.Outcome o1 = w.choose((qq3.correctIndex + 1) % 4);
        check("неверный ответ — не урон", !o1.correct && o1.damageToSpamton == 0);
        check("неверный ответ — лечение", o1.healedSpamton == Battle.HEAL_ON_WRONG);
        check("после неверного фаза атаки", w.phase() == Battle.Phase.ATTACK);
        check("HP Спамтона не выше максимума", w.spamtonHp() <= Battle.SPAMTON_MAX_HP);
        w.finishAttack(2);
        check("урон от пуль списывается", w.playerHp() == Battle.PLAYER_MAX_HP - 2 * Battle.DAMAGE_PER_BULLET);
        check("после атаки снова вопрос", w.phase() == Battle.Phase.QUIZ);

        // Серия верных ответов: урон растёт, но не больше 16.
        Battle c = new Battle(new Random(9));
        int lastDmg = 0;
        for (int i = 0; i < 3; i++) {
            Battle.Question qq = c.nextQuestion();
            Battle.Outcome out = c.choose(qq.correctIndex);
            if (c.phase() == Battle.Phase.ATTACK) c.finishAttack(0);
            if (i > 0) check("урон растёт с серией", out.damageToSpamton > lastDmg);
            check("урон не выше 16", out.damageToSpamton <= 16);
            lastDmg = out.damageToSpamton;
        }

        // Полная победа: Спамтон умирает, фаза WON.
        Battle win = new Battle(new Random(2));
        boolean won = false;
        for (int i = 0; i < 50 && !won; i++) {
            Battle.Question qq = win.nextQuestion();
            Battle.Outcome out = win.choose(qq.correctIndex);
            if (win.phase() == Battle.Phase.WON) { won = true; break; }
            win.finishAttack(0);
        }
        check("верные ответы в итоге побеждают Спамтона", won && win.spamtonHp() == 0);

        // Поражение: HP игрока до нуля — фаза LOST.
        Battle lose = new Battle(new Random(4));
        for (int i = 0; i < 100 && lose.phase() != Battle.Phase.LOST; i++) {
            if (lose.phase() == Battle.Phase.QUIZ) {
                Battle.Question qq = lose.question() != null ? lose.question() : lose.nextQuestion();
                lose.choose((qq.correctIndex + 1) % 4);
            } else if (lose.phase() == Battle.Phase.ATTACK) {
                lose.finishAttack(10);
            }
        }
        check("поражение при нуле HP игрока", lose.phase() == Battle.Phase.LOST && lose.playerHp() == 0);

        // Ошибки использования фаз.
        try { new Battle(new Random(1)).choose(0); check("choose без вопроса", false); }
        catch (IllegalStateException e) { check("choose без вопроса — ошибка", true); }
    }

    static void bulletTests() {
        BulletField f = new BulletField(new Random(8));
        check("поле стартует без попаданий", f.hits() == 0 && !f.finished());
        f.moveHeartTo(-100, -100);
        check("сердце не выходит за рамку (лево/верх)",
                f.heartX() >= BulletField.HEART / 2 && f.heartY() >= BulletField.HEART / 2);
        f.moveHeartTo(1000, 1000);
        check("сердце не выходит за рамку (право/низ)",
                f.heartX() <= BulletField.WIDTH - BulletField.HEART / 2
                        && f.heartY() <= BulletField.HEIGHT - BulletField.HEART / 2);

        // Симуляция: неподвижное сердце в центре — попадания возможны, но фаза кончается ровно по времени.
        BulletField g = new BulletField(new Random(21));
        g.moveHeartTo(BulletField.WIDTH / 2, BulletField.HEIGHT / 2);
        int steps = 0;
        while (!g.finished() && steps < 100000) { g.update(1f / 60f); steps++; }
        check("атака заканчивается по времени", g.finished() && g.time() >= BulletField.DURATION);
        check("пули создаются", steps > 300);
        check("попадания согласованы со счётчиком", g.hits() >= 0);

        // Попадание: пуля прямо над сердцем.
        BulletField h = new BulletField(new Random(0));
        h.moveHeartTo(50, 50);
        BulletField.Bullet b = new BulletField.Bullet("X", 46, 46, 8, 8, 0, 0);
        check("пересечение AABB", h.hitsHeart(b));
        BulletField.Bullet far = new BulletField.Bullet("X", 150, 10, 8, 8, 0, 0);
        check("без пересечения", !h.hitsHeart(far));
    }

    static boolean distinct(int[] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = i + 1; j < a.length; j++)
                if (a[i] == a[j]) return false;
        return true;
    }
}
