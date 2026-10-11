package com.example.airhockey;

/**
 * Физика аэрохоккея без Android: стол, шайба, две ракетки, голы, ИИ.
 * Координаты: стол W×H, верхние ворота — y = 0 (ИИ/игрок 2), нижние — y = H (игрок 1).
 * Шаг делится на подшаги, поэтому шайба не проскакивает ракетку и стенку.
 */
public final class Table {
    public static final float W = 1000f, H = 1800f;
    public static final float PUCK_R = 46f, PADDLE_R = 84f;
    /** Центр ракетки может подойти к боковой стенке на радиус шайбы (так можно бить шайбу от стенки). */
    public static final float PADDLE_MIN_X = PUCK_R;
    public static final float GOAL_HALF = 165f;
    public static final float MAX_PUCK = 2600f;
    public static final float MAX_PADDLE = 3200f;
    /** Счёт, при котором матч заканчивается. */
    public static final int TARGET = 7;

    /** Сторона: 0 — нижний игрок (человек), 1 — верхний (ИИ или второй игрок). */
    public interface Listener {
        void onPaddleHit(float speed, int side);
        void onWallHit(float speed);
        void onGoal(int scorer);
    }

    /** Уровень сложности ИИ. */
    public static final class Level {
        final String name;
        final float maxSpeed;   // скорость ракетки ИИ, ед/с
        final float reaction;   // задержка реакции, с
        final float error;      // ошибка прицеливания, ед
        Level(String name, float maxSpeed, float reaction, float error) {
            this.name = name; this.maxSpeed = maxSpeed; this.reaction = reaction; this.error = error;
        }
    }

    public static final Level[] LEVELS = {
            new Level("Лёгкий", 1100f, 0.30f, 200f),
            new Level("Средний", 1800f, 0.18f, 110f),
            new Level("Сложный", 2500f, 0.08f, 40f),
            new Level("Мастер", 3100f, 0.03f, 0f),
    };

    public final float[] px = new float[2], py = new float[2];   // ракетки: 0 — нижняя, 1 — верхняя
    public final float[] vx = new float[2], vy = new float[2];
    public final float[] tx = new float[2], ty = new float[2];   // цели ракеток (от управления)
    public float puckX, puckY, puckVX, puckVY;
    public final int[] score = new int[2];
    /** Какая сторона управляется ботом (true) или человеком (false): 0 — нижняя, 1 — верхняя. */
    public final boolean[] bot = {false, false};
    /** Уровень бота для каждой стороны. */
    public final Level[] botLevel = {LEVELS[1], LEVELS[1]};

    /** Назначить сторону боту заданного уровня (level == null — сторона управляется человеком). */
    public void setBot(int side, Level level) {
        bot[side] = level != null;
        if (level != null) botLevel[side] = level;
    }
    public float drag = 0.12f;   // трение шайбы, 1/с
    public float restitution = 0.92f;
    private float stallTime = 0f;
    private float noGoalTime = 0f;   // секунды игры без гола

    private final float[] prevPx = new float[2], prevPy = new float[2];
    private float prevPuckX, prevPuckY;
    private Listener listener;
    private float serveDelay;     // пауза после гола, с
    private int serveTo = -1;     // кому подаём: сторона, которая пропустила
    private final float[] aiReact = new float[2];   // таймер реакции ботов
    private final float[] aiAimX = new float[2];    // цель ботов по x (с ошибкой)
    private long seed = 12345L;

    public Table(Listener l) {
        this.listener = l;
        reset();
    }

    public void reset() {
        score[0] = 0;
        score[1] = 0;
        serveTo = -1;
        serveDelay = 0;
        placeAll();
    }

    private void placeAll() {
        px[0] = W / 2; py[0] = H - 300; px[1] = W / 2; py[1] = 300;
        vx[0] = vx[1] = vy[0] = vy[1] = 0;
        tx[0] = px[0]; ty[0] = py[0]; tx[1] = px[1]; ty[1] = py[1];
        puckX = W / 2; puckY = H / 2; puckVX = puckVY = 0;
        aiReact[0] = aiReact[1] = 0;
        aiAimX[0] = aiAimX[1] = W / 2;
    }

    public boolean isOver() {
        return score[0] >= TARGET || score[1] >= TARGET;
    }

    public int winner() {
        if (score[0] >= TARGET) return 0;
        if (score[1] >= TARGET) return 1;
        return -1;
    }

    /** Управление ракеткой человека (сторона 0) или второго игрока (сторона 1). */
    public void setTarget(int side, float x, float y) {
        float minY = side == 0 ? H / 2 + PADDLE_R : PADDLE_R;
        float maxY = side == 0 ? H - PADDLE_R : H / 2 - PADDLE_R;
        tx[side] = clamp(x, PADDLE_MIN_X, W - PADDLE_MIN_X);
        ty[side] = clamp(y, minY, maxY);
    }

    /** Шаг симуляции; dt — секунды, ограничивается сверху. */
    public void update(float dtIn) {
        float dt = Math.min(dtIn, 1f / 30f);
        if (isOver()) {
            movePaddles(dt, 0);
            return;
        }
        if (serveDelay > 0) {
            serveDelay -= dt;
            movePaddles(dt, dt);
            for (int s = 0; s < 2; s++) keepPaddleOutOfPuck(s);
            if (serveDelay <= 0) {
                placeAll();
                if (serveTo == 1) puckVY = -260f; else puckVY = 260f;   // подача к тому, кто пропустил
                serveTo = -1;
            }
            return;
        }
        // правило без тупиков: 25 с без гола — вбрасывание в центр (шайба зажата у стенки, см. README)
        noGoalTime += dt;
        if (noGoalTime > 25f) {
            noGoalTime = 0f;
            serveTo = puckY < H / 2 ? 0 : 1;
            serveDelay = 1.2f;
            puckX = W / 2;
            puckY = H / 2;
            puckVX = puckVY = 0;
            return;
        }
        // защита от зацикливания: шайба зажата у боковой стенки и бьётся между ракетками вдоль неё
        boolean atSideWall = puckX < PUCK_R + 6f || puckX > W - PUCK_R - 6f;
        if (atSideWall && Math.abs(puckVX) < 200f && Math.abs(puckVY) > 300f) {
            stallTime += dt;
            if (stallTime > 0.8f) {
                puckVX = (puckX < W / 2 ? 1f : -1f) * 500f;
                stallTime = 0f;
            }
        } else {
            stallTime = Math.max(0f, stallTime - dt);
        }
        int sub = 4;
        float h = dt / sub;
        for (int i = 0; i < sub; i++) {
            movePaddles(h, dt);
            for (int s = 0; s < 2; s++) if (bot[s]) aiThink(s, dt);
            stepPuck(h);
            if (serveDelay > 0 || isOver()) break;   // после гола до подачи шайба стоит
        }
    }

    private void movePaddles(float h, float frameDt) {
        for (int s = 0; s < 2; s++) {
            prevPx[s] = px[s];
            prevPy[s] = py[s];
            float maxV = bot[s] ? botLevel[s].maxSpeed : MAX_PADDLE;
            float dx = tx[s] - px[s], dy = ty[s] - py[s];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float step = maxV * h;
            float nx, ny;
            if (dist <= step || dist < 1e-3f) {
                nx = tx[s]; ny = ty[s];
            } else {
                nx = px[s] + dx / dist * step;
                ny = py[s] + dy / dist * step;
            }
            float oldX = px[s], oldY = py[s];
            px[s] = clamp(nx, PADDLE_MIN_X, W - PADDLE_MIN_X);
            py[s] = clamp(ny, s == 0 ? H / 2 + PADDLE_R : PADDLE_R, s == 0 ? H - PADDLE_R : H / 2 - PADDLE_R);
            if (h > 0) {
                vx[s] = (px[s] - oldX) / h;
                vy[s] = (py[s] - oldY) / h;
            }
        }
    }

    private void stepPuck(float h) {
        prevPuckX = puckX;
        prevPuckY = puckY;
        float k = (float) Math.exp(-drag * h);
        puckVX *= k;
        puckVY *= k;
        puckX += puckVX * h;
        puckY += puckVY * h;

        // стены: левая/правая
        if (puckX < PUCK_R) {
            puckX = PUCK_R;
            wallBounce(Math.abs(puckVX), true);
        } else if (puckX > W - PUCK_R) {
            puckX = W - PUCK_R;
            wallBounce(Math.abs(puckVX), true);
        }
        // верхняя стенка / ворота
        if (puckY < PUCK_R) {
            if (Math.abs(puckX - W / 2) < GOAL_HALF - PUCK_R * 0.3f) {
                goal(0);       // шайба вошла в верхние ворота — гол игроку 0
                return;
            }
            puckY = PUCK_R;
            wallBounce(Math.abs(puckVY), false);
        }
        if (puckY > H - PUCK_R) {
            if (Math.abs(puckX - W / 2) < GOAL_HALF - PUCK_R * 0.3f) {
                goal(1);       // в нижние ворота — гол игроку 1
                return;
            }
            puckY = H - PUCK_R;
            wallBounce(Math.abs(puckVY), false);
        }
        // ракетки
        for (int s = 0; s < 2; s++) collidePaddle(s);

        float sp = (float) Math.sqrt(puckVX * puckVX + puckVY * puckVY);
        if (sp > MAX_PUCK) {
            puckVX *= MAX_PUCK / sp;
            puckVY *= MAX_PUCK / sp;
        }
    }

    private void wallBounce(float speed, boolean vertical) {
        if (vertical) puckVX = -puckVX * restitution;
        else puckVY = -puckVY * restitution;
        if (listener != null && speed > 120f) listener.onWallHit(speed);
    }

    private void collidePaddle(int s) {
        float dx = puckX - px[s], dy = puckY - py[s];
        float min = PUCK_R + PADDLE_R;
        float d2 = dx * dx + dy * dy;
        if (d2 >= min * min || d2 < 1e-6f) return;
        float d = (float) Math.sqrt(d2);
        float nx = dx / d, ny = dy / d;
        float savedVX = puckVX, savedVY = puckVY;
        // относительная скорость по нормали: шайба должна лететь от ракетки
        float rvx = puckVX - vx[s], rvy = puckVY - vy[s];
        float vn = rvx * nx + rvy * ny;
        if (vn < 0) {
            float j = -(1f + restitution) * vn;
            puckVX += j * nx;
            puckVY += j * ny;
        }
        float sp = (float) Math.sqrt(puckVX * puckVX + puckVY * puckVY);
        if (sp > MAX_PUCK) {
            puckVX *= MAX_PUCK / sp;
            puckVY *= MAX_PUCK / sp;
        }
        // вытолкнуть шайбу из ракетки; если стенка мешает — скользим вдоль неё
        float tx = px[s] + nx * min, ty = py[s] + ny * min;
        tx = clamp(tx, PUCK_R, W - PUCK_R);
        ty = clamp(ty, PUCK_R, H - PUCK_R);
        float ex = tx - px[s], ey = ty - py[s];
        if (ex * ex + ey * ey < min * min - 1f) {
            // X упёрся в стенку: сохраняем зазор, меняем Y
            float dxc = clamp(tx - px[s], -min, min);
            float dyc = (float) Math.sqrt(Math.max(0f, min * min - dxc * dxc));
            ty = py[s] + (ny < 0 ? -dyc : dyc);
            ty = clamp(ty, PUCK_R, H - PUCK_R);
        }
        puckX = tx;
        puckY = ty;
        // остаточное перекрытие: шайба выталкивается из ракетки
        pushPuckOutOfPaddle(s);
        if (listener != null && vn < -60f) listener.onPaddleHit(-vn, s);
    }

    /** Перекрытие после удара: отодвигаем шайбу (ракетки управляются игроком и не сдвигаются). */
    private void pushPuckOutOfPaddle(int s) {
        float ex = puckX - px[s], ey = puckY - py[s];
        float min = PUCK_R + PADDLE_R;
        float ed = (float) Math.sqrt(ex * ex + ey * ey);
        // шайба почти неподвижна и зажата (например в углу): ракетка отходит, иначе тупик
        if (ed < min - 0.5f && Math.hypot(puckVX, puckVY) < 60f) {
            keepPaddleOutOfPuck(s);
            return;
        }
        if (ed < min - 0.5f && ed > 1e-6f) {
            float rest = min - ed;
            puckX = clamp(puckX + ex / ed * rest, PUCK_R, W - PUCK_R);
            puckY = clamp(puckY + ey / ed * rest, PUCK_R, H - PUCK_R);
        }
    }

    /** Пауза после гола: ракетка не может проникать в шайбу, поэтому ракетку отодвигаем (импульса нет). */
    private void keepPaddleOutOfPuck(int s) {
        float ex = puckX - px[s], ey = puckY - py[s];
        float min = PUCK_R + PADDLE_R;
        float ed = (float) Math.sqrt(ex * ex + ey * ey);
        if (ed < min - 0.5f && ed > 1e-6f) {
            float over = min - ed;
            px[s] = clamp(px[s] - ex / ed * over, PADDLE_MIN_X, W - PADDLE_MIN_X);
            py[s] = clamp(py[s] - ey / ed * over, s == 0 ? H / 2 + PADDLE_R : PADDLE_R, s == 0 ? H - PADDLE_R : H / 2 - PADDLE_R);
        }
    }

    /** Жёсткие стены для шайбы (без отскока — отскок уже учтён в шаге). */
    private void clampPuckToTable() {
        if (puckX < PUCK_R) puckX = PUCK_R;
        if (puckX > W - PUCK_R) puckX = W - PUCK_R;
        if (puckY < PUCK_R && Math.abs(puckX - W / 2) >= GOAL_HALF - PUCK_R * 0.3f) puckY = PUCK_R;
        if (puckY > H - PUCK_R && Math.abs(puckX - W / 2) >= GOAL_HALF - PUCK_R * 0.3f) puckY = H - PUCK_R;
    }

    private void goal(int scorer) {
        score[scorer]++;
        noGoalTime = 0f;
        if (listener != null) listener.onGoal(scorer);
        serveTo = 1 - scorer;      // шайба летит к тому, кого обыграли
        serveDelay = isOver() ? 0 : 1.2f;
        puckVX = puckVY = 0;
        // шайба сразу уходит в центр и стоит: иначе гол засчитывался бы на каждом подшаге
        puckX = W / 2;
        puckY = isOver() ? (scorer == 0 ? H / 2 - 40 : H / 2 + 40) : H / 2;
    }

    // ---------- ИИ ----------

    /**
     * ИИ для стороны s. Все расчёты ведутся в зеркальных координатах, где бот всегда «верхний»:
     * для нижней стороны y отражается (y' = H - y), и обратно — при установке цели.
     */
    private void aiThink(int s, float dt) {
        Level level = botLevel[s];
        boolean low = s == 0;
        float mPuckY = low ? H - puckY : puckY;
        float mVY = low ? -puckVY : puckVY;
        aiReact[s] -= dt;
        if (aiReact[s] <= 0) {
            aiReact[s] = level.reaction;
            aiAimX[s] = predictX(mPuckY, mVY) + (level.error > 0 ? (rnd() * 2 - 1) * level.error : 0);
        }
        float goalX = W / 2, goalY = H;     // бьём в ворота соперника (в зеркальных координатах — нижние)
        float tgtX, tgtY;
        if (mPuckY < H / 2 || (mVY < 0 && mPuckY < H * 0.55f)) {
            // шайба на стороне бота — атакуем: зайти сзади и ударить в сторону ворот
            float dirX = goalX - puckX, dirY = goalY - mPuckY;
            float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (len < 1e-3f) len = 1;
            tgtX = puckX - dirX / len * (PUCK_R + PADDLE_R) * 0.9f;
            tgtY = mPuckY - dirY / len * (PUCK_R + PADDLE_R) * 0.9f;
            if (tgtY < PADDLE_R) tgtY = PADDLE_R;
            if (mPuckY < 500 && mVY > -80) {
                // шайба почти у бота и медленная: бьём сразу
                tgtX = aiAimX[s];
                tgtY = mPuckY + (PUCK_R + PADDLE_R) * 0.6f;
            }
        } else {
            // оборона: стоим между шайбой и своими воротами
            tgtX = clamp(aiAimX[s] * 0.5f + W / 2 * 0.5f, W / 2 - 260, W / 2 + 260);
            tgtY = 300;
        }
        setTarget(s, tgtX, low ? H - tgtY : tgtY);   // скорость ограничена уровнем в movePaddles
    }

    /** Предсказание x, где шайба пересечёт линию бота (в зеркальных координатах), с отражениями от боковых стен. */
    private float predictX(float mPuckY, float mVY) {
        if (mVY >= -1f) return puckX;          // не летит к боту — ждём в центре
        float t = (mPuckY - 300f) / -mVY;
        if (t < 0) t = 0;
        float x = puckX + puckVX * t;
        float span = W - 2 * PUCK_R;
        float u = (x - PUCK_R) % (2 * span);
        if (u < 0) u += 2 * span;
        if (u > span) u = 2 * span - u;
        return u + PUCK_R;
    }

    private float rnd() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return ((seed >>> 33) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
