package com.example.airhotkey;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Random;

/** Визуальные эффекты: частицы с гравитацией, ударные волны и всплывающий текст. */
final class Fx {

    private static final class Particle {
        float x, y, vx, vy, life, max, size;
        int color;
    }

    private static final class Ring {
        float x, y, life, max, maxR, width;
        int color;
    }

    private static final class Label {
        float x, y, vy, life, max, size;
        String text;
        int color;
    }

    private static final int MAX_PARTICLES = 420;

    private final ArrayList<Particle> particles = new ArrayList<Particle>();
    private final ArrayList<Ring> rings = new ArrayList<Ring>();
    private final ArrayList<Label> labels = new ArrayList<Label>();
    private final Random rnd;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float s = 1f;

    Fx(Random rnd) {
        this.rnd = rnd;
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setScale(float scale) {
        s = scale;
    }

    void clear() {
        particles.clear();
        rings.clear();
        labels.clear();
    }

    /** Взрыв частиц из точки: разлёт по кругу, падение, затухание. */
    void burst(float x, float y, int color, int count) {
        for (int i = 0; i < count && particles.size() < MAX_PARTICLES; i++) {
            Particle p = new Particle();
            float a = rnd.nextFloat() * (float) (Math.PI * 2);
            float sp = (120f + rnd.nextFloat() * 340f) * s;
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * sp;
            p.vy = (float) Math.sin(a) * sp - 60f * s;
            p.max = 0.45f + rnd.nextFloat() * 0.75f;
            p.life = p.max;
            p.size = (1.8f + rnd.nextFloat() * 3.6f) * s;
            // часть частиц — белые, как перегретое ядро
            p.color = rnd.nextInt(4) == 0 ? 0xFFFFFFFF : color;
            particles.add(p);
        }
    }

    /** Ударная волна: кольцо расширяется и гаснет. */
    void ring(float x, float y, int color, float maxRadius, float life) {
        Ring r = new Ring();
        r.x = x;
        r.y = y;
        r.maxR = maxRadius;
        r.max = life;
        r.life = life;
        r.width = 8f * s;
        r.color = color;
        rings.add(r);
    }

    /** Текст, который всплывает и гаснет (очки, «ПРОМАХ», «КОМБО»). */
    void label(float x, float y, String text, int color, float size, float life) {
        Label l = new Label();
        l.x = x;
        l.y = y;
        l.text = text;
        l.color = color;
        l.size = size;
        l.max = life;
        l.life = life;
        l.vy = 70f * s;
        labels.add(l);
    }

    void update(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }
            p.vy += 320f * s * dt;
            p.vx *= 1f - 2.2f * dt;
            p.vy *= 1f - 0.9f * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            r.life -= dt;
            if (r.life <= 0) rings.remove(i);
        }
        for (int i = labels.size() - 1; i >= 0; i--) {
            Label l = labels.get(i);
            l.life -= dt;
            if (l.life <= 0) {
                labels.remove(i);
                continue;
            }
            l.y -= l.vy * dt;
            l.vy *= 1f - 1.5f * dt;
        }
    }

    void draw(Canvas c) {
        // кольца
        for (Ring r : rings) {
            float t = 1f - r.life / r.max;             // 0 → 1
            float radius = r.maxR * (1f - (1f - t) * (1f - t));
            float a = 1f - t;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(withAlpha(r.color, a * 0.35f));
            paint.setStrokeWidth(r.width * 3f * (1f - t) + 2f * s);
            c.drawCircle(r.x, r.y, radius, paint);
            paint.setColor(withAlpha(0xFFFFFFFF, a * 0.9f));
            paint.setStrokeWidth(Math.max(1f, r.width * (1f - t)));
            c.drawCircle(r.x, r.y, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // частицы: свечение + ядро
        for (Particle p : particles) {
            float a = p.life / p.max;
            float r = p.size * (0.45f + 0.55f * a);
            paint.setColor(withAlpha(p.color, a * 0.22f));
            c.drawCircle(p.x, p.y, r * 3.2f, paint);
            paint.setColor(withAlpha(p.color, a));
            c.drawCircle(p.x, p.y, r, paint);
        }

        // всплывающий текст
        for (Label l : labels) {
            float a = Math.min(1f, l.life / (l.max * 0.5f));
            textPaint.setTextSize(l.size);
            textPaint.setColor(withAlpha(l.color, a));
            textPaint.setShadowLayer(10f * s, 0, 0, withAlpha(l.color, a * 0.9f));
            c.drawText(l.text, l.x, l.y, textPaint);
        }
        textPaint.clearShadowLayer();
    }

    static int withAlpha(int color, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (color & 0xFFFFFF);
    }
}
