package com.example.calcpro;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** История запросов, сохраняется между запусками (SharedPreferences). */
final class HistoryStore {
    private static final String PREFS = "calc_history";
    private static final String KEY = "items";
    private static final String SEP = "\u001F";
    private static final int MAX = 50;

    private HistoryStore() {}

    static List<String> load(Context c) {
        List<String> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY, "");
        if (raw.isEmpty()) return out;
        for (String s : raw.split(SEP)) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** Добавляет запрос в начало списка; повтор поднимается наверх. */
    static void add(Context c, String query) {
        List<String> list = load(c);
        list.remove(query);
        list.add(0, query);
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(c, list);
    }

    static void clear(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }

    private static void save(Context c, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append(SEP);
            sb.append(s.replace(SEP, " "));
        }
        prefs(c).edit().putString(KEY, sb.toString()).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
