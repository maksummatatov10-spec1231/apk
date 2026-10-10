package com.example.calcpro.math;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Узел дерева выражения. Неизменяемый. */
public final class Node {
    public enum Kind { NUM, REAL, VAR, CONST, NEG, BIN, FUN }

    public final Kind kind;
    public final Rational num;  // NUM
    public final double real;   // REAL
    public final String name;   // VAR, CONST, FUN, операция BIN
    public final Node a;
    public final Node b;

    private Node(Kind kind, Rational num, double real, String name, Node a, Node b) {
        this.kind = kind;
        this.num = num;
        this.real = real;
        this.name = name;
        this.a = a;
        this.b = b;
    }

    public static Node num(Rational r) { return new Node(Kind.NUM, r, 0, null, null, null); }
    public static Node real(double d) { return new Node(Kind.REAL, null, d, null, null, null); }
    public static Node var(String n) { return new Node(Kind.VAR, null, 0, n, null, null); }
    public static Node constant(String n) { return new Node(Kind.CONST, null, 0, n, null, null); }
    public static Node neg(Node a) { return new Node(Kind.NEG, null, 0, null, a, null); }
    public static Node bin(String op, Node a, Node b) { return new Node(Kind.BIN, null, 0, op, a, b); }
    public static Node fun(String name, Node a) { return new Node(Kind.FUN, null, 0, name, a, null); }

    public boolean isNumber() {
        return kind == Kind.NUM || kind == Kind.REAL;
    }

    public boolean isZeroLiteral() {
        return kind == Kind.NUM && num.isZero();
    }

    public void collectVars(Set<String> out) {
        if (kind == Kind.VAR) out.add(name);
        if (a != null) a.collectVars(out);
        if (b != null) b.collectVars(out);
    }

    public boolean hasVar(String v) {
        if (kind == Kind.VAR) return v.equals(name);
        return (a != null && a.hasVar(v)) || (b != null && b.hasVar(v));
    }

    /** Вещественное значение. Бросает MathError при ошибке (деление на ноль, неизвестная переменная). */
    public double eval(Map<String, Double> env) {
        switch (kind) {
            case NUM: return num.toDouble();
            case REAL: return real;
            case VAR: {
                Double v = env.get(name);
                if (v == null) throw new MathError("Неизвестная переменная «" + name + "»");
                return v;
            }
            case CONST: return "π".equals(name) || "pi".equals(name) ? Math.PI : Math.E;
            case NEG: return -a.eval(env);
            case FUN: return Functions.apply(name, a.eval(env));
            case BIN: return applyBin(name, a.eval(env), b.eval(env));
            default: throw new MathError("Неизвестный узел");
        }
    }

    static double applyBin(String op, double x, double y) {
        switch (op) {
            case "+": return x + y;
            case "-": return x - y;
            case "*": return x * y;
            case "/":
                if (y == 0) throw new MathError("Деление на ноль");
                return x / y;
            case "^": return Math.pow(x, y);
            default: throw new MathError("Неизвестная операция " + op);
        }
    }

    /** Точное рациональное значение, либо null если выражение не рационально (π, корни, функции). */
    public Rational exact(Map<String, Rational> env) {
        switch (kind) {
            case NUM: return num;
            case REAL:
            case CONST: return null;
            case VAR: return env == null ? null : env.get(name);
            case NEG: {
                Rational x = a.exact(env);
                return x == null ? null : x.neg();
            }
            case BIN: {
                Rational x = a.exact(env);
                Rational y = b.exact(env);
                if (x == null || y == null) return null;
                switch (name) {
                    case "+": return x.add(y);
                    case "-": return x.sub(y);
                    case "*": return x.mul(y);
                    case "/": return x.div(y);
                    case "^":
                        if (!y.isInteger() || y.numerator().bitLength() > 16) return null;
                        return x.pow(y.numerator().intValue());
                    default: return null;
                }
            }
            case FUN: {
                if (!"!".equals(name)) return null;
                Rational x = a.exact(env);
                if (x == null || !x.isInteger() || x.signum() < 0 || x.numerator().bitLength() > 9) return null;
                java.math.BigInteger f = java.math.BigInteger.ONE;
                for (int i = 2; i <= x.numerator().intValue(); i++) f = f.multiply(java.math.BigInteger.valueOf(i));
                return Rational.of(f);
            }
            default: return null;
        }
    }

    /** Многочлен от x, если выражение таково; иначе null. */
    public Poly toPoly() {
        switch (kind) {
            case NUM: return Poly.constant(num);
            case VAR: return "x".equals(name) ? Poly.x() : null;
            case NEG: {
                Poly p = a.toPoly();
                return p == null ? null : p.neg();
            }
            case BIN: {
                Poly p = a.toPoly();
                if (p == null) return null;
                Poly q = b.toPoly();
                switch (name) {
                    case "+": return q == null ? null : p.add(q);
                    case "-": return q == null ? null : p.sub(q);
                    case "*": return q == null ? null : p.mul(q);
                    case "/":
                        if (q == null || q.degree() != 0 || q.isZero()) return null;
                        return p.scale(Rational.ONE.div(q.coef(0)));
                    case "^":
                        if (q == null || q.degree() != 0) return null;
                        Rational e = q.coef(0);
                        if (!e.isInteger() || e.signum() < 0 || e.numerator().intValue() > 12) return null;
                        return p.pow(e.numerator().intValue());
                    default: return null;
                }
            }
            default: return null;
        }
    }

    // ---------- пошаговое упрощение ----------

    /** Один шаг вычисления: сворачивает первую операцию, у которой все операнды — числа. null, если шагов нет. */
    public Node stepOnce() {
        switch (kind) {
            case NEG:
            case FUN:
                if (!a.isNumber()) {
                    Node r = a.stepOnce();
                    if (r == null) return null;
                    return kind == Kind.NEG ? neg(r) : fun(name, r);
                }
                return evaluateConstant();
            case BIN:
                if (!a.isNumber()) {
                    Node r = a.stepOnce();
                    return r == null ? null : bin(name, r, b);
                }
                if (!b.isNumber()) {
                    Node r = b.stepOnce();
                    return r == null ? null : bin(name, a, r);
                }
                return evaluateConstant();
            default:
                return null;
        }
    }

    private Node evaluateConstant() {
        Rational ex = exact(null);
        if (ex != null) return num(ex);
        return real(eval(Collections.<String, Double>emptyMap()));
    }

    // ---------- вывод ----------

    private int precedence() {
        switch (kind) {
            case BIN:
                switch (name) {
                    case "+": case "-": return 1;
                    case "*": case "/": return 2;
                    case "^": return 4;
                    default: return 5;
                }
            case NEG: return 3;
            case NUM: return num.isInteger() ? 5 : 2;
            default: return 5;
        }
    }

    public String show() {
        return show(0);
    }

    /** Начинается ли запись узла с буквы (переменная, константа, функция, степень от них). */
    boolean startsWithLetter() {
        switch (kind) {
            case VAR: case CONST: case FUN: return true;
            case BIN: return "^".equals(name) && a.startsWithLetter();
            default: return false;
        }
    }

    private String show(int parentPrec) {
        String s = raw();
        return precedence() < parentPrec ? "(" + s + ")" : s;
    }

    private String raw() {
        switch (kind) {
            case NUM: return Fmt.rat(num);
            case REAL: return Fmt.num(real);
            case VAR: return name;
            case CONST: return "pi".equals(name) ? "π" : name;
            case NEG: return "−" + a.show(3);
            case FUN:
                if ("!".equals(name)) return a.show(5) + "!";
                if ("sqrt".equals(name)) return "√(" + a.show(0) + ")";
                if ("cbrt".equals(name)) return "∛(" + a.show(0) + ")";
                if ("abs".equals(name)) return "|" + a.show(0) + "|";
                return name + "(" + a.show(0) + ")";
            case BIN: {
                String op = name;
                if ("^".equals(op)) {
                    if (b.kind == Kind.NUM && b.num.isInteger() && b.num.signum() >= 0 && b.num.numerator().bitLength() < 5)
                        return a.show(5) + Fmt.sup(b.num.numerator().intValue());
                    return a.show(5) + "^(" + b.show(0) + ")";
                }
                int p = precedence();
                if ("*".equals(op) && a.isNumber() && b.startsWithLetter()) {
                    return a.show(p) + b.show(p); // 2x, 3π, 2x²
                }
                String sym;
                switch (op) {
                    case "+": sym = " + "; break;
                    case "-": sym = " − "; break;
                    case "*": sym = " · "; break;
                    default: sym = " ÷ "; break;
                }
                return a.show(p) + sym + b.show(p + 1);
            }
            default: return "?";
        }
    }
}
