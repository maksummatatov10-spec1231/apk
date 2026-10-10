package com.example.calcpro.math;

import java.util.Arrays;

/** Многочлен от одной переменной x с точными рациональными коэффициентами. */
public final class Poly {
    private final Rational[] c; // c[i] — коэффициент при x^i, старший ненулевой (кроме нуля)

    private Poly(Rational[] arr) {
        int n = arr.length == 0 ? 1 : arr.length;
        Rational[] src = arr.length == 0 ? new Rational[]{Rational.ZERO} : arr;
        while (n > 1 && src[n - 1].isZero()) n--;
        this.c = Arrays.copyOf(src, n);
    }

    public static Poly constant(Rational r) {
        return new Poly(new Rational[]{r});
    }

    public static Poly x() {
        return new Poly(new Rational[]{Rational.ZERO, Rational.ONE});
    }

    public boolean isZero() {
        return c.length == 1 && c[0].isZero();
    }

    /** Степень; для ненулевого многочлена — индекс старшего коэффициента. */
    public int degree() {
        return isZero() ? -1 : c.length - 1;
    }

    public int size() {
        return c.length;
    }

    public Rational coef(int i) {
        return i < c.length ? c[i] : Rational.ZERO;
    }

    public Poly add(Poly o) {
        int n = Math.max(c.length, o.c.length);
        Rational[] r = new Rational[n];
        for (int i = 0; i < n; i++) r[i] = coef(i).add(o.coef(i));
        return new Poly(r);
    }

    public Poly neg() {
        Rational[] r = new Rational[c.length];
        for (int i = 0; i < c.length; i++) r[i] = c[i].neg();
        return new Poly(r);
    }

    public Poly sub(Poly o) {
        return add(o.neg());
    }

    public Poly mul(Poly o) {
        Rational[] r = new Rational[c.length + o.c.length - 1];
        Arrays.fill(r, Rational.ZERO);
        for (int i = 0; i < c.length; i++)
            for (int j = 0; j < o.c.length; j++)
                r[i + j] = r[i + j].add(c[i].mul(o.c[j]));
        return new Poly(r);
    }

    public Poly scale(Rational k) {
        Rational[] r = new Rational[c.length];
        for (int i = 0; i < c.length; i++) r[i] = c[i].mul(k);
        return new Poly(r);
    }

    public Poly pow(int e) {
        Poly res = constant(Rational.ONE);
        for (int i = 0; i < e; i++) res = res.mul(this);
        return res;
    }

    public Rational eval(Rational x) {
        Rational acc = Rational.ZERO;
        for (int i = c.length - 1; i >= 0; i--) acc = acc.mul(x).add(c[i]);
        return acc;
    }

    /** Деление на (x − r) по схеме Горнера; остаток должен быть нулём. */
    public Poly divideByLinear(Rational r) {
        int n = c.length - 1;
        if (n < 1) throw new MathError("Нельзя делить константу");
        Rational[] q = new Rational[n];
        Rational carry = Rational.ZERO;
        for (int i = n; i >= 1; i--) {
            carry = c[i].add(carry.mul(r));
            q[i - 1] = carry;
        }
        return new Poly(q);
    }

    /** Запись в виде «2x² − 3x + 1». */
    public String format() {
        if (isZero()) return "0";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int i = c.length - 1; i >= 0; i--) {
            Rational k = c[i];
            if (k.isZero()) continue;
            boolean negative = k.signum() < 0;
            Rational a = k.abs();
            if (first) {
                if (negative) sb.append('−');
            } else {
                sb.append(negative ? " − " : " + ");
            }
            if (i == 0) {
                sb.append(Fmt.rat(a));
            } else {
                if (!a.equals(Rational.ONE)) sb.append(Fmt.par(a).equals(Fmt.rat(a)) ? Fmt.rat(a) : Fmt.par(a));
                sb.append('x');
                if (i > 1) sb.append(Fmt.sup(i));
            }
            first = false;
        }
        return sb.toString();
    }
}
