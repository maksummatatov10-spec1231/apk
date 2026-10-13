package com.example.junkil;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;
import java.util.Map;

/** Звуки из res/raw через SoundPool. Имена совпадают с файлами res/raw/<имя>.wav. */
final class AndroidSounds implements Sounds {

    private static final String[] NAMES = {
        "select", "confirm", "hurt", "hit", "heal", "whoosh", "win", "lose", "mercy", "back", "lid", "glint", "junkil_bak",
        "morse_sos", "morse_junk", "morse_free", "morse_buy", "morse_sale", "morse_help", "morse_yes", "morse_no",
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
        for (String name : NAMES) {
            int resId = context.getResources().getIdentifier(name, "raw", context.getPackageName());
            if (resId != 0) ids.put(name, pool.load(context, resId, 1));
        }
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
