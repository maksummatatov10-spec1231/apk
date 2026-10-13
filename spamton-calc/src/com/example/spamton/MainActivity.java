package com.example.spamton;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import java.util.Random;

/** Точка входа: полноэкранный калькулятор Спамтона. */
public final class MainActivity extends Activity {

    private AndroidSounds sounds;
    private SpamtonView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        final SharedPreferences prefs = getSharedPreferences("spamton", MODE_PRIVATE);
        Store store = new Store() {
            @Override public boolean getBool(String key, boolean def) { return prefs.getBoolean(key, def); }
            @Override public void putBool(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
        };
        sounds = new AndroidSounds(this);
        view = new SpamtonView(this, sounds, store, new Random());
        setContentView(view);
    }

    @Override
    protected void onDestroy() {
        if (sounds != null) sounds.release();
        super.onDestroy();
    }
}
