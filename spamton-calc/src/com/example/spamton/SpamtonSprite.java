package com.example.spamton;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Пиксельный Спамтон, нарисованный процедурно (без чужих картинок).
 * Голова-марионетка с жёлто-розовыми очками, чёрный свитер с V-вырезом.
 */
public final class SpamtonSprite {

    public static final int COLS = 24;
    public static final int ROWS = 30;

    private static final int COL_BLACK = 0xFF0B0B0B;
    private static final int COL_SKIN = 0xFFF6E3D0;
    private static final int COL_YELLOW = 0xFFFFF200;
    private static final int COL_PINK = 0xFFFF5CC8;
    private static final int COL_WHITE = 0xFFFFFFFF;
    private static final int COL_CHEEK = 0xFFFF9EC7;

    private static char[][] grid;

    private SpamtonSprite() {}

    /** Сетка символов: K чёрный, S кожа, Y жёлтый, P розовый, W белый, C щёки, . пусто. */
    static synchronized char[][] grid() {
        if (grid == null) grid = build();
        return grid;
    }

    /** Рисует спрайт с верхним левым углом (x, y) и размером клетки cell. */
    public static void draw(Canvas canvas, float x, float y, float cell, Paint paint) {
        char[][] g = grid();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int color = colorOf(g[r][c]);
                if (color == 0) continue;
                paint.setColor(color);
                canvas.drawRect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, paint);
            }
        }
    }

    static int colorOf(char ch) {
        switch (ch) {
            case 'K': return COL_BLACK;
            case 'S': return COL_SKIN;
            case 'Y': return COL_YELLOW;
            case 'P': return COL_PINK;
            case 'W': return COL_WHITE;
            case 'C': return COL_CHEEK;
            default: return 0;
        }
    }

    static char[][] build() {
        char[][] g = new char[ROWS][COLS];
        for (char[] row : g) java.util.Arrays.fill(row, '.');

        // Голова: эллипс.
        double cx = 11.5, cy = 10.5, rx = 9.6, ry = 9.8;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                double dx = (c - cx) / rx, dy = (r - cy) / ry;
                if (dx * dx + dy * dy <= 1.0) {
                    boolean hair = r <= 3 || (r <= 5 && Math.abs(c - cx) > 7.5);
                    g[r][c] = hair ? 'K' : 'S';
                }
            }
        }
        // Очки: жёлтое и розовое стёкла, чёрная оправа.
        for (int r = 8; r <= 11; r++) {
            for (int c = 4; c <= 10; c++) g[r][c] = (r == 8 || r == 11 || c == 4 || c == 10) ? 'K' : 'Y';
            for (int c = 13; c <= 19; c++) g[r][c] = (r == 8 || r == 11 || c == 13 || c == 19) ? 'K' : 'P';
        }
        g[9][11] = 'K';
        g[9][12] = 'K';
        // Щёки.
        g[13][6] = 'C'; g[13][7] = 'C'; g[13][16] = 'C'; g[13][17] = 'C';
        // Рот-улыбка.
        g[14][8] = 'K'; g[14][15] = 'K';
        for (int c = 9; c <= 14; c++) g[15][c] = 'K';
        // Шея.
        for (int r = 19; r <= 21; r++) for (int c = 10; c <= 13; c++) g[r][c] = 'S';
        // Свитер с V-вырезом.
        for (int r = 22; r < ROWS; r++) {
            double half = 4.5 + (r - 22) * 0.7;
            for (int c = 0; c < COLS; c++) {
                double d = Math.abs(c - cx);
                if (d <= half) {
                    boolean vNeck = d < (r - 22) * 0.6;
                    g[r][c] = vNeck ? 'W' : 'K';
                }
            }
        }
        // Чёрный контур вокруг головы.
        char[][] out = new char[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) out[r] = g[r].clone();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (g[r][c] != '.') continue;
                if (neighbourFilled(g, r, c)) out[r][c] = 'K';
            }
        }
        return out;
    }

    private static boolean neighbourFilled(char[][] g, int r, int c) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            int rr = r + o[0], cc = c + o[1];
            if (rr < 0 || cc < 0 || rr >= ROWS || cc >= COLS) continue;
            if (g[rr][cc] != '.' && g[rr][cc] != 'K' && r < 22) return true;
        }
        return false;
    }
}
