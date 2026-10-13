package com.example.calcpro;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Solver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Логика калькулятора (обычного и научного) без Android: ввод с кнопок, память, ±, ⌫, CE,
 * автозакрытие скобок, отбрасывание хвостовых операторов и вычисление через {@link Solver}.
 * Тригонометрия работает в выбранных единицах: градусы (по умолчанию) или радианы.
 */
final class CalcEngine {

    /** Результат нажатия «=». */
    static final class Outcome {
        final String expression;   // что посчитано, с автозакрытыми скобками
        final String exact;        // точный вид, если есть («4/3», «5√2»), иначе null
        final String decimal;      // десятичный вид для дисплея
        final String error;        // текст ошибки, если не посчитано
        final boolean needsEquations; // ввод похож на уравнение/график — решать в разделе «Уравнения»

        Outcome(String expression, String exact, String decimal, String error, boolean needsEquations) {
            this.expression = expression;
            this.exact = exact;
            this.decimal = decimal;
            this.error = error;
            this.needsEquations = needsEquations;
        }

        boolean ok() { return error == null && !needsEquations; }
    }

    static final int MAX_LENGTH = 80;

    /** Последний операнд: скобочная группа (с одним уровнем вложенности) или число. */
    private static final Pattern LAST_OPERAND = Pattern.compile(
            "\\((?:[^()]|\\([^()]*\\))*\\)$|[0-9]+[.,]?[0-9]*$");
    private static final Pattern TRAILING_FUNCTION = Pattern.compile(
            "(asin|acos|atan|sin|cos|tan|ln|log|abs|10\\^|e\\^)\\($|[√∛]\\($");
    private static final Pattern TRIG = Pattern.compile("(asin|acos|atan|sin|cos|tan)\\(");
    private static final Pattern VARIABLE = Pattern.compile("(?<![a-zA-Z])[xy](?![a-zA-Z])");

    private String expr = "";
    private boolean fresh;          // показан результат; следующая цифра начнёт новое выражение
    private String operand;         // результат как операнд: «(4/3)»
    private String resultText;      // результат как показывается на дисплее
    private String memory;          // память как выражение, null — пусто
    private String error;           // последняя ошибка для показа
    private boolean errored;        // после ошибки следующая клавиша начинает новое выражение
    private boolean degrees = true;

    // ---------- состояние для экрана ----------

    String expression() { return expr; }
    boolean isFresh() { return fresh; }
    boolean hasMemory() { return memory != null; }
    String error() { return error; }
    boolean degrees() { return degrees; }
    void setDegrees(boolean value) { degrees = value; }

    String display() {
        if (fresh && resultText != null) return resultText;
        Integer start = lastOperandStart();
        if (start == null) return "0";
        String g = expr.substring(start);
        if (g.startsWith("(") && g.endsWith(")") && isSingleGroup(g)) g = g.substring(1, g.length() - 1);
        return g.isEmpty() ? "0" : g;
    }

    // ---------- ввод ----------

    /** Нажатие клавиши: цифры, операторы, скобки, функции, константы, постфиксы. */
    void press(String key) {
        error = null;
        if (errored) {
            expr = "";
            errored = false;
        }
        if (fresh) {
            // После «=»: оператор продолжает результат, цифра или функция начинает заново.
            expr = isBinary(key) && operand != null ? operand : "";
            fresh = false;
            resultText = null;
        }
        if (expr.length() + key.length() > MAX_LENGTH) return;

        char last = expr.isEmpty() ? 0 : expr.charAt(expr.length() - 1);

        if (key.equals(".") || key.equals(",")) {
            if (last == ')') return;
            Integer start = lastOperandStart();
            if (start != null && expr.substring(start).matches("[0-9]*[.,][0-9]*")) return; // уже есть точка
            if (last == 0 || !isDigit(last)) { expr += "0"; }
            expr += ".";
            return;
        }

        if (isBinary(key)) {
            if (expr.isEmpty()) {
                if (key.equals("−")) expr = "−";     // отрицательное число
                return;
            }
            if (last == '(') {
                if (key.equals("−")) expr += key;
                return;
            }
            if (isBinary(last)) {
                if (key.equals("−") && last != '−') {
                    expr += key;                      // «5×−3»
                } else if (last == '−') {
                    if (key.equals("−")) return;      // «5−−» не нужно
                    // «5×−» → «5+»: убираем минус и предыдущий оператор
                    if (expr.length() >= 2 && isBinary(expr.charAt(expr.length() - 2))) {
                        expr = expr.substring(0, expr.length() - 2) + key;
                    } else {
                        expr = expr.substring(0, expr.length() - 1) + key;
                    }
                } else {
                    expr = expr.substring(0, expr.length() - 1) + key;   // замена оператора
                }
                return;
            }
            expr += key;
            return;
        }

        if (key.equals("%") || key.equals("!")) {
            if (expr.isEmpty() || isBinary(last) || last == '(' || last == '√' || last == '∛') return;
            expr += key;
            return;
        }

        if (key.equals(")")) {
            if (!hasOpenParen() || last == '(' || isBinary(last)) return;
            expr += key;
            return;
        }

        if (key.equals("(")) {
            appendTerm(key);
            return;
        }

        // функции, константы и цифры
        boolean startsWord = Character.isLetter(key.charAt(0)) || key.startsWith("√") || key.startsWith("∛")
                || key.startsWith("10^");
        if (startsWord) {
            appendTerm(key);
        } else {
            expr += key;
        }
    }

    /** Добавляет множитель-скобку, функцию или константу; перед ними ставит «×», если нужно (2π, 3sin(…)). */
    private void appendTerm(String term) {
        char last = expr.isEmpty() ? 0 : expr.charAt(expr.length() - 1);
        if (last != 0 && (isDigit(last) || last == ')' || last == 'π' || last == 'e' || last == '!' || last == '%')) {
            expr += "×";
        }
        expr += term;
    }

    /** ⌫: стирает функцию целиком (sin( → пусто) или один символ. */
    void backspace() {
        error = null;
        if (fresh) {
            clearAll();
            return;
        }
        if (expr.isEmpty()) return;
        Matcher m = TRAILING_FUNCTION.matcher(expr);
        if (m.find()) {
            expr = expr.substring(0, m.start());
        } else {
            expr = expr.substring(0, expr.length() - 1);
        }
    }

    /** CE: стирает последний операнд (число или скобочную группу), а если его нет — последний оператор. */
    void clearEntry() {
        error = null;
        if (fresh) {
            clearAll();
            return;
        }
        Integer start = lastOperandStart();
        if (start != null) {
            expr = expr.substring(0, start);
        } else if (!expr.isEmpty()) {
            expr = expr.substring(0, expr.length() - 1);
        }
    }

    /** C: очищает выражение и результат; память не трогает. */
    void clearAll() {
        expr = "";
        fresh = false;
        operand = null;
        resultText = null;
        error = null;
    }

    /** 1/x: заменяет последний операнд его обратным значением. */
    void reciprocal() {
        error = null;
        if (fresh) {
            expr = operand == null ? "" : operand;
            fresh = false;
            resultText = null;
        }
        Integer start = lastOperandStart();
        if (start == null) return;
        expr = expr.substring(0, start) + "(1÷" + expr.substring(start) + ")";
    }

    /** ±: меняет знак последнего операнда. */
    void negate() {
        error = null;
        if (fresh) {
            expr = operand == null ? "" : operand;
            fresh = false;
            resultText = null;
        }
        if (expr.isEmpty()) {
            expr = "−";
            return;
        }
        Integer start = lastOperandStart();
        if (start == null) return;
        String op = expr.substring(start);
        if (op.startsWith("(−") && op.endsWith(")") && isSingleGroup(op)) {
            op = op.substring(2, op.length() - 1);
        } else {
            op = "(−" + op + ")";
        }
        expr = expr.substring(0, start) + op;
    }

    // ---------- память ----------

    /** MC, MR, M+, M− — память хранит выражение, а не число, поэтому точность сохраняется. */
    void memoryClear() {
        memory = null;
    }

    void memoryRecall() {
        error = null;
        if (memory == null) {
            error = "Память пуста";
            return;
        }
        if (fresh) {
            expr = "";
            fresh = false;
            resultText = null;
        }
        // MR заменяет последнее число (как на обычном калькуляторе), а не дописывается к нему
        Integer start = lastOperandStart();
        if (start != null) expr = expr.substring(0, start);
        appendTerm("(" + memory + ")");
    }

    void memoryAdd(boolean add) {
        error = null;
        String value = "(" + currentValueExpression() + ")";
        if (memory == null) {
            memory = add ? value : "−" + value;
        } else {
            memory = "(" + memory + ")" + (add ? "+" : "−") + value;
        }
    }

    private String currentValueExpression() {
        if (fresh && operand != null) return operand.substring(1, operand.length() - 1);
        String e = closeParens(stripTrailingOperators(expr));
        return e.isEmpty() ? "0" : e;
    }

    // ---------- вычисление ----------

    /** «=»: считает выражение; повторное «=» после результата ничего не меняет. */
    Outcome equals() {
        error = null;
        if (fresh) {
            return new Outcome(expr, operand == null ? null : operand.substring(1, operand.length() - 1),
                    resultText, null, false);
        }
        String e = closeParens(stripTrailingOperators(expr));
        if (e.isEmpty()) return new Outcome("", null, null, null, false);
        if (VARIABLE.matcher(e).find()) {
            return new Outcome(e, null, null, null, true);
        }
        String solverInput = degrees ? rewriteDegrees(e) : e;
        try {
            Solver.Result r = Solver.solve(solverInput);
            String a = r.answers.isEmpty() ? "" : r.answers.get(0);
            String prefix = "Ответ: ";
            if (!a.startsWith(prefix)) {
                return new Outcome(e, null, null, null, true);
            }
            String value = a.substring(prefix.length());
            int approx = value.indexOf(" ≈ ");
            String exact = approx >= 0 ? value.substring(0, approx) : value;
            String decimal = approx >= 0 ? value.substring(approx + 3) : value;
            expr = e;
            fresh = true;
            operand = "(" + exact + ")";
            resultText = decimal;
            return new Outcome(e, exact, decimal, null, false);
        } catch (MathError ex) {
            error = ex.getMessage();
            errored = true;
            return new Outcome(e, null, null, error, false);
        } catch (RuntimeException ex) {
            error = "Не удалось вычислить: проверьте запись";
            errored = true;
            return new Outcome(e, null, null, error, false);
        }
    }

    // ---------- вспомогательные функции ----------

    private Integer lastOperandStart() {
        Matcher m = LAST_OPERAND.matcher(expr);
        return m.find() ? m.start() : null;
    }

    private boolean hasOpenParen() {
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        return depth > 0;
    }

    static String closeParens(String s) {
        StringBuilder sb = new StringBuilder(s);
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        while (depth-- > 0) sb.append(')');
        return sb.toString();
    }

    static String stripTrailingOperators(String s) {
        String r = s;
        while (!r.isEmpty()) {
            char c = r.charAt(r.length() - 1);
            if (isBinary(c) || c == '(' || c == '√' || c == '∛') {
                r = r.substring(0, r.length() - 1);
            } else if (TRAILING_FUNCTION.matcher(r).find()) {
                r = TRAILING_FUNCTION.matcher(r).replaceFirst("");
            } else {
                break;
            }
        }
        return r;
    }

    /**
     * Градусы: аргумент тригонометрических функций переводится в радианы (sin(30) = 0,5),
     * результат обратных функций — из радиан в градусы. Работает со вложенными скобками.
     */
    static String rewriteDegrees(String s) {
        String out = s;
        int pos = 0;
        while (true) {
            Matcher m = TRIG.matcher(out);
            if (!m.find(pos)) break;
            String name = m.group(1);
            int open = m.end() - 1;
            int close = matchingClose(out, open);
            if (close < 0) break;
            String inner = rewriteDegrees(out.substring(open + 1, close));
            String replaced = name.startsWith("a")
                    ? "((180÷π)×" + name + "(" + inner + "))"
                    : name + "((" + inner + ")×π÷180)";
            out = out.substring(0, m.start()) + replaced + out.substring(close + 1);
            pos = m.start() + replaced.length();
        }
        return out;
    }

    private static int matchingClose(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isSingleGroup(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0 && i != s.length() - 1) return false;
            }
        }
        return depth == 0;
    }

    private static boolean isBinary(String key) {
        return key.equals("+") || key.equals("−") || key.equals("×") || key.equals("÷") || key.equals("^");
    }

    private static boolean isBinary(char c) {
        return c == '+' || c == '−' || c == '×' || c == '÷' || c == '^';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
