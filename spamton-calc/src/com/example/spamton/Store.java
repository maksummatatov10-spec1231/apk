package com.example.spamton;

/** Хранилище флагов (на Android — SharedPreferences). Нужен для «побеждён ли Спамтон». */
public interface Store {
    boolean getBool(String key, boolean def);
    void putBool(String key, boolean value);
}
