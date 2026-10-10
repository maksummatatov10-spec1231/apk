package com.example.calcpro.math;

import java.math.BigDecimal;
import java.math.MathContext;

/** Форматирование чисел и дробей для русскоязычного вывода. */
public final class Fmt {
    private static final String[] SUP = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"};

    private Fmt() {}

    private static final String[] SUB = {"₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉"};

    /** Подстрочный индекс: 2 -> "₂". */
    public static String sub(int n) {
        StringBuilder sb = new StringBuilder();
        for (char ch : Integer.toString(n).toCharArray()) sb.append(SUB[ch - '0']);
        return sb.toString();
    }

    /** Надстрочный индекс: 2 -> "²", 10 -> "¹⁰". */
    public static String sup(int n) {
        StringBuilder sb = new StringBuilder();
        for (char ch : Integer.toString(n).toCharArray()) sb.append(SUP[ch - '0']);
        return sb.toString();
    }

    /** Дробь или целое; минус — typographic. */
    public static String rat(Rational r) {
        String s = r.isInteger() ? r.numerator().toString() : r.numerator() + "/" + r.denominator();
        return s.replace('-', '−');
    }

    /** Число в скобках, если отрицательное или дробное: (−3), (3/4). */
    public static String par(Rational r) {
        String s = rat(r);
        return (s.startsWith("−") || !r.isInteger()) ? "(" + s + ")" : s;
    }

    /** Точная дробь с приближением: "1/3 ≈ 0,333333". */
    public static String ratApprox(Rational r) {
        if (r.isInteger()) return rat(r);
        return rat(r) + " ≈ " + num(r.toDouble());
    }

    /** Вещественное число с 6 значащими цифрами, запятая как десятичный разделитель. */
    public static String num(double x) {
        if (Double.isNaN(x)) return "не определено";
        if (Double.isInfinite(x)) return x > 0 ? "∞" : "−∞";
        if (Math.abs(x) < 1e-12) return "0";
        BigDecimal bd = new BigDecimal(x).round(new MathContext(6)).stripTrailingZeros();
        return bd.toPlainString().replace('.', ',').replace('-', '−');
    }

    /** Комплексное число re ± im·i. */
    public static String complex(double re, double im) {
        String imPart = num(Math.abs(im)) + "i";
        if (Math.abs(re) < 1e-12) return "±" + imPart;
        return num(re) + " ± " + imPart;
    }
}
