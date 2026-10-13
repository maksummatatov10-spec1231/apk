package com.example.junkil;

import java.util.ArrayList;
import java.util.List;

/** Запись звуков для харнесса: какие звуки проиграны, без аудио. */
final class RecSounds implements Sounds {
    final List<String> played = new ArrayList<>();

    @Override
    public void play(String name) {
        played.add(name);
    }

    int count(String name) {
        int n = 0;
        for (String s : played) if (s.equals(name)) n++;
        return n;
    }
}
