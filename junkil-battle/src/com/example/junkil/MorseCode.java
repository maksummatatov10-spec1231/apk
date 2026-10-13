package com.example.junkil;

/** Азбука Морзе (международная) для сигнала Джанклима. Без Android: только строки. */
final class MorseCode {

    private MorseCode() {}

    private static final String[] TABLE = {
        ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---",   // A–J
        "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-",      // K–T
        "..-", "...-", ".--", "-..-", "-.--", "--.."                              // U–Z
    };

    /** Код одной буквы A–Z, либо null для остальных символов. */
    static String letter(char c) {
        char u = Character.toUpperCase(c);
        if (u < 'A' || u > 'Z') return null;
        return TABLE[u - 'A'];
    }

    /** Слово в морзе: буквы разделены пробелом, например SOS → "... --- ...". */
    static String encode(String word) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            String code = letter(word.charAt(i));
            if (code == null) throw new IllegalArgumentException("нет кода для: " + word.charAt(i));
            if (sb.length() > 0) sb.append(' ');
            sb.append(code);
        }
        return sb.toString();
    }

    /** Расшифровка слова из морзе (обратная операция к encode). Пустая строка — если код не распознан. */
    static String decode(String morse) {
        StringBuilder out = new StringBuilder();
        for (String part : morse.trim().split(" ")) {
            if (part.isEmpty()) continue;
            char found = 0;
            for (int i = 0; i < TABLE.length; i++) {
                if (TABLE[i].equals(part)) { found = (char) ('A' + i); break; }
            }
            if (found == 0) return "";
            out.append(found);
        }
        return out.toString();
    }
}
