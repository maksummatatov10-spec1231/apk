package com.example.spamton;

import java.util.Random;

/**
 * Поведение Спамтона-калькулятора без Android.
 * До победы: большинство ответов неверные (с «выгодными» предложениями).
 * После победы (союзник): всегда точный ответ.
 */
public final class Spamton {

    /** Процент неверных ответов до победы. */
    public static final int WRONG_PERCENT = 70;

    /** Итог одного «=»: что показать и что сказать. */
    public static final class Verdict {
        public final double shown;
        public final boolean correct;
        public final String remark;

        Verdict(double shown, boolean correct, String remark) {
            this.shown = shown;
            this.correct = correct;
            this.remark = remark;
        }
    }

    static final String[] WRONG_REMARKS = {
            "I CALCULATED IT!!! TRUST ME!!",
            "EXACT ANSWER!!! ONLY FOR YOU!!",
            "ALMOST RIGHT... BUY THE FULL VERSION!!",
            "HERE'S YOUR ANSWER!! $$$ 100% GUARANTEED $$$",
            "I'M NOT WRONG!! I'M A BIG SHOT!!",
            "MATH IS EASY!!! I'M A GENIUS!!!",
    };

    static final String[] LUCKY_REMARKS = {
            "...WOW!! I GOT IT RIGHT!! FOR NOW!!",
            "LUCKY!!! DON'T GET USED TO IT!!",
            "THIS ONE'S ON THE HOUSE!!! ...FOR NOW",
    };

    static final String[] ALLY_REMARKS = {
            "HERE'S THE REAL ANSWER. NO CHARGE, FRIEND.",
            "I'M ON YOUR SIDE NOW!! EXACT RESULT!!",
            "NO MORE SPAM. JUST THE NUMBERS. PROMISE.",
            "YOU BEAT ME, SO I'LL HELP YOU. I'M A BIG SHOT, REMEMBER?",
    };

    private final Random rnd;
    private boolean ally;

    public Spamton(long seed) {
        this(new Random(seed), false);
    }

    public Spamton(Random rnd, boolean ally) {
        this.rnd = rnd;
        this.ally = ally;
    }

    public boolean isAlly() { return ally; }

    /** Вызывается после победы в бою. */
    public void befriend() { ally = true; }

    /** Что Спамтон покажет на «=» при верном истинном значении {@code truth}. */
    public Verdict answer(double truth) {
        if (ally) {
            return new Verdict(truth, true, pick(ALLY_REMARKS));
        }
        if (rnd.nextInt(100) < WRONG_PERCENT) {
            return new Verdict(distort(truth), false, pick(WRONG_REMARKS));
        }
        return new Verdict(truth, true, pick(LUCKY_REMARKS));
    }

    /** Заметно неверный ответ, отличающийся от {@code truth}. */
    double distort(double truth) {
        boolean integral = truth == Math.rint(truth);
        double[] candidates = integral
                ? new double[] {truth + 1, truth - 1, truth + 10, truth - 10, -truth, truth * 2, truth + 100}
                : new double[] {truth * 1.1, truth * 0.9, truth + 1, truth - 1, -truth, truth + 0.5};
        for (int attempt = 0; attempt < 20; attempt++) {
            double c = candidates[rnd.nextInt(candidates.length)];
            if (Math.abs(c - truth) > 1e-9) return c;
        }
        return truth + 1;
    }

    private String pick(String[] list) {
        return list[rnd.nextInt(list.length)];
    }
}
