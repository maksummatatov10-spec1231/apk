package com.example.calcpro;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Математическая клавиатура: три страницы (цифры, функции, символы), переключаются вкладками.
 * Клавиша с (insert == null) — служебная: «AC», «⌫» или «Решить». «=» — обычный знак.
 */
final class KeypadView extends LinearLayout {

    interface Listener {
        /** Вставить текст в позицию курсора; cursorBack — на сколько символов откатить курсор (для скобок). */
        void onInsert(String text, int cursorBack);
        void onDelete();
        void onClear();
        void onSolve();
    }

    static final int DIGIT = 0, FUNC = 1, OP = 2, ACTION = 3, SOLVE = 4;

    static final class Key {
        final String label;
        final String insert;   // null — служебная клавиша
        final int back;
        final int kind;

        Key(String label, String insert, int back, int kind) {
            this.label = label;
            this.insert = insert;
            this.back = back;
            this.kind = kind;
        }
    }

    private static Key d(String label, String insert) { return new Key(label, insert, 0, DIGIT); }
    private static Key f(String label, String insert) { return new Key(label, insert, 0, FUNC); }
    private static Key fb(String label, String insert, int back) { return new Key(label, insert, back, FUNC); }
    private static Key o(String label, String insert) { return new Key(label, insert, 0, OP); }
    private static Key clear() { return new Key("AC", null, 0, ACTION); }
    private static Key del() { return new Key("⌫", null, 0, ACTION); }
    private static Key solve() { return new Key("Решить", null, 0, SOLVE); }

    private static final Key[][][] PAGES = {
        { // Цифры
            {clear(), del(), o("(", "("), o(")", ")"), o("÷", "÷")},
            {d("7", "7"), d("8", "8"), d("9", "9"), o("×", "×"), o("x", "x")},
            {d("4", "4"), d("5", "5"), d("6", "6"), o("−", "−"), o("y", "y")},
            {d("1", "1"), d("2", "2"), d("3", "3"), o("+", "+"), o("^", "^")},
            {d("0", "0"), d(",", ","), o("=", "="), o("!", "!"), solve()},
        },
        { // Функции
            {f("sin", "sin()"), f("cos", "cos()"), f("tan", "tan()"), f("asin", "asin()"), f("acos", "acos()")},
            {f("atan", "atan()"), f("ln", "ln()"), f("log", "log()"), f("√", "√("), f("∛", "∛(")},
            {f("x²", "^2"), f("x³", "^3"), f("eˣ", "e^"), f("10ˣ", "10^"), f("|x|", "abs(")},
            {f("π", "π"), f("e", "e"), f("n!", "!"), f("1/x", "1/"), f("xⁿ", "^")},
            {fb("()", "()", 1), o("=", "="), f("⌫", null), f("AC", null), solve()},
        },
        { // Символы
            {d("x", "x"), d("y", "y"), d("z", "z"), d("a", "a"), d("b", "b")},
            {d("c", "c"), d("k", "k"), d("m", "m"), d("n", "n"), d("t", "t")},
            {d("²", "²"), d("³", "³"), d("⁴", "⁴"), o("·", "·"), o(";", ";")},
            {d(".", "."), d(",", ","), o("%", "%"), o("!", "!"), d("π", "π")},
            {clear(), del(), fb("()", "()", 1), o(")", ")"), solve()},
        },
    };

    private static final int[] TAB_LABELS = {R.string.tab_digits, R.string.tab_functions, R.string.tab_symbols};

    private final Listener listener;
    private final FrameLayout host;
    private final TextView[] tabs = new TextView[3];
    private final View[] pages = new View[3];
    private int current = -1;

    KeypadView(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setOrientation(VERTICAL);
        int pad = Ui.dp(c, 8);
        setPadding(pad, Ui.dp(c, 10), pad, pad);

        LinearLayout tabRow = new LinearLayout(c);
        tabRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            TextView t = Ui.text(c, c.getString(TAB_LABELS[i]), 14, R.color.on_surface_variant, true);
            t.setGravity(Gravity.CENTER);
            t.setClickable(true);
            t.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { select(index); }
            });
            tabs[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 36), 1f);
            lp.setMargins(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
            tabRow.addView(t, lp);
        }
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.setMargins(0, 0, 0, Ui.dp(c, 6));
        addView(tabRow, tabsLp);

        host = new FrameLayout(c);
        for (int i = 0; i < PAGES.length; i++) {
            View page = buildPage(c, PAGES[i]);
            page.setVisibility(GONE);
            pages[i] = page;
            host.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        addView(host, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        select(0);
    }

    private View buildPage(Context c, Key[][] rows) {
        LinearLayout page = new LinearLayout(c);
        page.setOrientation(VERTICAL);
        for (Key[] row : rows) {
            LinearLayout line = new LinearLayout(c);
            line.setOrientation(HORIZONTAL);
            for (Key key : row) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f);
                int m = Ui.dp(c, 3);
                lp.setMargins(m, m, m, m);
                line.addView(keyView(c, key), lp);
            }
            page.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return page;
    }

    private View keyView(Context c, final Key key) {
        TextView t = new TextView(c);
        t.setText(key.label);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setClickable(true);
        float radius = Ui.dp(c, 14);
        int ripple = 0x33000000;
        int bg;
        int text;
        float size;
        boolean bold;
        switch (key.kind) {
            case OP:
                bg = Ui.color(c, R.color.key_op); text = Ui.color(c, R.color.on_accent_container);
                size = 20; bold = true; break;
            case FUNC:
                bg = Ui.color(c, R.color.key_func); text = Ui.color(c, R.color.on_surface);
                size = 15; bold = false; break;
            case ACTION:
                bg = Ui.color(c, R.color.surface_variant); text = Ui.color(c, R.color.on_surface);
                size = 17; bold = true; break;
            case SOLVE:
                bg = Ui.color(c, R.color.key_action); text = Ui.color(c, R.color.on_accent);
                size = 14; bold = true; break;
            default:
                bg = Ui.color(c, R.color.key_digit); text = Ui.color(c, R.color.on_surface);
                size = 22; bold = false; break;
        }
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(text);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(Ui.ripple(bg, radius, ripple));
        Ui.press(t);
        t.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dispatch(key); }
        });
        return t;
    }

    private void dispatch(Key key) {
        if (key.insert != null) {
            listener.onInsert(key.insert, key.back);
        } else if (key.kind == SOLVE) {
            listener.onSolve();
        } else if ("⌫".equals(key.label)) {
            listener.onDelete();
        } else {
            listener.onClear();
        }
    }

    /** Переключение страницы с направленной анимацией. */
    void select(int index) {
        if (index == current) return;
        final int prev = current;
        current = index;
        for (int i = 0; i < tabs.length; i++) styleTab(i, i == index);

        float dir = prev < 0 || index > prev ? 1f : -1f;
        float shift = Ui.dp(getContext(), 24) * dir;
        if (prev >= 0) {
            final View old = pages[prev];
            old.animate().alpha(0f).translationX(-shift).setDuration(120)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            if (current != prev) {
                                pages[prev].setVisibility(GONE);
                                pages[prev].setTranslationX(0f);
                            }
                        }
                    }).start();
        }
        View nw = pages[index];
        nw.animate().cancel();
        nw.setVisibility(VISIBLE);
        nw.setAlpha(0f);
        nw.setTranslationX(shift);
        nw.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void styleTab(int i, boolean selected) {
        TextView t = tabs[i];
        Context c = getContext();
        if (selected) {
            t.setBackground(Ui.rounded(Ui.color(c, R.color.accent_container), Ui.dp(c, 18)));
            t.setTextColor(Ui.color(c, R.color.on_accent_container));
        } else {
            t.setBackground(Ui.ripple(0x00000000, Ui.dp(c, 18), 0x22000000));
            t.setTextColor(Ui.color(c, R.color.on_surface_variant));
        }
    }
}
