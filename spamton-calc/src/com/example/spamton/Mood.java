package com.example.spamton;

/**
 * Эмоции Спамтона. Каждой соответствует картинка res/drawable-nodpi/spamton_<имя>.png.
 * Если картинки нет (ещё не сгенерирована), используется NEUTRAL (spamton.png).
 */
public enum Mood {
    NEUTRAL, HAPPY, ANGRY, SAD, SURPRISED, SMUG, GREEDY, SCARED, SLEEPY, EVIL, MANIC,
    FRIENDLY, FOCUSED, EMBARRASSED;

    /** Имя файла ресурса без расширения. */
    public String resName() {
        return this == NEUTRAL ? "spamton" : "spamton_" + name().toLowerCase(java.util.Locale.ROOT);
    }
}
