package com.example.calcpro;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.calcpro.math.Solver;

/** Построение карточек: результаты, ошибки, приветствие, строки списков. */
final class Cards {
    private Cards() {}

    interface Picker {
        void pick(String query);
    }

    static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(c, 16);
        card.setPadding(p, Ui.dp(c, 14), p, Ui.dp(c, 16));
        card.setBackground(Ui.rounded(Ui.color(c, R.color.surface_high), Ui.dp(c, 22)));
        return card;
    }

    static LinearLayout.LayoutParams cardParams(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(c, 12));
        return lp;
    }

    static void addResult(Context c, LinearLayout container, Solver.Result res, String query, int index) {
        LinearLayout card = card(c);

        TextView head = Ui.text(c, res.heading.toUpperCase(), 12, R.color.accent, true);
        head.setLetterSpacing(0.08f);
        card.addView(head);

        TextView q = Ui.text(c, query, 16, R.color.on_surface_variant, false);
        q.setTypeface(Typeface.MONOSPACE);
        q.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        q.setTextIsSelectable(true);
        card.addView(q);

        for (final String ans : res.answers) {
            TextView t = Ui.text(c, ans, 20, R.color.on_accent_container, true);
            t.setTextIsSelectable(true);
            t.setBackground(Ui.ripple(Ui.color(c, R.color.accent_container), Ui.dp(c, 16), 0x33000000));
            t.setPadding(Ui.dp(c, 16), Ui.dp(c, 12), Ui.dp(c, 16), Ui.dp(c, 12));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { copy(v.getContext(), ans); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
            card.addView(t, lp);
        }

        if (res.graph != null) {
            GraphView gv = new GraphView(c, res.graph, res.marks,
                    Ui.color(c, R.color.graph_grid), Ui.color(c, R.color.graph_axis),
                    Ui.color(c, R.color.accent), Ui.color(c, R.color.graph_mark),
                    Ui.color(c, R.color.on_surface_variant));
            gv.setBackground(Ui.rounded(Ui.color(c, R.color.key_digit), Ui.dp(c, 16)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 240));
            lp.setMargins(0, Ui.dp(c, 10), 0, 0);
            card.addView(gv, lp);
        }

        if (!res.steps.isEmpty()) {
            TextView title = Ui.text(c, "Решение по шагам", 14, R.color.on_surface, true);
            title.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 6));
            card.addView(title);
            int n = 0;
            for (String step : res.steps) {
                boolean indented = step.startsWith(" ");
                if (!indented) n++;
                String body = step.trim();
                if (indented) {
                    TextView t = Ui.text(c, body, 14, R.color.on_surface_variant, false);
                    t.setPadding(Ui.dp(c, 36), Ui.dp(c, 2), 0, Ui.dp(c, 2));
                    t.setTextIsSelectable(true);
                    card.addView(t);
                } else {
                    card.addView(stepRow(c, n, body));
                }
            }
        }
        container.addView(card, index, cardParams(c));
        Ui.appear(card, 0);
    }

    /** Шаг решения: кружок с номером и текст. */
    private static LinearLayout stepRow(Context c, int n, String body) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));

        TextView num = Ui.text(c, String.valueOf(n), 12, R.color.on_accent_container, true);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.rounded(Ui.color(c, R.color.accent_container), Ui.dp(c, 12)));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        nl.setMargins(0, Ui.dp(c, 1), Ui.dp(c, 10), 0);
        row.addView(num, nl);

        TextView t = Ui.text(c, body, 15, R.color.on_surface, false);
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        t.setTextIsSelectable(true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    static void addError(Context c, LinearLayout container, String query, String message, int index) {
        LinearLayout card = card(c);
        TextView head = Ui.text(c, "ОШИБКА", 12, R.color.error, true);
        head.setLetterSpacing(0.08f);
        card.addView(head);
        TextView q = Ui.text(c, query, 16, R.color.on_surface_variant, false);
        q.setTypeface(Typeface.MONOSPACE);
        card.addView(q);
        TextView m = Ui.text(c, message, 16, R.color.error, false);
        m.setPadding(0, Ui.dp(c, 8), 0, 0);
        card.addView(m);
        container.addView(card, index, cardParams(c));
        Ui.appear(card, 0);
    }

    /** Приветствие: короткое описание и быстрые примеры. */
    static void addWelcome(final Context c, LinearLayout container, final Picker picker, final Runnable openEquations) {
        LinearLayout card = card(c);
        TextView head = Ui.text(c, "ЧТО МОЖНО СДЕЛАТЬ", 12, R.color.accent, true);
        head.setLetterSpacing(0.08f);
        card.addView(head);

        TextView t = Ui.text(c, c.getString(R.string.welcome_text), 15, R.color.on_surface, false);
        t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 10));
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        card.addView(t);

        String[] examples = {"2x² − 3x + 1 = 0", "x³ − 6x² + 11x − 6 = 0", "(2 + 3) · 4 ÷ 5 − 2^3", "sin(x) = 0,5"};
        for (final String ex : examples) {
            TextView e = Ui.text(c, ex, 15, R.color.accent, true);
            e.setTypeface(Typeface.MONOSPACE);
            e.setBackground(Ui.ripple(Ui.color(c, R.color.key_digit), Ui.dp(c, 14), 0x22000000));
            e.setPadding(Ui.dp(c, 14), Ui.dp(c, 11), Ui.dp(c, 14), Ui.dp(c, 11));
            e.setClickable(true);
            e.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { picker.pick(ex); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
            card.addView(e, lp);
        }

        TextView more = Ui.text(c, c.getString(R.string.welcome_more), 14, R.color.accent, true);
        more.setPadding(0, Ui.dp(c, 10), 0, 0);
        more.setClickable(true);
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openEquations.run(); }
        });
        card.addView(more);
        container.addView(card, 0, cardParams(c));
        Ui.appear(card, 0);
    }

    /** Строка списка: заголовок (выражение), подпись и стрелка. */
    static View row(Context c, String title, String note, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 14);
        row.setPadding(p, Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12));
        row.setBackground(Ui.ripple(Ui.color(c, R.color.surface_high), Ui.dp(c, 18), 0x22000000));
        row.setClickable(true);
        row.setOnClickListener(onClick);
        Ui.press(row);

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(c, title, 17, R.color.accent, true);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        text.addView(t);
        if (note != null && !note.isEmpty()) {
            TextView n = Ui.text(c, note, 13, R.color.on_surface_variant, false);
            n.setPadding(0, Ui.dp(c, 2), 0, 0);
            text.addView(n);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView arrow = new ImageView(c);
        arrow.setImageDrawable(c.getDrawable(R.drawable.ic_chevron));
        arrow.setColorFilter(Ui.color(c, R.color.outline));
        row.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        return row;
    }

    /** Заголовок раздела списка. */
    static TextView sectionTitle(Context c, String title) {
        TextView t = Ui.text(c, title.toUpperCase(), 12, R.color.accent, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 18), 0, Ui.dp(c, 8));
        return t;
    }

    static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Результат", text));
        Toast.makeText(c, R.string.copied, Toast.LENGTH_SHORT).show();
    }
}
