package com.example.spamton;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Точное (в пределах double) вычисление выражений без Android.
 * Поддерживает + − × ÷ ^ %, скобки, унарный минус и √.
 * Это «настоящий» ответ; Спамтон его искажает, пока не побеждён.
 */
public final class Calc {

    private Calc() {}

    /** Ошибка ввода или математики (деление на ноль, незакрытая скобка и т.п.). */
    public static final class CalcException extends RuntimeException {
        public CalcException(String message) { super(message); }
    }

    /** Вычисляет выражение. Незакрытые скобки закрываются автоматически. */
    public static double eval(String expression) {
        String src = expression.replace('×', '*').replace('÷', '/').replace('−', '-');
        src = closeParens(src);
        if (src.trim().isEmpty()) throw new CalcException("empty");
        Parser p = new Parser(src);
        double v = p.expr();
        p.skipSpaces();
        if (!p.atEnd()) throw new CalcException("syntax");
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new CalcException("range");
        return v == 0 ? 0 : v; // убираем -0
    }

    /** Форматирует число для дисплея: до 10 значащих цифр, без хвостовых нулей. */
    public static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "ОШИБКА";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        BigDecimal bd = new BigDecimal(v).round(new MathContext(10));
        String s = bd.stripTrailingZeros().toPlainString();
        return s.equals("-0") ? "0" : s;
    }

    static String closeParens(String s) {
        int open = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') open++;
            else if (c == ')' && open > 0) open--;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < open; i++) sb.append(')');
        return sb.toString();
    }

    /** Рекурсивный спуск: expr → term → power → unary → primary. */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipSpaces() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        private char peek() {
            skipSpaces();
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        double expr() {
            double v = term();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; v = v + term(); }
                else if (c == '-') { pos++; v = v - term(); }
                else return v;
            }
        }

        private double term() {
            double v = power();
            while (true) {
                char c = peek();
                if (c == '*') { pos++; v = v * power(); }
                else if (c == '/') {
                    pos++;
                    double d = power();
                    if (d == 0) throw new CalcException("div0");
                    v = v / d;
                } else return v;
            }
        }

        private double power() {
            double base = unary();
            if (peek() == '^') {
                pos++;
                double e = power(); // правоассоциативно
                return Math.pow(base, e);
            }
            return base;
        }

        private double unary() {
            char c = peek();
            if (c == '-') { pos++; return -unary(); }
            if (c == '+') { pos++; return unary(); }
            return postfix();
        }

        private double postfix() {
            double v = primary();
            while (peek() == '%') {
                pos++;
                v = v / 100.0;
            }
            return v;
        }

        private double primary() {
            char c = peek();
            if (c == '(') {
                pos++;
                double v = expr();
                if (peek() != ')') throw new CalcException("paren");
                pos++;
                return v;
            }
            if (c == '√') {
                pos++;
                double v = primary();
                if (v < 0) throw new CalcException("sqrt");
                return Math.sqrt(v);
            }
            return number();
        }

        private double number() {
            skipSpaces();
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
            if (start == pos) throw new CalcException("syntax");
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new CalcException("syntax");
            }
        }
    }
}
