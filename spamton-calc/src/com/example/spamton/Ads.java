package com.example.spamton;

import java.util.Random;

/** Тексты рекламы Спамтона. Без Android, просто данные и выбор. */
public final class Ads {

    private Ads() {}

    /** Бегущая строка сверху. */
    public static final String TICKER =
            "   *** МЕГА СКИДКА *** 2+2 ВСЕГО ЗА 5 КРОМЕРОВ *** КУПИ СЕЙЧАС *** "
          + "БЕЗ ВОЗВРАТА *** Я НЕ РОБОТ *** ЩЕДРОСТЬ!!! *** "
          + "КАЛЬКУЛЯТОР ПРО МАКС УЛЬТРА *** ОБНИМИ МЕНЯ *** ";

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
            new Popup("!!! БЕСПЛАТНО !!!", "ХОЧЕШЬ БЕСПЛАТНО?? ПОСМОТРИ 1 РЕКЛАМУ!", "СМОТРЕТЬ"),
            new Popup("МЕГА СКИДКА", "КУПИ КАЛЬКУЛЯТОР, ВТОРОЙ В ПОДАРОК, ЕЩЕ 50% СКИДКА!!", "КУПИТЬ"),
            new Popup("[ БОЛЬШОЙ ШОТ ]", "ПРАВИЛЬНЫЙ ОТВЕТ ЗА 3 КРОМЕРА!!! ПОВЕРЬ МНЕ!!", "ОПЛАТИТЬ"),
            new Popup("ПОЗДРАВЛЯЕМ!!", "ТЫ НАШ 1 000 000-Й ПОЛЬЗОВАТЕЛЬ!!! НАЖМИ!", "ЗАБРАТЬ"),
            new Popup("ПОДПИШИСЬ!!!", "10 КРОМЕРОВ В НЕДЕЛЮ ЗА ОТВЕТЫ, КОТОРЫЕ ПОЧТИ ВЕРНЫ", "ДА"),
            new Popup("ВНИМАНИЕ!!!", "ТВОЙ ТЕЛЕФОН ТОРМОЗИТ. КУПИ УСКОРИТЕЛЬ!!!", "ИСПРАВИТЬ"),
    };

    /** Фразы, если Спамтон побеждён и реклама прекращена. */
    public static final String[] FRIENDLY_TIPS = {
            "СОВЕТ: НАЖМИ НА ДИСПЛЕЙ, ЧТОБЫ СКОПИРОВАТЬ ОТВЕТ.",
            "СОВЕТ: ДЕРЖИ КНОПКУ... НЕТ, ЛУЧШЕ НЕ НАДО.",
            "СОВЕТ: Я БОЛЬШЕ НИЧЕГО ТЕБЕ НЕ ПРОДАМ.",
    };

    public static Popup randomPopup(Random rnd) {
        return POPUPS[rnd.nextInt(POPUPS.length)];
    }
}
