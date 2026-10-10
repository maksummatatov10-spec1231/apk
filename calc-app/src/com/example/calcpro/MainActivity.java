package com.example.calcpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Solver;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String DEL = "<DEL>";
    private static final String CLR = "<CLR>";

    /** Клавиатура: строки пар «подпись, вставляемый текст». */
    private static final String[][] KEYS = {
        {"sin", "sin(", "cos", "cos(", "tan", "tan(", "ln", "ln(", "log", "log("},
        {"x", "x", "x²", "^2", "xⁿ", "^", "√", "√(", "π", "π"},
        {"(", "(", ")", ")", "!", "!", "%", "%", "⌫", DEL},
        {"7", "7", "8", "8", "9", "9", "÷", "÷", "C", CLR},
        {"4", "4", "5", "5", "6", "6", "×", "×", "−", "−"},
        {"1", "1", "2", "2", "3", "3", "+", "+", "=", "="},
        {"0", "0", ",", ",", "e", "e", "|x|", "abs(", ".", "."},
    };

    private EditText input;
    private ScrollView scroll;
    private LinearLayout results;
    private LinearLayout keypad;
    private final List<String> history = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        input = findViewById(R.id.input);
        scroll = findViewById(R.id.scroll);
        results = findViewById(R.id.results);
        keypad = findViewById(R.id.keypad);

        buildKeypad();
        findViewById(R.id.btn_solve).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { solve(); }
        });
        findViewById(R.id.btn_history).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHistory(); }
        });
        findViewById(R.id.btn_clear_results).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                results.removeAllViews();
                addWelcomeCard();
            }
        });
        findViewById(R.id.btn_keypad).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                keypad.setVisibility(keypad.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView tv, int actionId, KeyEvent ev) {
                solve();
                return true;
            }
        });
        addWelcomeCard();
    }

    // ---------- клавиатура ----------

    private void buildKeypad() {
        for (String[] row : KEYS) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < row.length; i += 2) {
                Button b = keyButton(row[i], row[i + 1]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                line.addView(b, lp);
            }
            keypad.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private Button keyButton(String label, String insert) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        boolean action = "=".equals(insert) || CLR.equals(insert) || DEL.equals(insert);
        b.setTextColor(color(action ? R.color.accent : R.color.on_surface));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(ripple(color(R.color.key_bg), dp(12)));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onKey(insert); }
        });
        return b;
    }

    private void onKey(String insert) {
        Editable text = input.getText();
        if (DEL.equals(insert)) {
            int s = input.getSelectionStart(), e = input.getSelectionEnd();
            if (s < 0) s = text.length();
            if (e < 0) e = s;
            if (s != e) {
                text.delete(Math.min(s, e), Math.max(s, e));
            } else if (s > 0) {
                text.delete(s - 1, s);
            }
            return;
        }
        if (CLR.equals(insert)) {
            text.clear();
            return;
        }
        int s = input.getSelectionStart(), e = input.getSelectionEnd();
        if (s < 0) s = text.length();
        if (e < 0) e = s;
        int start = Math.min(s, e), end = Math.max(s, e);
        text.replace(start, end, insert);
        input.setSelection(start + insert.length());
    }

    // ---------- решение ----------

    private void solve() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Введите выражение или уравнение", Toast.LENGTH_SHORT).show();
            return;
        }
        history.remove(q);
        history.add(0, q);
        if (history.size() > 50) history.remove(history.size() - 1);
        try {
            addResultCard(Solver.solve(q), q);
        } catch (MathError e) {
            addErrorCard(q, e.getMessage());
        } catch (RuntimeException e) {
            addErrorCard(q, "Не удалось решить: проверьте запись");
        }
        scroll.post(new Runnable() {
            @Override public void run() { scroll.smoothScrollTo(0, 0); }
        });
    }

    private void showHistory() {
        if (history.isEmpty()) {
            Toast.makeText(this, "История пуста", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("История")
                .setItems(history.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        input.setText(history.get(which));
                        input.setSelection(input.getText().length());
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    // ---------- карточки ----------

    private void addResultCard(Solver.Result res, String query) {
        LinearLayout card = card();

        TextView head = text(res.heading.toUpperCase(), 12, R.color.accent, true);
        head.setLetterSpacing(0.08f);
        card.addView(head);

        TextView q = text(query, 16, R.color.on_surface_variant, false);
        q.setTypeface(Typeface.MONOSPACE);
        q.setPadding(0, dp(4), 0, dp(8));
        q.setTextIsSelectable(true);
        card.addView(q);

        for (String ans : res.answers) {
            TextView t = text(ans, 20, R.color.on_accent_container, true);
            t.setTextIsSelectable(true);
            t.setBackground(rounded(color(R.color.accent_container), dp(14)));
            t.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(6), 0, dp(2));
            card.addView(t, lp);
        }

        if (res.graph != null) {
            GraphView gv = new GraphView(this, res.graph, res.marks,
                    color(R.color.graph_grid), color(R.color.graph_axis),
                    color(R.color.accent), color(R.color.graph_mark),
                    color(R.color.on_surface_variant));
            gv.setBackground(rounded(color(R.color.key_bg), dp(14)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230));
            lp.setMargins(0, dp(10), 0, 0);
            card.addView(gv, lp);
        }

        if (!res.steps.isEmpty()) {
            TextView title = text("Решение по шагам", 14, R.color.on_surface, true);
            title.setPadding(0, dp(14), 0, dp(4));
            card.addView(title);
            int n = 0;
            for (String step : res.steps) {
                boolean indented = step.startsWith(" ");
                if (!indented) n++;
                TextView t = text((indented ? "" : n + ". ") + step.trim(), 15, R.color.on_surface, false);
                t.setPadding(indented ? dp(16) : 0, dp(3), 0, dp(3));
                t.setLineSpacing(dp(2), 1f);
                t.setTextIsSelectable(true);
                card.addView(t);
            }
        }
        addCard(card);
    }

    private void addErrorCard(String query, String message) {
        LinearLayout card = card();
        TextView head = text("ОШИБКА", 12, R.color.error, true);
        head.setLetterSpacing(0.08f);
        card.addView(head);
        TextView q = text(query, 16, R.color.on_surface_variant, false);
        q.setTypeface(Typeface.MONOSPACE);
        card.addView(q);
        TextView m = text(message, 16, R.color.error, false);
        m.setPadding(0, dp(8), 0, 0);
        card.addView(m);
        addCard(card);
    }

    private void addWelcomeCard() {
        LinearLayout card = card();
        TextView head = text("ВОЗМОЖНОСТИ", 12, R.color.accent, true);
        head.setLetterSpacing(0.08f);
        card.addView(head);
        TextView t = text("Вычисляю выражения с пошаговым решением, решаю уравнения и системы, "
                + "показываю график функции и отмечаю корни. Нажмите пример, чтобы решить его:", 15, R.color.on_surface, false);
        t.setPadding(0, dp(6), 0, dp(8));
        card.addView(t);
        String[] examples = {
            "2x² − 3x + 1 = 0",
            "x³ − 6x² + 11x − 6 = 0",
            "(2 + 3) · 4 ÷ 5 − 2^3",
            "sin(x) = 0,5",
            "x + y = 5; 2x − y = 1",
            "√(x + 4) = 3",
        };
        for (String ex : examples) {
            TextView e = text(ex, 16, R.color.accent, true);
            e.setTypeface(Typeface.MONOSPACE);
            e.setBackground(rounded(color(R.color.key_bg), dp(12)));
            e.setPadding(dp(14), dp(10), dp(14), dp(10));
            e.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    input.setText(ex);
                    input.setSelection(ex.length());
                    solve();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(4), 0, dp(4));
            card.addView(e, lp);
        }
        addCard(card);
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        results.addView(card, 0, lp);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        card.setBackground(rounded(color(R.color.card), dp(20)));
        return card;
    }

    private TextView text(String s, int sp, int colorRes, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(colorRes));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.START);
        return t;
    }

    // ---------- утилиты ----------

    private int color(int res) {
        return getResources().getColor(res);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private RippleDrawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), rounded(color, radius), null);
    }
}
