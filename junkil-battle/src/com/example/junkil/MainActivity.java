package com.example.junkil;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Точка входа: экран боя с Джанклимом и цикл кадров (~60 в секунду). */
public class MainActivity extends Activity {

    private static final long FRAME_MS = 16L;
    private BattleView view;
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
        Bitmap[] sprites = new Bitmap[3];
        String[] names = {"junkil", "junkil_angry", "junkil_sad"};
        for (int i = 0; i < names.length; i++) {
            int id = getResources().getIdentifier(names[i], "drawable", getPackageName());
            if (id != 0) sprites[i] = BitmapFactory.decodeResource(getResources(), id);
        }
        view = new BattleView(this, sprites, System.currentTimeMillis());
        setContentView(view);
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
}
