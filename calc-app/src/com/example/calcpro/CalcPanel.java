package com.example.calcpro;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.View;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Solver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Калькулятор в стиле Calculator²: строка выражения сверху, крупный дисплей результата,
 * сетка кнопок. Научный режим добавляет сетку функций над основной сеткой.
 * «=» считает выражение; если введено уравнение или график — передаёт его режиму «Уравнения».
 */
final class CalcPanel extends LinearLayout {

    interface Listener {
        /** Ввод похож на уравнение, систему или график — решает экран «Уравнения». */
        void onEquation(String text);
    }

    /** Последний операнд: скобочная группа (возможно с вложенной скобкой) или число. */
    private static final Pattern LAST_OPERAND = Pattern.compile(
            "\\((?:[^()]|\\([^()]*\\))*\\)$|[0-9]+[.,]?[0-9]*$");
    private static final Pattern VARIABLE_X = Pattern.compile("(?<![a-zA-Z])x(?![a-zA-Z])");
    private static final String OPERATORS = "+−×÷^%";

    private final Listener listener;
    private final boolean scientific;
    private final int keyHeightDp;

    private String text = "";                 // выражение, как его набрали
    private String result = null;             // результат последнего «=» для дисплея
    private String resultOperand = null;      // тот же результат как операнд: "(19/6)"
    private boolean justEvaluated;
    private String memory = "";               // память: выражение-операнд

    private TextView exprView;
    private TextView displayView;
    private TextView memIndicator;
    private LinearLayout memStrip;

    CalcPanel(Context c, boolean scientific, Listener listener) {
        super(c);
        this.scientific = scientific;
        this.keyHeightDp = scientific ? 50 : 60;
        this.listener = listener;
        setOrientation(VERTICAL);
        int p = Ui.dp(c, 12);
        setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 12));

        View display = buildDisplay(c);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, scientific ? Ui.dp(c, 150) : 0, scientific ? 0f : 1f);
        if (scientific) {
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout body = new LinearLayout(c);
            body.setOrientation(VERTICAL);
            body.addView(display, dlp);
            body.addView(buildFunctionGrid(c), wrapLp(c));
            body.addView(buildMemStrip(c), wrapLp(c));
            body.addView(buildMainGrid(c), wrapLp(c));
            sv.addView(body, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            addView(display, dlp);
            addView(buildMemStrip(c), wrapLp(c));
            addView(buildMainGrid(c), wrapLp(c));
        }
        refresh();
    }

    private static LinearLayout.LayoutParams wrapLp(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---------- дисплей ----------

    private View buildDisplay(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setGravity(Gravity.BOTTOM);
        int p = Ui.dp(c, 16);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.rounded(Ui.color(c, R.color.surface_high), Ui.dp(c, 20)));

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        exprView = Ui.text(c, " ", 18, R.color.on_surface_variant, false);
        exprView.setSingleLine(true);
        exprView.setTypeface(Typeface.MONOSPACE);
        exprView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        hs.addView(exprView, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        memIndicator = Ui.text(c, "M", 13, R.color.accent, true);
        memIndicator.setVisibility(GONE);
        row.addView(memIndicator, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        displayView = Ui.text(c, "0", 44, R.color.on_surface, false);
        displayView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        displayView.setSingleLine(true);
        row.addView(displayView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    /** Показ дисплея: результат, если был «=», иначе последнее набранное число. */
    private void refresh() {
        exprView.setText(text.isEmpty() ? " " : text);
        String shown;
        if (result != null) {
            shown = result;
        } else {
            shown = currentNumber();
        }
        displayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, shown.length() > 12 ? 28 : 44);
        displayView.setText(shown);
        memIndicator.setVisibility(memory.isEmpty() ? GONE : VISIBLE);
    }

    /** Показ последнего операнда: «(−5)» отображается как «−5». */
    private String currentNumber() {
        if (text.isEmpty()) return "0";
        Matcher m = LAST_OPERAND.matcher(text);
        if (!m.find()) return "0";
        String g = m.group();
        if (g.startsWith("(") && g.endsWith(")")) g = g.substring(1, g.length() - 1);
        return g;
    }

    // ---------- сетки кнопок ----------

    private View buildMainGrid(Context c) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(4);
        g.setRowCount(6);
        place(g, key(c, "Mem", KEY_FUNC, new Runnable() { @Override public void run() { toggleMem(); } }), 0, 0, 1, 1);
        TextView back = key(c, "←", KEY_FUNC, null);
        place(g, back, 0, 1, 1, 1);
        place(g, key(c, "CE", KEY_FUNC, new Runnable() { @Override public void run() { clearEntry(); } }), 0, 2, 1, 1);
        place(g, key(c, "C", KEY_FUNC, new Runnable() { @Override public void run() { clearAll(); } }), 0, 3, 1, 1);
        // «←» стирает символ; при удержании удаляет всё быстрее, как на ПК.
        Ui.hold(back, new Runnable() { @Override public void run() { deleteBack(); } });

        place(g, key(c, "±", KEY_FUNC, new Runnable() { @Override public void run() { negate(); } }), 1, 0, 1, 1);
        place(g, key(c, "%", KEY_FUNC, new Runnable() { @Override public void run() { insert("%"); } }), 1, 1, 1, 1);
        place(g, key(c, "÷", KEY_OP, new Runnable() { @Override public void run() { insert("÷"); } }), 1, 2, 1, 1);
        place(g, key(c, "×", KEY_OP, new Runnable() { @Override public void run() { insert("×"); } }), 1, 3, 1, 1);

        String[][] digits = {{"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}};
        String[] ops = {"−", "+", null};
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 3; col++) {
                final String d = digits[r][col];
                place(g, key(c, d, KEY_DIGIT, new Runnable() { @Override public void run() { insert(d); } }), 2 + r, col, 1, 1);
            }
            if (ops[r] != null) {
                final String op = ops[r];
                place(g, key(c, op, KEY_OP, new Runnable() { @Override public void run() { insert(op); } }), 2 + r, 3, 1, 1);
            }
        }
        place(g, key(c, "=", KEY_EQUALS, new Runnable() { @Override public void run() { evaluate(); } }), 4, 3, 2, 1);
        place(g, key(c, "0", KEY_DIGIT, new Runnable() { @Override public void run() { insert("0"); } }), 5, 0, 1, 2);
        place(g, key(c, ".", KEY_DIGIT, new Runnable() { @Override public void run() { insert("."); } }), 5, 2, 1, 1);
        return g;
    }

    /** Строка функций для научного режима. */
    private View buildFunctionGrid(Context c) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(5);
        String[][] rows = {
            {"sin", "cos", "tan", "(", ")"},
            {"asin", "acos", "atan", "ln", "log"},
            {"√", "∛", "abs", "eˣ", "10ˣ"},
            {"x²", "x³", "xʸ", "n!", "1/x"},
            {"π", "e", null, null, null},
        };
        for (int r = 0; r < rows.length; r++) {
            for (int col = 0; col < rows[r].length; col++) {
                final String label = rows[r][col];
                if (label == null) continue;
                final String insertText = functionInsert(label);
                if (insertText == null) continue;
                place(g, key(c, label, KEY_FUNC, new Runnable() { @Override public void run() { insert(insertText); } }),
                        r, col, 1, 1);
            }
        }
        return g;
    }

    private static String functionInsert(String label) {
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
            case "x²": return "^2";
            case "x³": return "^3";
            case "xʸ": return "^";
            case "n!": return "!";
            case "1/x": return "1/";
            case "(": return "(";
            case ")": return ")";
            case "π": return "π";
            case "e": return "e";
            default: return null;
        }
    }

    /** Память: MC, MR, M+, M− в отдельной полоске, открывается кнопкой «Mem». */
    private View buildMemStrip(Context c) {
        memStrip = new LinearLayout(c);
        memStrip.setOrientation(HORIZONTAL);
        memStrip.setVisibility(GONE);
        memStrip.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        String[] labels = {"MC", "MR", "M+", "M−"};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView k = key(c, labels[i], KEY_FUNC, new Runnable() {
                @Override public void run() { memoryAction(which); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 44), 1f);
            int m = Ui.dp(c, 4);
            lp.setMargins(m, 0, m, 0);
            memStrip.addView(k, lp);
        }
        return memStrip;
    }

    // ---------- кнопки ----------

    private static final int KEY_DIGIT = 0, KEY_FUNC = 1, KEY_OP = 2, KEY_EQUALS = 3;

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
            t.setOnClickListener(new View.OnClickListener() {
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

    // ---------- ввод ----------

    private void insert(String s) {
        if (justEvaluated) {
            boolean op = OPERATORS.contains(s);
            text = op && resultOperand != null ? resultOperand : "";
            justEvaluated = false;
        }
        result = null;
        text = text + s;
        refresh();
    }

    private void deleteBack() {
        if (justEvaluated) {
            clearAll();
            return;
        }
        if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
        refresh();
    }

    /** CE: стирает последнее число, а если его нет — последний символ. */
    private void clearEntry() {
        if (justEvaluated) {
            clearAll();
            return;
        }
        Matcher m = LAST_OPERAND.matcher(text);
        if (m.find()) {
            text = text.substring(0, m.start());
        } else {
            text = "";
        }
        result = null;
        refresh();
    }

    private void clearAll() {
        text = "";
        result = null;
        resultOperand = null;
        justEvaluated = false;
        refresh();
    }

    /** ±: меняет знак последнего операнда; операнд — число, скобочная группа или степень. */
    private void negate() {
        if (justEvaluated) {
            text = resultOperand == null ? "" : resultOperand;
            justEvaluated = false;
            result = null;
        }
        if (text.isEmpty()) {
            text = "−";
            refresh();
            return;
        }
        int start;
        if (text.endsWith(")")) {
            int depth = 0;
            start = -1;
            for (int i = text.length() - 1; i >= 0; i--) {
                char ch = text.charAt(i);
                if (ch == ')') depth++;
                else if (ch == '(' && --depth == 0) { start = i; break; }
            }
            if (start < 0) start = 0;
        } else {
            start = 0;
            for (int i = text.length() - 1; i >= 0; i--) {
                if (OPERATORS.indexOf(text.charAt(i)) >= 0 || text.charAt(i) == '(') {
                    start = i + 1;
                    break;
                }
            }
        }
        String operand = text.substring(start);
        if (operand.isEmpty()) return;
        if (operand.startsWith("(−") && operand.endsWith(")") && isSingleGroup(operand)) {
            operand = operand.substring(2, operand.length() - 1);
        } else {
            operand = "(−" + operand + ")";
        }
        text = text.substring(0, start) + operand;
        result = null;
        refresh();
    }

    /** Проверяет, что скобка в начале закрывается в самом конце строки. */
    private static boolean isSingleGroup(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') {
                depth--;
                if (depth == 0 && i != s.length() - 1) return false;
            }
        }
        return depth == 0;
    }

    private void toggleMem() {
        memStrip.setVisibility(memStrip.getVisibility() == VISIBLE ? GONE : VISIBLE);
    }

    /** Текущее значение как операнд: результат после «=» или введённое выражение. */
    private String currentOperand() {
        if (justEvaluated && resultOperand != null) return resultOperand;
        if (text.isEmpty()) return "0";
        return "(" + text + ")";
    }

    private void memoryAction(int which) {
        switch (which) {
            case 0: // MC
                memory = "";
                break;
            case 1: // MR
                if (memory.isEmpty()) {
                    Toast.makeText(getContext(), "Память пуста", Toast.LENGTH_SHORT).show();
                    return;
                }
                insert("(" + memory + ")");
                return;
            case 2: // M+
                memory = memory.isEmpty() ? currentOperand() : memory + "+" + currentOperand();
                break;
            default: // M−
                memory = memory.isEmpty() ? "−" + currentOperand() : memory + "−" + currentOperand();
                break;
        }
        refresh();
    }

    // ---------- «=» ----------

    private void evaluate() {
        String q = text.trim();
        if (q.isEmpty()) return;
        if (q.contains("=") || q.contains(";") || VARIABLE_X.matcher(q).find()) {
            listener.onEquation(q);
            return;
        }
        try {
            Solver.Result res = Solver.solve(q);
            String a = res.answers.isEmpty() ? "" : res.answers.get(0);
            String prefix = "Ответ: ";
            if (!a.startsWith(prefix)) {
                listener.onEquation(q);
                return;
            }
            String value = a.substring(prefix.length());
            int approx = value.indexOf(" ≈ ");
            String exact = approx >= 0 ? value.substring(0, approx) : value;
            String decimal = approx >= 0 ? value.substring(approx + 3) : value;
            HistoryStore.add(getContext(), q);
            resultOperand = "(" + exact + ")";
            result = decimal;
            justEvaluated = true;
            refresh();
        } catch (MathError e) {
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            Ui.shake(this);
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), "Не удалось вычислить: проверьте запись", Toast.LENGTH_LONG).show();
            Ui.shake(this);
        }
    }
}
