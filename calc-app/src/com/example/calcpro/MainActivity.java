package com.example.calcpro;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Solver;

import java.util.List;

public class MainActivity extends Activity {

    static final int SCREEN_CALC = 0;
    static final int SCREEN_EQUATIONS = 1;
    static final int SCREEN_EXAMPLES = 2;
    static final int SCREEN_HISTORY = 3;
    static final int SCREEN_HELP = 4;

    private static final int[] TITLES = {
        R.string.menu_calc, R.string.menu_equations, R.string.menu_examples, R.string.menu_history, R.string.menu_help,
    };
    private static final int[] ICONS = {
        R.drawable.ic_calc, R.drawable.ic_equation, R.drawable.ic_examples, R.drawable.ic_history, R.drawable.ic_help,
    };

    private SideDrawer drawer;
    private HamburgerView hamburger;
    private TextView title;
    private FrameLayout screenHost;
    private final View[] screens = new View[TITLES.length];
    private final View[] navRows = new View[TITLES.length];
    private final ImageView[] navIcons = new ImageView[TITLES.length];
    private final TextView[] navLabels = new TextView[TITLES.length];
    private int current = -1;

    private EditText input;
    private LinearLayout results;
    private ScrollView resultScroll;
    private LinearLayout historyList;
    private TextView historyEmpty;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.color(this, R.color.surface));
        root.addView(buildAppBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60)));

        screenHost = new FrameLayout(this);
        root.addView(screenHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        screens[SCREEN_CALC] = buildCalcScreen();
        screens[SCREEN_EQUATIONS] = buildCatalogScreen(Catalog.EQUATIONS);
        screens[SCREEN_EXAMPLES] = buildCatalogScreen(Catalog.EXAMPLES);
        screens[SCREEN_HISTORY] = buildHistoryScreen();
        screens[SCREEN_HELP] = buildHelpScreen();
        for (View s : screens) {
            s.setVisibility(View.GONE);
            screenHost.addView(s, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        drawer = new SideDrawer(this, root, buildNavigation());
        drawer.setProgressListener(new SideDrawer.ProgressListener() {
            @Override public void onProgress(float progress) {
                hamburger.setProgress(progress);
            }
        });
        setContentView(drawer);

        showScreen(SCREEN_CALC);
        if (results.getChildCount() == 0) addWelcome();
    }

    @Override
    public void onBackPressed() {
        if (drawer.isOpen()) {
            drawer.close();
        } else if (current != SCREEN_CALC) {
            showScreen(SCREEN_CALC);
        } else {
            super.onBackPressed();
        }
    }

    // ---------- оболочка: верхняя панель и левое меню ----------

    private View buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 16), 0);

        FrameLayout menuButton = new FrameLayout(this);
        menuButton.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 24), 0x22000000));
        menuButton.setClickable(true);
        menuButton.setContentDescription(getString(R.string.menu_calc));
        hamburger = new HamburgerView(this);
        hamburger.setColor(Ui.color(this, R.color.on_surface));
        menuButton.addView(hamburger, new FrameLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24), Gravity.CENTER));
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (drawer.isOpen()) drawer.close();
                else drawer.open();
            }
        });
        bar.addView(menuButton, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        title = Ui.text(this, getString(R.string.menu_calc), 22, R.color.on_surface, true);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private View buildNavigation() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 36), Ui.dp(this, 16), Ui.dp(this, 20));

        TextView appName = Ui.text(this, getString(R.string.app_name), 22, R.color.on_surface, true);
        col.addView(appName);
        TextView sub = Ui.text(this, getString(R.string.drawer_subtitle), 13, R.color.on_surface_variant, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 24));
        col.addView(sub);

        for (int i = 0; i < TITLES.length; i++) {
            final int id = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
            row.setClickable(true);
            row.setMinimumHeight(Ui.dp(this, 56));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showScreen(id); }
            });
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            navIcons[i] = icon;
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));

            TextView label = Ui.text(this, getString(TITLES[i]), 16, R.color.on_surface, false);
            label.setPadding(Ui.dp(this, 18), 0, 0, 0);
            navLabels[i] = label;
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            navRows[i] = row;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            col.addView(row, lp);
            Ui.press(row);
        }
        updateNav();
        return col;
    }

    private void updateNav() {
        for (int i = 0; i < navRows.length; i++) {
            boolean selected = i == current;
            navRows[i].setBackground(selected
                    ? Ui.rounded(Ui.color(this, R.color.accent_container), Ui.dp(this, 28))
                    : Ui.ripple(0x00000000, Ui.dp(this, 28), 0x22000000));
            int tint = Ui.color(this, selected ? R.color.on_accent_container : R.color.on_surface_variant);
            navIcons[i].setColorFilter(tint);
            navLabels[i].setTextColor(Ui.color(this, selected ? R.color.on_accent_container : R.color.on_surface));
            navLabels[i].setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        }
    }

    /** Переключение экрана: старый уезжает и гаснет, новый выезжает. */
    private void showScreen(final int id) {
        if (id == current) {
            drawer.close();
            return;
        }
        final int prev = current;
        current = id;

        if (prev >= 0) {
            final View old = screens[prev];
            old.animate().alpha(0f).translationX(-Ui.dp(this, 24)).setDuration(160)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            if (current != prev) {
                                old.setVisibility(View.GONE);
                            }
                            old.setTranslationX(0f);
                        }
                    }).start();
        }
        View nw = screens[id];
        nw.animate().cancel();
        nw.setVisibility(View.VISIBLE);
        nw.setAlpha(0f);
        nw.setTranslationX(Ui.dp(this, 24));
        nw.animate().alpha(1f).translationX(0f).setStartDelay(60).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();

        if (prev < 0) {
            title.setText(TITLES[id]);
        } else {
            title.animate().alpha(0f).setDuration(110).withEndAction(new Runnable() {
                @Override public void run() {
                    title.setText(TITLES[id]);
                    title.animate().alpha(1f).setDuration(180).start();
                }
            }).start();
        }

        if (id == SCREEN_HISTORY) refreshHistory();
        updateNav();
        drawer.close();
    }

    // ---------- экран «Калькулятор» ----------

    private View buildCalcScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        inputCard.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 10));
        inputCard.setBackground(Ui.rounded(Ui.color(this, R.color.surface_high), Ui.dp(this, 24)));

        input = new EditText(this);
        input.setHint(R.string.input_hint);
        input.setHintTextColor(Ui.color(this, R.color.on_surface_variant));
        input.setTextColor(Ui.color(this, R.color.on_surface));
        input.setTextSize(22);
        input.setBackground(null);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Ввод — через встроенную математическую клавиатуру; системная не выезжает.
        input.setShowSoftInputOnFocus(false);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView tv, int actionId, KeyEvent ev) {
                solve();
                return true;
            }
        });
        inputCard.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView solveButton = Ui.text(this, getString(R.string.solve), 15, R.color.on_accent, true);
        solveButton.setGravity(Gravity.CENTER);
        solveButton.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        solveButton.setBackground(Ui.ripple(Ui.color(this, R.color.accent), Ui.dp(this, 20), 0x33FFFFFF));
        solveButton.setClickable(true);
        Ui.press(solveButton);
        solveButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { solve(); }
        });
        actions.addView(solveButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        inputCard.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4));
        col.addView(inputCard, cardLp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView resultsTitle = Ui.text(this, getString(R.string.results), 13, R.color.on_surface_variant, true);
        resultsTitle.setLetterSpacing(0.06f);
        head.addView(resultsTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = Ui.text(this, getString(R.string.clear), 14, R.color.accent, true);
        clear.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        clear.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 16), 0x22000000));
        clear.setClickable(true);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearResults(); }
        });
        head.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.setMargins(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 12), 0);
        col.addView(head, headLp);

        resultScroll = new ScrollView(this);
        resultScroll.setClipToPadding(false);
        resultScroll.setVerticalScrollBarEnabled(false);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        int rp = Ui.dp(this, 12);
        results.setPadding(rp, Ui.dp(this, 8), rp, Ui.dp(this, 12));
        resultScroll.addView(results, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        KeypadView keypad = new KeypadView(this, new KeypadView.Listener() {
            @Override public void onInsert(String text, int cursorBack) { insert(text, cursorBack); }
            @Override public void onDelete() { deleteBack(); }
            @Override public void onClear() { input.getText().clear(); }
            @Override public void onSolve() { solve(); }
        });
        keypad.setBackground(Ui.roundedTop(Ui.color(this, R.color.surface_variant), Ui.dp(this, 28)));
        col.addView(keypad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private void insert(String text, int cursorBack) {
        Editable t = input.getText();
        int s = input.getSelectionStart(), e = input.getSelectionEnd();
        if (s < 0) s = t.length();
        if (e < 0) e = s;
        int start = Math.min(s, e), end = Math.max(s, e);
        t.replace(start, end, text);
        input.setSelection(Math.max(0, start + text.length() - cursorBack));
    }

    private void deleteBack() {
        Editable t = input.getText();
        int s = input.getSelectionStart(), e = input.getSelectionEnd();
        if (s < 0) s = t.length();
        if (e < 0) e = s;
        if (s != e) {
            t.delete(Math.min(s, e), Math.max(s, e));
        } else if (s > 0) {
            t.delete(s - 1, s);
        }
    }

    private void clearResults() {
        results.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            @Override public void run() {
                results.removeAllViews();
                addWelcome();
                results.setAlpha(1f);
            }
        }).start();
    }

    private void addWelcome() {
        Cards.addWelcome(this, results, new Cards.Picker() {
            @Override public void pick(String query) { openQuery(query, true); }
        }, new Runnable() {
            @Override public void run() { showScreen(SCREEN_EQUATIONS); }
        });
    }

    /** Открывает калькулятор с готовым запросом (из меню, истории или примеров). */
    private void openQuery(String query, boolean solveNow) {
        showScreen(SCREEN_CALC);
        input.setText(query);
        input.setSelection(query.length());
        if (solveNow) solve();
    }

    private void solve() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) {
            Ui.shake(input);
            Toast.makeText(this, R.string.empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        HistoryStore.add(this, q);
        try {
            Cards.addResult(this, results, Solver.solve(q), q, 0);
        } catch (MathError e) {
            Cards.addError(this, results, q, e.getMessage(), 0);
        } catch (RuntimeException e) {
            Cards.addError(this, results, q, "Не удалось решить: проверьте запись", 0);
        }
        resultScroll.post(new Runnable() {
            @Override public void run() { resultScroll.smoothScrollTo(0, 0); }
        });
    }

    // ---------- экраны «Уравнения» и «Примеры» ----------

    private View buildCatalogScreen(Catalog.Section[] sections) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        col.setPadding(p, 0, p, Ui.dp(this, 28));
        for (Catalog.Section section : sections) {
            col.addView(Cards.sectionTitle(this, section.title));
            for (final Catalog.Item item : section.items) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
                col.addView(Cards.row(this, item.expr, item.note, new View.OnClickListener() {
                    @Override public void onClick(View v) { openQuery(item.expr, true); }
                }), lp);
            }
        }
        sv.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    // ---------- экран «История» ----------

    private View buildHistoryScreen() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        historyList.setPadding(p, Ui.dp(this, 4), p, Ui.dp(this, 16));
        historyEmpty = Ui.text(this, getString(R.string.history_empty), 15, R.color.on_surface_variant, false);
        historyEmpty.setGravity(Gravity.CENTER);
        historyEmpty.setPadding(0, Ui.dp(this, 64), 0, 0);
        sv.addView(historyList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView clear = Ui.text(this, getString(R.string.clear_history), 15, R.color.error, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 18));
        clear.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 16), 0x22000000));
        clear.setClickable(true);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                HistoryStore.clear(MainActivity.this);
                refreshHistory();
            }
        });
        col.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private void refreshHistory() {
        historyList.removeAllViews();
        List<String> items = HistoryStore.load(this);
        if (items.isEmpty()) {
            historyList.addView(historyEmpty);
            return;
        }
        int index = 0;
        for (final String q : items) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            View row = Cards.row(this, q, null, new View.OnClickListener() {
                @Override public void onClick(View v) { openQuery(q, true); }
            });
            historyList.addView(row, lp);
            if (index < 12) Ui.appear(row, index * 30L);
            index++;
        }
    }

    // ---------- экран «Справка» ----------

    private View buildHelpScreen() {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        TextView t = Ui.text(this, getString(R.string.help_text), 15, R.color.on_surface, false);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        t.setTextIsSelectable(true);
        int p = Ui.dp(this, 20);
        t.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 28));
        sv.addView(t, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }
}
