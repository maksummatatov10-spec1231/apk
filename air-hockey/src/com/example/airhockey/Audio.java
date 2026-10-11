package com.example.airhockey;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

/** Звуки через SoundPool. Включение/выключение звука сохраняется в настройках. */
final class Audio {
    static final int HIT = 0, WALL = 1, GOAL = 2, SERVE = 3, CLICK = 4, WIN = 5, LOSE = 6;
    private static final int[] RES = {
            R.raw.snd_hit, R.raw.snd_wall, R.raw.snd_goal, R.raw.snd_serve,
            R.raw.snd_click, R.raw.snd_win, R.raw.snd_lose};

    private final SoundPool pool;
    private final Map<Integer, Integer> ids = new HashMap<>();
    private final Settings settings;
    private long lastWall;

    Audio(Context ctx, Settings settings) {
        this.settings = settings;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
        for (int i = 0; i < RES.length; i++) ids.put(i, pool.load(ctx, RES[i], 1));
    }

    void play(int which) {
        if (!settings.sound()) return;
        Integer id = ids.get(which);
        if (id == null) return;
        // шум от отскоков не должен забивать звук: не чаще раза в 60 мс
        if (which == WALL) {
            long now = System.currentTimeMillis();
            if (now - lastWall < 60) return;
            lastWall = now;
        }
        pool.play(id, 1f, 1f, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
