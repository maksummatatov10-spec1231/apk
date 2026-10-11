package com.example.airhockey;

import android.content.Context;
import android.content.SharedPreferences;

/** Настройки и статистика, сохраняются в SharedPreferences. */
final class Settings {
    private final SharedPreferences prefs;

    Settings(Context ctx) {
        prefs = ctx.getSharedPreferences("airhockey", Context.MODE_PRIVATE);
    }

    boolean sound() { return prefs.getBoolean("sound", true); }
    void setSound(boolean on) { prefs.edit().putBoolean("sound", on).apply(); }

    int wins() { return prefs.getInt("wins", 0); }
    int matches() { return prefs.getInt("matches", 0); }
    boolean has(String ach) { return prefs.getBoolean("ach_" + ach, false); }

    /** Записывает итог матча и возвращает список новых достижений. */
    String recordMatch(int playerSide, int[] score, int lowLevel, int highLevel, boolean vsAi) {
        SharedPreferences.Editor e = prefs.edit();
        e.putInt("matches", matches() + 1);
        StringBuilder unlocked = new StringBuilder();
        if (vsAi && playerSide >= 0) {
            boolean won = score[playerSide] > score[1 - playerSide];
            if (won) {
                e.putInt("wins", wins() + 1);
                if (!has("first_win")) { e.putBoolean("ach_first_win", true); unlocked.append("Первая победа\n"); }
                if (highLevel == 3 && !has("master")) { e.putBoolean("ach_master", true); unlocked.append("Победа над Мастером\n"); }
                if (score[1 - playerSide] == 0 && !has("shutout")) { e.putBoolean("ach_shutout", true); unlocked.append("Сухая победа 7:0\n"); }
            }
        }
        e.apply();
        return unlocked.toString();
    }
}
