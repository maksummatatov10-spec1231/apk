package com.example.junkil;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/** Точка входа: экран боя с Джанклимом (ландшафт, полноэкранный режим) и цикл кадров. */
public class MainActivity extends Activity {

    private static final long FRAME_MS = 16L;
    /** Бесшовные текстуры атак (порядок = индекс в ArenaSim.Item.tex). */
    private static final String[] TEXTURES = {
        "atk_static", "atk_circuit", "atk_dollar", "atk_chrome", "atk_bubbles",
        "atk_vinyl", "atk_rust", "atk_cassette", "atk_stripes",
    };

    private BattleView view;
    private AndroidSounds sounds;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastNs;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = System.nanoTime();
            float dt = lastNs == 0 ? 0f : Math.min(0.05f, (now - lastNs) / 1e9f);
            lastNs = now;
            view.tick(dt);
            view.invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bitmap[] sprites = {load("junkil"), null, null};
        Bitmap[] textures = new Bitmap[TEXTURES.length];
        for (int i = 0; i < TEXTURES.length; i++) textures[i] = load(TEXTURES[i]);
        view = new BattleView(this, sprites, textures, System.currentTimeMillis());
        sounds = new AndroidSounds(this);
        view.setSounds(sounds);
        setContentView(view);
        hideSystemUi();
    }

    private Bitmap load(String name) {
        int id = getResources().getIdentifier(name, "drawable", getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id);
    }

    private void hideSystemUi() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastNs = 0;
        handler.post(frame);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(frame);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sounds != null) sounds.release();
    }
}
