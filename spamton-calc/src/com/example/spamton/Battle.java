package com.example.spamton;

import java.util.Random;

/**
 * Логика боя за телефон (без Android).
 * Цикл: вопрос → игрок выбирает ответ → (если Спамтон жив) фаза уклонения → следующий вопрос.
 * Верный ответ бьёт Спамтона; неверный — он лечится «спамом» и смеётся.
 * Побеждённый Спамтон становится союзником.
 */
public final class Battle {

    public static final int SPAMTON_MAX_HP = 60;
    public static final int PLAYER_MAX_HP = 20;
    public static final int DAMAGE_PER_BULLET = 2;
    public static final int HEAL_ON_WRONG = 4;

    public enum Phase { QUIZ, ATTACK, WON, LOST }

    /** Вопрос с четырьмя вариантами. */
    public static final class Question {
        public final String text;
        public final int[] options;
        public final int correctIndex;

        Question(String text, int[] options, int correctIndex) {
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
        }

        public int answer() { return options[correctIndex]; }
    }

    /** Итог выбора ответа. */
    public static final class Outcome {
        public final boolean correct;
        public final int damageToSpamton;
        public final int healedSpamton;
        public final String line;

        Outcome(boolean correct, int damageToSpamton, int healedSpamton, String line) {
            this.correct = correct;
            this.damageToSpamton = damageToSpamton;
            this.healedSpamton = healedSpamton;
            this.line = line;
        }
    }

    /** Реплики при верном ответе (Спамтон получает урон, но не побеждён). */
    static final String[] HIT_LINES = {
            "ХМ!! ПОВЕЗЛО!! ...НУ ЛАДНО, МОЙ ХОД!!",
            "ПОЗДРАВЛЯЮ!!! ТЫ ВЫИГРАЛ [ВЕЧНУЮ ЖИЗНЬ]!",
            "ОН ДЁРГАЕТ ЗА НИТИ И ЗАСТАВЛЯЕТ ИХ ЗВЕНЕТЬ",
            "ЭЙ!! ТАК НЕЧЕСТНО!! ...НО ПРОДОЛЖАЙ!!",
    };

    /** Реплики при неверном ответе (Спамтон лечится). */
    static final String[] HEAL_LINES = {
            "НЕВЕРНО!!! МОЙ СПАМ СИЛЬНЕЕ!! ХИ-ХИ!!",
            "ХИ-ХИ-ХИ! ВЫ ВЫБРАЛИ [[ЗАЧЁТ]]!",
            "ОЙ, ОШИБОЧКА!! А Я ЛЕЧУСЬ, ХА!!",
    };

    private final Random rnd;
    private int spamtonHp = SPAMTON_MAX_HP;
    private int playerHp = PLAYER_MAX_HP;
    private int streak;
    private int round;
    private Phase phase = Phase.QUIZ;
    private Question question;

    public Battle(Random rnd) {
        this.rnd = rnd;
    }

    public int spamtonHp() { return spamtonHp; }
    public int playerHp() { return playerHp; }
    public int streak() { return streak; }
    public int round() { return round; }
    public Phase phase() { return phase; }
    public Question question() { return question; }

    /** Создаёт новый вопрос и переводит бой в фазу QUIZ. */
    public Question nextQuestion() {
        round++;
        phase = Phase.QUIZ;
        question = makeQuestion(round);
        return question;
    }

    /** Игрок выбрал вариант. Возвращает результат; фаза станет WON или ATTACK. */
    public Outcome choose(int index) {
        if (phase != Phase.QUIZ || question == null) {
            throw new IllegalStateException("not in quiz");
        }
        if (index == question.correctIndex) {
            int dmg = 8 + Math.min(streak, 4) * 2;
            streak++;
            spamtonHp = Math.max(0, spamtonHp - dmg);
            if (spamtonHp == 0) {
                phase = Phase.WON;
                return new Outcome(true, dmg, 0, "НЕЕЕТ... МОЙ АККАУНТ... КАК?!");
            }
            phase = Phase.ATTACK;
            return new Outcome(true, dmg, 0, pick(HIT_LINES));
        }
        streak = 0;
        int healed = Math.min(HEAL_ON_WRONG, SPAMTON_MAX_HP - spamtonHp);
        spamtonHp += healed;
        phase = Phase.ATTACK;
        return new Outcome(false, 0, healed, pick(HEAL_LINES));
    }

    /** Фаза уклонения окончена: списывает урон за попадания. */
    public void finishAttack(int hits) {
        if (phase != Phase.ATTACK) throw new IllegalStateException("not in attack");
        playerHp = Math.max(0, playerHp - hits * DAMAGE_PER_BULLET);
        phase = playerHp == 0 ? Phase.LOST : Phase.QUIZ;
    }

    /** Вопрос по раунду: сложность растёт. */
    Question makeQuestion(int r) {
        int a, b, result;
        String text;
        int kind = r <= 2 ? 0 : (r <= 4 ? rnd.nextInt(2) : rnd.nextInt(3));
        if (kind == 0) {
            a = 2 + rnd.nextInt(19);
            b = 2 + rnd.nextInt(19);
            result = a + b;
            text = a + " + " + b + " = ?";
        } else if (kind == 1) {
            a = 2 + rnd.nextInt(11);
            b = 2 + rnd.nextInt(11);
            result = a * b;
            text = a + " \u00d7 " + b + " = ?";
        } else {
            int root = 3 + rnd.nextInt(10);
            result = root;
            text = "\u221a" + (root * root) + " = ?";
        }
        int[] options = new int[4];
        int correct = rnd.nextInt(4);
        options[correct] = result;
        // Остальные варианты: близкие к верному, без повторов и без отрицательных.
        int[] distractors = new int[3];
        int count = 0;
        while (count < 3) {
            int delta = 1 + rnd.nextInt(9);
            if (rnd.nextBoolean()) delta = -delta;
            int cand = result + delta;
            if (cand < 0 || cand == result || contains(distractors, count, cand)) continue;
            distractors[count++] = cand;
        }
        for (int i = 0, d = 0; i < 4; i++) {
            if (i != correct) options[i] = distractors[d++];
        }
        return new Question(text, options, correct);
    }

    private static boolean contains(int[] arr, int n, int v) {
        for (int i = 0; i < n; i++) if (arr[i] == v) return true;
        return false;
    }

    private String pick(String[] list) {
        return list[rnd.nextInt(list.length)];
    }
}
