package com.example.spamton;

import java.util.Random;

/** Тексты рекламы Спамтона. Без Android, просто данные и выбор. */
public final class Ads {

    private Ads() {}

    /** Бегущая строка сверху. */
    public static final String TICKER =
            "   *** MEGA DEAL *** 2+2 FOR ONLY 5 KROMER *** BUY NOW *** "
          + "NO REFUNDS *** I'M NOT A ROBOT *** GENEROSITY!!! *** "
          + "CALCULATOR PRO MAX ULTRA *** HOLD ME *** ";

    /** Всплывающее объявление: заголовок, текст, текст кнопки. */
    public static final class Popup {
        public final String title;
        public final String body;
        public final String cta;

        Popup(String title, String body, String cta) {
            this.title = title;
            this.body = body;
            this.cta = cta;
        }
    }

    static final Popup[] POPUPS = {
            new Popup("!!! FREE !!!", "WANT IT FOR FREE?? WATCH 1 AD!", "WATCH"),
            new Popup("MEGA DEAL", "BUY A CALCULATOR, GET A SECOND ONE 50% OFF!!", "BUY"),
            new Popup("[ BIG SHOT ]", "CORRECT ANSWER FOR 3 KROMER!!! TRUST ME!!", "PAY"),
            new Popup("CONGRATULATIONS!!", "YOU ARE OUR 1,000,000th USER!!! CLICK!", "CLAIM"),
            new Popup("SUBSCRIBE!!!", "10 KROMER A WEEK FOR ANSWERS THAT ARE SORT OF RIGHT", "YES"),
            new Popup("WARNING!!!", "YOUR PHONE IS SLOW. BUY BOOSTER!!!", "FIX"),
    };

    /** Фраза, если Спамтон побеждён и реклама прекращена. */
    public static final String[] FRIENDLY_TIPS = {
            "TIP: TAP THE DISPLAY TO COPY THE ANSWER.",
            "TIP: HOLD A BUTTON TO... NO, NEVER MIND.",
            "TIP: I WILL NOT SELL YOU ANYTHING ANYMORE.",
    };

    public static Popup randomPopup(Random rnd) {
        return POPUPS[rnd.nextInt(POPUPS.length)];
    }
}
