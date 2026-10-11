package com.example.calcpro;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Обычный и научный калькулятор в стиле Calculator²: строка выражения, крупный дисплей,
 * сетка кнопок. Вся логика ввода — в {@link CalcEngine}; здесь только экран и нажатия.
 */
final class CalcPanel extends LinearLayout {

    interface Listener {
        /** Ввод похож на уравнение или график — решает экран «Уравнения». */
        void onEquation(String text);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Listener listener;
    private final boolean scientific;
    private final int keyHeightDp;
    private final CalcEngine engine = new CalcEngine();

    private TextView exprView;
    private TextView displayView;
    private TextView memIndicator;
    private TextView angleView;
    private LinearLayout memStrip;
    private TextView backKey;

    CalcPanel(Context c, boolean scientific, Listener listener) {
        super(c);
        this.scientific = scientific;
        this.keyHeightDp = scientific ? 48 : 60;
        this.listener = listener;
        setOrientation(VERTICAL);
        int p = Ui.dp(c, 12);
        setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 12));

        View display = buildDisplay(c);
        if (scientific) {
            LinearLayout body = new LinearLayout(c);
            body.setOrientation(VERTICAL);
            body.addView(display, new LinearLayout.LayoutParams(MATCH, Ui.dp(c, 150)));
            body.addView(buildFunctionGrid(c), new LinearLayout.LayoutParams(MATCH, WRAP));
            body.addView(buildMemStrip(c), new LinearLayout.LayoutParams(MATCH, WRAP));
            body.addView(buildMainGrid(c), new LinearLayout.LayoutParams(MATCH, WRAP));
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(body, new ScrollView.LayoutParams(MATCH, WRAP));
            addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        } else {
            addView(display, new LinearLayout.LayoutParams(MATCH, 0, 1f));
            addView(buildMemStrip(c), new LinearLayout.LayoutParams(MATCH, WRAP));
            addView(buildMainGrid(c), new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        refresh();
    }

    // ---------- дисплей ----------

    private View buildDisplay(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.BOTTOM);
        int p = Ui.dp(c, 16);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.rounded(Ui.color(c, R.color.surface_high), Ui.dp(c, 20)));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        exprView = Ui.text(c, " ", 18, R.color.on_surface_variant, false);
        exprView.setSingleLine(true);
        exprView.setTypeface(Typeface.MONOSPACE);
        hs.addView(exprView, new HorizontalScrollView.LayoutParams(WRAP, WRAP));
        top.addView(hs, new LinearLayout.LayoutParams(0, WRAP, 1f));

        if (scientific) {
            angleView = Ui.text(c, "DEG", 12, R.color.accent, true);
            angleView.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
            angleView.setBackground(Ui.rounded(Ui.color(c, R.color.accent_container), Ui.dp(c, 10)));
            angleView.setClickable(true);
            angleView.setContentDescription("Единицы углов: градусы или радианы");
            angleView.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    engine.setDegrees(!engine.degrees());
                    refresh();
                }
            });
            top.addView(angleView, new LinearLayout.LayoutParams(WRAP, WRAP));
        }
        card.addView(top, new LinearLayout.LayoutParams(MATCH, WRAP));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        memIndicator = Ui.text(c, "M", 13, R.color.accent, true);
        memIndicator.setVisibility(GONE);
        row.addView(memIndicator, new LinearLayout.LayoutParams(WRAP, WRAP));
        displayView = Ui.text(c, "0", 44, R.color.on_surface, false);
        displayView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        displayView.setSingleLine(true);
        row.addView(displayView, new LinearLayout.LayoutParams(0, WRAP, 1f));
        card.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return card;
    }

    /** Перерисовка дисплея и строки выражения из состояния движка. */
    private void refresh() {
        String e = engine.expression();
        exprView.setText(e.isEmpty() ? " " : e);
        String shown = engine.display();
        displayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, shown.length() > 12 ? 28 : 44);
        displayView.setText(shown);
        memIndicator.setVisibility(engine.hasMemory() ? VISIBLE : GONE);
        if (angleView != null) angleView.setText(engine.degrees() ? "DEG" : "RAD");
    }

    // ---------- сетки кнопок ----------

    private View buildMainGrid(Context c) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(4);
        g.setRowCount(6);
        g.setUseDefaultMargins(false);

        place(g, key(c, "Mem", KEY_FUNC, new Runnable() { @Override public void run() { toggleMem(); } }), 0, 0, 1, 1);
        backKey = key(c, "⌫", KEY_FUNC, null);
        place(g, backKey, 0, 1, 1, 1);
        Ui.hold(backKey, new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.backspace(); } }); } });
        place(g, key(c, "CE", KEY_FUNC, new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.clearEntry(); } }); } }), 0, 2, 1, 1);
        place(g, key(c, "C", KEY_FUNC, new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.clearAll(); } }); } }), 0, 3, 1, 1);

        place(g, key(c, "±", KEY_FUNC, new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.negate(); } }); } }), 1, 0, 1, 1);
        place(g, token(c, "%", KEY_FUNC, "%"), 1, 1, 1, 1);
        place(g, token(c, "÷", KEY_OP, "÷"), 1, 2, 1, 1);
        place(g, token(c, "×", KEY_OP, "×"), 1, 3, 1, 1);

        String[][] digits = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}};
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 3; col++) {
                place(g, token(c, digits[r][col], KEY_DIGIT, digits[r][col]), 2 + r, col, 1, 1);
            }
        }
        place(g, token(c, "−", KEY_OP, "−"), 2, 3, 1, 1);
        place(g, token(c, "+", KEY_OP, "+"), 3, 3, 1, 1);
        place(g, key(c, "=", KEY_EQUALS, new Runnable() { @Override public void run() { evaluate(); } }), 4, 3, 2, 1);
        place(g, token(c, "0", KEY_DIGIT, "0"), 5, 0, 1, 2);
        place(g, token(c, ".", KEY_DIGIT, "."), 5, 2, 1, 1);
        return g;
    }

    /** Научные функции: тригонометрия, логарифмы, корни, степени, константы. */
    private View buildFunctionGrid(Context c) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(5);
        g.setRowCount(5);
        g.setUseDefaultMargins(false);
        String[][] rows = {
            {"sin", "cos", "tan", "(", ")"},
            {"asin", "acos", "atan", "ln", "log"},
            {"√", "∛", "abs", "eˣ", "10ˣ"},
            {"x²", "xʸ", "n!", "1/x", "π"},
            {"e", null, null, null, null},
        };
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length; col++) {
                final String label = rows[r][col];
                if (label == null) continue;
                TextView k;
                if (label.equals("1/x")) {
                    k = key(c, label, KEY_FUNC, new Runnable() { @Override public void run() {
                        press(new Runnable() { @Override public void run() { engine.reciprocal(); } });
                    } });
                } else if (label.equals("x²")) {
                    k = key(c, label, KEY_FUNC, new Runnable() { @Override public void run() {
                        press(new Runnable() { @Override public void run() { engine.press("^"); engine.press("2"); } });
                    } });
                } else {
                    k = token(c, label, KEY_FUNC, insertFor(label));
                }
                place(g, k, r, col, 1, 1);
            }
        }
        return g;
    }

    /** Что вставляет клавиша научного ряда. «x²» и «xʸ» — степень в две-три клавиши ввода. */
    private static String insertFor(String label) {
        switch (label) {
            case "sin": return "sin(";
            case "cos": return "cos(";
            case "tan": return "tan(";
            case "asin": return "asin(";
            case "acos": return "acos(";
            case "atan": return "atan(";
            case "ln": return "ln(";
            case "log": return "log(";
            case "√": return "√(";
            case "∛": return "∛(";
            case "abs": return "abs(";
            case "eˣ": return "e^";
            case "10ˣ": return "10^";
            case "xʸ": return "^";
            case "n!": return "!";
            case "π": return "π";
            case "e": return "e";
            case "(": return "(";
            case ")": return ")";
            default: return label;
        }
    }

    /** Память: MC, MR, M+, M− в отдельной полоске, открывается кнопкой «Mem». */
    private View buildMemStrip(Context c) {
        memStrip = new LinearLayout(c);
        memStrip.setOrientation(HORIZONTAL);
        memStrip.setVisibility(GONE);
        memStrip.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        Runnable[] actions = {
            new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.memoryClear(); } }); } },
            new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.memoryRecall(); } }); } },
            new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.memoryAdd(true); } }); } },
            new Runnable() { @Override public void run() { press(new Runnable() { @Override public void run() { engine.memoryAdd(false); } }); } },
        };
        String[] labels = {"MC", "MR", "M+", "M−"};
        for (int i = 0; i < labels.length; i++) {
            TextView k = key(c, labels[i], KEY_FUNC, actions[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f);
            int m = Ui.dp(c, 4);
            lp.setMargins(m, 0, m, 0);
            memStrip.addView(k, lp);
        }
        return memStrip;
    }

    // ---------- кнопки ----------

    private static final int KEY_DIGIT = 0, KEY_FUNC = 1, KEY_OP = 2, KEY_EQUALS = 3;

    /** Кнопка, вставляющая текст в выражение. */
    private TextView token(Context c, String label, int kind, final String insert) {
        return key(c, label, kind, new Runnable() {
            @Override public void run() {
                press(new Runnable() { @Override public void run() { engine.press(insert); } });
            }
        });
    }

    private TextView key(Context c, String label, int kind, Runnable action) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setClickable(true);
        int bg, fg;
        float size;
        boolean bold = false;
        switch (kind) {
            case KEY_OP:
                bg = Ui.color(c, R.color.key_op); fg = Ui.color(c, R.color.on_accent_container);
                size = 24; bold = true; break;
            case KEY_FUNC:
                bg = Ui.color(c, R.color.key_func); fg = Ui.color(c, R.color.on_surface);
                size = label.length() > 3 ? 14 : 17; break;
            case KEY_EQUALS:
                bg = Ui.color(c, R.color.key_action); fg = Ui.color(c, R.color.on_accent);
                size = 30; bold = true; break;
            default:
                bg = Ui.color(c, R.color.key_digit); fg = Ui.color(c, R.color.on_surface);
                size = 24; break;
        }
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(fg);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setBackground(Ui.ripple(bg, Ui.dp(c, 16), 0x33000000));
        if (action != null) {
            Ui.press(t);
            final Runnable a = action;
            t.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { a.run(); }
            });
        }
        return t;
    }

    private void place(GridLayout g, View v, int row, int col, int rowSpan, int colSpan) {
        Context c = getContext();
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(row, rowSpan, GridLayout.FILL, 1f),
                GridLayout.spec(col, colSpan, GridLayout.FILL, 1f));
        int m = Ui.dp(c, 4);
        lp.setMargins(m, m, m, m);
        lp.height = Ui.dp(c, keyHeightDp) * rowSpan;
        lp.width = 0;
        g.addView(v, lp);
    }

    // ---------- действия ----------

    /** Любое нажатие: выполнить действие движка и перерисовать экран. */
    private void press(Runnable action) {
        action.run();
        refresh();
        String err = engine.error();
        if (err != null) {
            Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
            Ui.shake(this);
        }
    }

    private void toggleMem() {
        memStrip.setVisibility(memStrip.getVisibility() == VISIBLE ? GONE : VISIBLE);
    }

    /** «=»: считает выражение, сохраняет в историю; уравнения уходят в раздел «Уравнения». */
    private void evaluate() {
        CalcEngine.Outcome o = engine.equals();
        refresh();
        if (o.needsEquations) {
            listener.onEquation(o.expression);
            return;
        }
        if (o.ok()) {
            HistoryStore.add(getContext(), o.expression);
        } else if (o.error != null) {
            Toast.makeText(getContext(), o.error, Toast.LENGTH_LONG).show();
            Ui.shake(this);
        }
    }
}
