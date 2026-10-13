package com.example.calcpro.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** Точная рациональная дробь на BigInteger. Всегда сокращена, знаменатель > 0. */
public final class Rational implements Comparable<Rational> {
    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

    private final BigInteger num;
    private final BigInteger den;

    private Rational(BigInteger n, BigInteger d) {
        this.num = n;
        this.den = d;
    }

    public static Rational of(BigInteger n, BigInteger d) {
        if (d.signum() == 0) throw new MathError("Деление на ноль");
        if (d.signum() < 0) {
            n = n.negate();
            d = d.negate();
        }
        BigInteger g = n.gcd(d);
        if (g.signum() != 0 && !g.equals(BigInteger.ONE)) {
            n = n.divide(g);
            d = d.divide(g);
        }
        return new Rational(n, d);
    }

    public static Rational of(BigInteger n) {
        return new Rational(n, BigInteger.ONE);
    }

    public static Rational of(long n) {
        return new Rational(BigInteger.valueOf(n), BigInteger.ONE);
    }

    public static Rational of(long n, long d) {
        return of(BigInteger.valueOf(n), BigInteger.valueOf(d));
    }

    /** Точный разбор десятичной записи: "2.5", "2,5", "-3". */
    public static Rational parseDecimal(String s) {
        BigDecimal bd = new BigDecimal(s.replace(',', '.'));
        if (bd.scale() <= 0) {
            return of(bd.unscaledValue().multiply(BigInteger.TEN.pow(-bd.scale())));
        }
        return of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()));
    }

    public Rational add(Rational o) {
        return of(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den));
    }

    public Rational sub(Rational o) {
        return add(o.neg());
    }

    public Rational mul(Rational o) {
        return of(num.multiply(o.num), den.multiply(o.den));
    }

    public Rational div(Rational o) {
        if (o.isZero()) throw new MathError("Деление на ноль");
        return of(num.multiply(o.den), den.multiply(o.num));
    }

    public Rational neg() {
        return new Rational(num.negate(), den);
    }

    public Rational abs() {
        return signum() < 0 ? neg() : this;
    }

    public Rational pow(int e) {
        if (e >= 0) return of(num.pow(e), den.pow(e));
        if (isZero()) throw new MathError("Деление на ноль");
        return of(den.pow(-e), num.pow(-e));
    }

    public boolean isZero() {
        return num.signum() == 0;
    }

    public boolean isInteger() {
        return den.equals(BigInteger.ONE);
    }

    public int signum() {
        return num.signum();
    }

    public BigInteger numerator() {
        return num;
    }

    public BigInteger denominator() {
        return den;
    }

    public double toDouble() {
        // быстрый путь: оба числа точно представимы в double, их деление даёт правильно округлённый результат
        if (num.bitLength() <= 53 && den.bitLength() <= 53) {
            return num.doubleValue() / den.doubleValue();
        }
        return new BigDecimal(num).divide(new BigDecimal(den), MathContext.DECIMAL64).doubleValue();
    }

    @Override
    public int compareTo(Rational o) {
        return num.multiply(o.den).compareTo(o.num.multiply(den));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Rational)) return false;
        Rational r = (Rational) o;
        return num.equals(r.num) && den.equals(r.den);
    }

    @Override
    public int hashCode() {
        return 31 * num.hashCode() + den.hashCode();
    }

    @Override
    public String toString() {
        return isInteger() ? num.toString() : num + "/" + den;
    }
}
