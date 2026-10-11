package com.example.airhockey;

/** Проверка физики и ИИ без Android: scripts/test.sh */
public final class TableCheck {
    static int failed = 0, passed = 0;

    static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  OK   " : "  FAIL ") + what);
    }

    /** Разумный человек: атакует, заходя сзади шайбы и целясь в верхние ворота; иначе стоит на обороне. */
    static void human(Table t) {
        if (t.puckY > Table.H / 2 - 60 || t.puckVY > 300) {
            float dirX = Table.W / 2 - t.puckX, dirY = 0 - t.puckY;
            float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (len < 1e-3f) len = 1;
            float off = (Table.PUCK_R + Table.PADDLE_R) * 0.9f;
            t.setTarget(0, t.puckX - dirX / len * off, t.puckY - dirY / len * off);
        } else {
            t.setTarget(0, t.puckX * 0.5f + Table.W / 4, Table.H - 300);
        }
    }

    static final class Stats implements Table.Listener {
        int hits, walls, goals0, goals1;
        float maxHit;
        public void onPaddleHit(float speed, int side) { hits++; maxHit = Math.max(maxHit, speed); }
        public void onWallHit(float speed) { walls++; }
        public void onGoal(int scorer) { if (scorer == 0) goals0++; else goals1++; }
    }

    /** Матч бот против бота: side 0 — нижний бот уровня lowLevel, side 1 — верхний бот уровня highLevel. */
    static int[] botMatch(int lowLevel, int highLevel, long seconds, Stats st) {
        Table t = new Table(st);
        t.setBot(0, Table.LEVELS[lowLevel]);
        t.setBot(1, Table.LEVELS[highLevel]);
        float dt = 1f / 60f;
        int frames = (int) (seconds / dt);
        for (int f = 0; f < frames && !t.isOver(); f++) {
            t.update(dt);
            if (Float.isNaN(t.puckX) || Float.isNaN(t.puckY)) return new int[]{-1, -1};
        }
        return new int[]{t.score[0], t.score[1]};
    }

    /** Симуляция одного матча: возвращает статистику нарушений. */
    static String simulate(int level, long seconds, Stats st, int[] bad) {
        Table t = new Table(st);
        t.setBot(1, Table.LEVELS[level]);
        float dt = 1f / 60f;
        int frames = (int) (seconds / dt);
        float minD = Table.PUCK_R + Table.PADDLE_R;
        for (int f = 0; f < frames && !t.isOver(); f++) {
            human(t);
            t.update(dt);
            if (Float.isNaN(t.puckX) || Float.isNaN(t.puckY) || Float.isNaN(t.puckVX) || Float.isNaN(t.puckVY)) { bad[0]++; break; }
            // шайба не вылетает за боковые стены
            if (t.puckX < Table.PUCK_R - 1 || t.puckX > Table.W - Table.PUCK_R + 1) bad[1]++;
            // ракетки внутри своей половины и стола
            for (int s = 0; s < 2; s++) {
                if (t.px[s] < Table.PADDLE_MIN_X - 1 || t.px[s] > Table.W - Table.PADDLE_MIN_X + 1) bad[2]++;
            }
            if (t.py[0] < Table.H / 2 + Table.PADDLE_R - 1) bad[2]++;
            if (t.py[1] > Table.H / 2 - Table.PADDLE_R + 1) bad[2]++;
            // шайба не проникает в ракетку (ошибки туннелирования), когда не в паузе после гола
            if (!t.isOver()) {
                for (int s = 0; s < 2; s++) {
                    float dx = t.puckX - t.px[s], dy = t.puckY - t.py[s];
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < minD * 0.97f && t.puckY > 0 && t.puckY < Table.H) bad[3]++;
                }
            }
            float sp = (float) Math.sqrt(t.puckVX * t.puckVX + t.puckVY * t.puckVY);
            if (sp > Table.MAX_PUCK + 1e-3f) bad[4]++;
        }
        return "счёт " + t.score[0] + ":" + t.score[1] + ", ударов " + st.hits + ", стен " + st.walls
                + ", макс. удар " + Math.round(st.maxHit);
    }

    public static void main(String[] args) {
        System.out.println("Физика и ИИ аэрохоккея:");
        int[] bad = new int[5];
        for (int lv = 0; lv < Table.LEVELS.length; lv++) {
            Stats st = new Stats();
            String res = simulate(lv, 900, st, bad);
            System.out.println("    " + Table.LEVELS[lv].name + " (900 с игры): " + res);
        }
        check(bad[0] == 0, "нет NaN в координатах");
        check(bad[1] == 0, "шайба не выходит за боковые стены (нарушений: " + bad[1] + ")");
        check(bad[2] == 0, "ракетки остаются в своих половинах");
        check(bad[3] == 0, "шайба не проходит сквозь ракетку (нарушений: " + bad[3] + ")");
        check(bad[4] == 0, "скорость шайбы ограничена");

        // матч доходит до конца и есть победитель
        Stats st = new Stats();
        Table t = new Table(st);
        t.setBot(1, Table.LEVELS[1]);
        for (int f = 0; f < 60 * 60 * 30 && !t.isOver(); f++) { human(t); t.update(1f / 60f); }
        check(t.isOver() && t.winner() >= 0, "матч до " + Table.TARGET + " голов завершается победителем (счёт " + t.score[0] + ":" + t.score[1] + ")");

        // после гола подача идёт к проигравшему
        Table.Listener none = null;
        Table g = new Table(none);
        g.setBot(1, Table.LEVELS[1]);
        g.px[0] = 100; g.py[0] = Table.H - 300;   // ракетки в стороне от линии полёта
        g.px[1] = 900; g.py[1] = 300;
        g.puckX = Table.W / 2; g.puckY = Table.H - 200; g.puckVX = 0; g.puckVY = 900;   // летит в нижние ворота
        for (int f = 0; f < 60 && g.score[1] == 0; f++) g.update(1f / 60f);   // гол игроку 1
        check(g.score[1] == 1, "гол в нижние ворота засчитан игроку 1");
        // после гола игрока 1 обыгран игрок 0 (нижняя сторона): подача должна идти вниз, к нему
        for (int f = 0; f < 60 * 2; f++) { g.setTarget(0, g.px[0], g.py[0]); g.update(1f / 60f); }
        check(g.puckVY > 0, "после гола игрока 1 подача летит к нижней стороне (к игроку 0)");

        // ворота: шайба у боковой стенки вне ворот отскакивает, а не влетает
        Table w = new Table(none);
        w.setBot(1, Table.LEVELS[1]);
        w.puckX = Table.W / 2 + Table.GOAL_HALF + 80; w.puckY = 80; w.puckVX = 0; w.puckVY = -800;
        for (int f = 0; f < 30; f++) w.update(1f / 60f);
        check(w.score[0] == 0 && w.score[1] == 0, "вне ворот шайба отскакивает от стенки, гола нет");

        // бот против бота: сильнейший уровень выигрывает больше голов (обе рассадки, 5 минут каждая)
        System.out.println("Бот против бота (очки нижний:верхний, 5 мин):");
        String[] names = {"Лёгкий", "Средний", "Сложный", "Мастер"};
        Stats bst = new Stats();
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                int[] r1 = botMatch(a, b, 300, bst);     // a внизу, b вверху
                int[] r2 = botMatch(b, a, 300, bst);     // b внизу, a вверху
                System.out.println("    " + names[a] + " (внизу) " + r1[0] + ":" + r1[1] + " " + names[b] + " (вверху)   |   "
                        + names[b] + " (внизу) " + r2[0] + ":" + r2[1] + " " + names[a] + " (вверху)");
                check(r1[0] >= 0 && r2[0] >= 0, "бот против бота " + names[a] + "/" + names[b] + ": нет NaN");
                int strong = (r1[1] - r1[0]) + (r2[0] - r2[1]);   // очки сильнейшего b (вверху в r1, внизу в r2)
                check(strong >= 0, "бот " + names[b] + " не уступает слабейшему " + names[a] + " (разница " + strong + ")");
            }
        }

        System.out.println("Итог: пройдено " + passed + ", провалено " + failed);
        if (failed > 0) System.exit(1);
    }
}
