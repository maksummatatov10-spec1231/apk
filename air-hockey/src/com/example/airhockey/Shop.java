package com.example.airhockey;

/** Каталог магазина: цвета ракеток и шайб. Бесплатные позиции доступны сразу. */
final class Shop {
    static final class Item {
        final String id, category, name;
        final int price, color;
        Item(String id, String category, String name, int price, int color) {
            this.id = id; this.category = category; this.name = name; this.price = price; this.color = color;
        }
    }

    static final Item[] ITEMS = {
            new Item("p_red", "paddle", "Красная", 0, 0xFFFF4D6D),
            new Item("p_mint", "paddle", "Мятная", 30, 0xFF3DFF9A),
            new Item("p_gold", "paddle", "Золотая", 60, 0xFFFFC857),
            new Item("p_violet", "paddle", "Фиолетовая", 90, 0xFFB56CFF),
            new Item("k_black", "puck", "Чёрная", 0, 0xFF111111),
            new Item("k_fire", "puck", "Огненная", 40, 0xFFFF7A00),
            new Item("k_ice", "puck", "Ледяная", 40, 0xFFB0F0FF),
    };

    static Item find(String id) {
        for (Item i : ITEMS) if (i.id.equals(id)) return i;
        return ITEMS[0];
    }

    private Shop() {}
}
