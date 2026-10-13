package com.example.spamton;

/** Соответствие реплики и озвучки (res/raw/vc_*.wav). Без Android, только данные и поиск. */
final class Voice {

    private Voice() {}

    /** Имя ресурса озвучки для точного текста на экране или null. */
    static String resourceFor(String text) {
        if (text == null) return null;
        for (String[] e : VoiceLines.ENTRIES) {
            if (e[0].equals(text)) return e[1];
        }
        return null;
    }

    /** Все имена ресурсов озвучки (для загрузки в SoundPool). */
    static String[] resources() {
        String[] out = new String[VoiceLines.ENTRIES.length];
        for (int i = 0; i < out.length; i++) out[i] = VoiceLines.ENTRIES[i][1];
        return out;
    }
}
