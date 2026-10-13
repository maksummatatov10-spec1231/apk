package com.example.spamton;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

/** Звуки и озвучка из res/raw через SoundPool. Имена совпадают с файлами res/raw/<имя>.wav. */
final class AndroidSounds implements Sounds {

    private static final String[] NAMES = {
            "blip", "click", "right", "wrong", "error", "ding",
            "hit", "heal", "takeover", "lose", "win"
    };

    private final SoundPool pool;
    private final Map<String, Integer> ids = new HashMap<>();
    private boolean muted;

    AndroidSounds(Context context) {
        SoundPool.Builder b = new SoundPool.Builder().setMaxStreams(6);
        b.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        pool = b.build();
        for (String name : NAMES) load(context, name);
        for (String name : Voice.resources()) load(context, name);
    }

    private void load(Context context, String name) {
        int resId = context.getResources().getIdentifier(name, "raw", context.getPackageName());
        if (resId != 0) ids.put(name, pool.load(context, resId, 1));
    }

    void setMuted(boolean value) { muted = value; }

    @Override
    public void play(String name) {
        Integer id = ids.get(name);
        if (muted || id == null) return;
        pool.play(id, 0.9f, 0.9f, 1, 0, 1f);
    }

    void release() { pool.release(); }
}
