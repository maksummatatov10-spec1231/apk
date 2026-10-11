package com.example.calcpro;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
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

/**
 * Оболочка приложения: верхняя панель и выезжающее слева меню.
 * Экраны: обычный и научный калькулятор (как Calculator²), решатель уравнений,
 * история и справка «О приложении».
 */
public class MainActivity extends Activity {

    static final int SCREEN_BASIC = 0;
    static final int SCREEN_SCIENTIFIC = 1;
    static final int SCREEN_EQUATIONS = 2;
    static final int SCREEN_HISTORY = 3;
    static final int SCREEN_ABOUT = 4;
    static final int SCREEN_COUNT = 5;

    private static final int PANEL_HEIGHT_DP = 420;

    private SideDrawer drawer;
    private HamburgerView hamburger;
    private TextView title;
    private FrameLayout screenHost;
    private final View[] screens = new View[SCREEN_COUNT];
    private final View[] navRows = new View[SCREEN_COUNT];
    private final ImageView[] navIcons = new ImageView[SCREEN_COUNT];
    private final TextView[] navLabels = new TextView[SCREEN_COUNT];
    private int current = -1;

    // экран «Уравнения»
    private EditText input;
    private LinearLayout results;
    private TextView resultsHint;
    private ScrollView resultScroll;
    private LinearLayout keypadBox;
    private ImageView keypadToggle;
    private boolean keypadOpen;
    private String lastAnswer;   // последний числовой ответ для кнопки «Ans»

    // экран «История»
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

        screens[SCREEN_BASIC] = new CalcPanel(this, false, panelListener());
        screens[SCREEN_SCIENTIFIC] = new CalcPanel(this, true, panelListener());
        screens[SCREEN_EQUATIONS] = buildEquationsScreen();
        screens[SCREEN_HISTORY] = buildHistoryScreen();
        screens[SCREEN_ABOUT] = buildAboutScreen();
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

        showScreen(SCREEN_BASIC);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isOpen()) {
            drawer.close();
        } else if (keypadOpen) {
            setKeypadOpen(false);
        } else if (current != SCREEN_BASIC) {
            showScreen(SCREEN_BASIC);
        } else {
            super.onBackPressed();
        }
    }

    /** Калькулятор отдаёт уравнения и графики в раздел «Уравнения» и решает их там. */
    private CalcPanel.Listener panelListener() {
        return new CalcPanel.Listener() {
            @Override public void onEquation(String text) {
                openQuery(text, true);
            }
        };
    }

    // ---------- оболочка: верхняя панель и левое меню ----------

    private String titleOf(int id) {
        if (id == SCREEN_BASIC) return getString(R.string.menu_basic);
        if (id == SCREEN_SCIENTIFIC) return getString(R.string.menu_scientific);
        if (id == SCREEN_EQUATIONS) return getString(R.string.menu_equations);
        if (id == SCREEN_HISTORY) return getString(R.string.menu_history);
        return getString(R.string.menu_about);
    }

    private int iconOf(int id) {
        if (id == SCREEN_BASIC) return R.drawable.ic_calc;
        if (id == SCREEN_SCIENTIFIC) return R.drawable.ic_science;
        if (id == SCREEN_EQUATIONS) return R.drawable.ic_equation;
        if (id == SCREEN_HISTORY) return R.drawable.ic_history;
        return R.drawable.ic_help;
    }

    private View buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 16), 0);

        FrameLayout menuButton = new FrameLayout(this);
        menuButton.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 24), 0x22000000));
        menuButton.setClickable(true);
        menuButton.setContentDescription(getString(R.string.menu_basic));
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

        title = Ui.text(this, getString(R.string.menu_basic), 22, R.color.on_surface, true);
        title.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private View buildNavigation() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 36), Ui.dp(this, 16), Ui.dp(this, 20));

        col.addView(Ui.text(this, getString(R.string.app_name), 22, R.color.on_surface, true));
        TextView sub = Ui.text(this, getString(R.string.drawer_subtitle), 13, R.color.on_surface_variant, false);
        sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        col.addView(sub);

        col.addView(navGroup("Калькулятор"));
        col.addView(navItem(SCREEN_BASIC));
        col.addView(navItem(SCREEN_SCIENTIFIC));

        col.addView(navGroup("Решатель"));
        col.addView(navItem(SCREEN_EQUATIONS));

        col.addView(navGroup("Прочее"));
        col.addView(navItem(SCREEN_HISTORY));
        col.addView(navItem(SCREEN_ABOUT));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateNav();
        return sv;
    }

    private View navGroup(String text) {
        TextView t = Ui.text(this, text.toUpperCase(), 11, R.color.accent, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), 0, Ui.dp(this, 6));
        return t;
    }

    private View navItem(final int id) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        row.setClickable(true);
        row.setMinimumHeight(Ui.dp(this, 52));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showScreen(id); }
        });
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconOf(id));
        navIcons[id] = icon;
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));

        TextView label = Ui.text(this, titleOf(id), 15, R.color.on_surface, false);
        label.setPadding(Ui.dp(this, 16), 0, 0, 0);
        navLabels[id] = label;
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        navRows[id] = row;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        row.setLayoutParams(lp);
        Ui.press(row);
        return row;
    }

    private void updateNav() {
        for (int i = 0; i < navRows.length; i++) {
            if (navRows[i] == null) continue;
            boolean selected = i == current;
            navRows[i].setBackground(selected
                    ? Ui.rounded(Ui.color(this, R.color.accent_container), Ui.dp(this, 26))
                    : Ui.ripple(0x00000000, Ui.dp(this, 26), 0x22000000));
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
            old.animate().cancel();
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
            title.setText(titleOf(id));
        } else {
            title.animate().alpha(0f).setDuration(110).withEndAction(new Runnable() {
                @Override public void run() {
                    title.setText(titleOf(id));
                    title.animate().alpha(1f).setDuration(180).start();
                }
            }).start();
        }

        if (id == SCREEN_HISTORY) refreshHistory();
        if (id != SCREEN_EQUATIONS) setKeypadOpen(false);
        updateNav();
        drawer.close();
    }

    // ---------- экран «Уравнения» ----------

    private View buildEquationsScreen() {
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
        input.setMaxLines(6);
        input.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // Системная клавиатура не открывается: вместо неё выезжает математическая панель.
        input.setShowSoftInputOnFocus(false);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView tv, int actionId, KeyEvent ev) {
                solve();
                return true;
            }
        });
        input.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setKeypadOpen(true); }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) setKeypadOpen(true);
            }
        });
        inputCard.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        keypadToggle = new ImageView(this);
        keypadToggle.setImageResource(R.drawable.ic_keyboard);
        keypadToggle.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        keypadToggle.setBackground(Ui.ripple(0x00000000, Ui.dp(this, 22), 0x22000000));
        keypadToggle.setClickable(true);
        keypadToggle.setContentDescription("Клавиатура");
        keypadToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setKeypadOpen(!keypadOpen); }
        });
        actions.addView(keypadToggle, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

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
        showHint();

        // Панель: ручка для сворачивания + клавиатура. По умолчанию скрыта.
        keypadBox = new LinearLayout(this);
        keypadBox.setOrientation(LinearLayout.VERTICAL);
        keypadBox.setBackground(Ui.roundedTop(Ui.color(this, R.color.surface_variant), Ui.dp(this, 28)));
        keypadBox.addView(buildHandle(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28)));
        KeypadView keypad = new KeypadView(this, new KeypadView.Listener() {
            @Override public void onInsert(String text, int cursorBack) { insert(text, cursorBack); }
            @Override public void onDelete() { deleteBack(); }
            @Override public void onClear() { input.getText().clear(); }
            @Override public void onSolve() { solve(); }
        });
        keypadBox.addView(keypad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        keypadBox.addView(buildUtilityRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        keypadBox.setVisibility(View.GONE);
        col.addView(keypadBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    /** Служебная строка под клавиатурой: курсор влево/вправо (с повтором) и «Ans». */
    private View buildUtilityRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int m = Ui.dp(this, 3);
        row.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 8));

        TextView left = utilityKey("←");
        Ui.hold(left, new Runnable() {
            @Override public void run() { moveCursor(-1); }
        });
        TextView right = utilityKey("→");
        Ui.hold(right, new Runnable() {
            @Override public void run() { moveCursor(1); }
        });
        TextView ans = utilityKey("Ans");
        ans.setContentDescription("Подставить предыдущий ответ");
        ans.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { insertAns(); }
        });
        Ui.press(ans);

        row.addView(left, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        row.addView(right, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        LinearLayout.LayoutParams ansLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 2f);
        row.addView(ans, ansLp);
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams();
            lp.setMargins(m, 0, m, 0);
        }
        return row;
    }

    private TextView utilityKey(String label) {
        TextView t = Ui.text(this, label, 16, R.color.on_surface, true);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setBackground(Ui.ripple(Ui.color(this, R.color.key_func), Ui.dp(this, 14), 0x33000000));
        return t;
    }

    private void moveCursor(int delta) {
        int pos = input.getSelectionStart();
        if (pos < 0) pos = input.getText().length();
        int next = Math.max(0, Math.min(input.getText().length(), pos + delta));
        input.setSelection(next);
    }

    private void insertAns() {
        if (lastAnswer == null) {
            Toast.makeText(this, "Пока нет числового ответа", Toast.LENGTH_SHORT).show();
            return;
        }
        insert(lastAnswer, 0);
    }

    /** Запоминает число из ответа «Ответ: …» для кнопки «Ans». */
    private void rememberAnswer(Solver.Result res) {
        if (res.answers.isEmpty()) return;
        String a = res.answers.get(0);
        String prefix = "Ответ: ";
        if (!a.startsWith(prefix)) return;
        String v = a.substring(prefix.length());
        int approx = v.indexOf(" ≈");
        if (approx >= 0) v = v.substring(0, approx);
        v = v.replace('−', '-').replace(',', '.').trim();
        lastAnswer = "(" + v + ")";
    }

    private View buildHandle() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClickable(true);
        row.setContentDescription("Свернуть клавиатуру");
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setKeypadOpen(false); }
        });
        View pill = new View(this);
        pill.setBackground(Ui.rounded(Ui.color(this, R.color.on_surface_variant), Ui.dp(this, 2)));
        row.addView(pill, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 4)));
        TextView hint = Ui.text(this, "Свернуть", 12, R.color.on_surface_variant, true);
        hint.setPadding(Ui.dp(this, 10), 0, 0, 0);
        row.addView(hint);
        return row;
    }

    /** Показ и сворачивание клавиатуры с анимацией выезда снизу. */
    private void setKeypadOpen(boolean open) {
        if (open == keypadOpen) return;
        keypadOpen = open;
        keypadBox.animate().cancel();
        float h = Ui.dp(this, PANEL_HEIGHT_DP);
        if (open) {
            keypadBox.setVisibility(View.VISIBLE);
            keypadBox.setAlpha(0f);
            keypadBox.setTranslationY(h);
            keypadBox.animate().translationY(0f).alpha(1f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            keypadBox.animate().translationY(h).alpha(0f).setDuration(200)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            if (!keypadOpen) keypadBox.setVisibility(View.GONE);
                            keypadBox.setTranslationY(0f);
                        }
                    }).start();
        }
        keypadToggle.setColorFilter(Ui.color(this, open ? R.color.accent : R.color.on_surface_variant));
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

    private void showHint() {
        results.removeAllViews();
        resultsHint = Ui.text(this, getString(R.string.equations_hint), 15, R.color.on_surface_variant, false);
        resultsHint.setGravity(Gravity.CENTER);
        resultsHint.setPadding(Ui.dp(this, 24), Ui.dp(this, 64), Ui.dp(this, 24), 0);
        results.addView(resultsHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void clearResults() {
        results.animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            @Override public void run() {
                showHint();
                results.setAlpha(1f);
            }
        }).start();
    }

    /** Открывает решатель с готовым запросом (из калькулятора, истории). */
    private void openQuery(String query, boolean solveNow) {
        showScreen(SCREEN_EQUATIONS);
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
        if (resultsHint != null && results.indexOfChild(resultsHint) >= 0) results.removeView(resultsHint);
        try {
            Solver.Result res = Solver.solve(q);
            rememberAnswer(res);
            Cards.addResult(this, results, res, q, 0);
        } catch (MathError e) {
            Cards.addError(this, results, q, e.getMessage(), 0);
        } catch (RuntimeException e) {
            Cards.addError(this, results, q, "Не удалось решить: проверьте запись", 0);
        }
        resultScroll.post(new Runnable() {
            @Override public void run() { resultScroll.smoothScrollTo(0, 0); }
        });
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

    // ---------- экран «О приложении» ----------

    private View buildAboutScreen() {
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
