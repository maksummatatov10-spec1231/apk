package com.example.calcpro.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбор выражений: + − * / ^ ! % скобки, неявное умножение (2x, 3π, (x+1)(x−1)),
 * функции sin cos tan asin acos atan ln log lg exp sqrt abs, константы π и e, степени ² ³.
 */
public final class Parser {
    private enum T { NUM, ID, OP, LP, RP, END }

    private static final class Tok {
        final T type;
        final String text;

        Tok(T type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    private static final String[] WORDS = {
        "asin", "acos", "atan", "sin", "cos", "tan", "ln", "log", "lg", "exp", "sqrt", "abs", "pi"
    };

    private final List<Tok> toks;
    private int pos;

    private Parser(List<Tok> toks) {
        this.toks = toks;
    }

    public static Node parse(String src) {
        Parser p = new Parser(tokenize(src));
        if (p.peek().type == T.END) throw new MathError("Пустое выражение");
        Node n = p.expr();
        if (p.peek().type != T.END) throw new MathError("Лишний фрагмент: «" + p.peek().text + "»");
        return n;
    }

    // ---------- токенизация ----------

    private static List<Tok> tokenize(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            boolean decimalStart = (c == '.' || c == ',') && i + 1 < n && Character.isDigit(s.charAt(i + 1));
            if (Character.isDigit(c) || decimalStart) {
                int st = i;
                while (i < n && Character.isDigit(s.charAt(i))) i++;
                if (i + 1 < n && (s.charAt(i) == '.' || s.charAt(i) == ',') && Character.isDigit(s.charAt(i + 1))) {
                    i++;
                    while (i < n && Character.isDigit(s.charAt(i))) i++;
                }
                out.add(new Tok(T.NUM, s.substring(st, i)));
                continue;
            }
            if (c == '√') {
                out.add(new Tok(T.ID, "sqrt"));
                i++;
                continue;
            }
            if (Character.isLetter(c)) {
                int st = i;
                while (i < n && Character.isLetter(s.charAt(i))) i++;
                String w = s.substring(st, i);
                if (isWord(w)) {
                    out.add(new Tok(T.ID, w));
                } else {
                    for (char ch : w.toCharArray()) out.add(new Tok(T.ID, String.valueOf(ch)));
                }
                continue;
            }
            switch (c) {
                case '+': out.add(new Tok(T.OP, "+")); break;
                case '-': case '−': out.add(new Tok(T.OP, "-")); break;
                case '*': case '×': case '·': out.add(new Tok(T.OP, "*")); break;
                case '/': case '÷': out.add(new Tok(T.OP, "/")); break;
                case '^': out.add(new Tok(T.OP, "^")); break;
                case '²': out.add(new Tok(T.OP, "^")); out.add(new Tok(T.NUM, "2")); break;
                case '³': out.add(new Tok(T.OP, "^")); out.add(new Tok(T.NUM, "3")); break;
                case '!': out.add(new Tok(T.OP, "!")); break;
                case '%': out.add(new Tok(T.OP, "%")); break;
                case '(': out.add(new Tok(T.LP, "(")); break;
                case ')': out.add(new Tok(T.RP, ")")); break;
                case '=': out.add(new Tok(T.OP, "=")); break;
                default:
                    throw new MathError("Неизвестный символ «" + c + "»");
            }
            i++;
        }
        out.add(new Tok(T.END, ""));
        return out;
    }

    private static boolean isWord(String w) {
        for (String s : WORDS) if (s.equals(w)) return true;
        return false;
    }

    // ---------- рекурсивный спуск ----------

    private Tok peek() {
        return toks.get(pos);
    }

    private Tok next() {
        return toks.get(pos++);
    }

    private boolean isOp(String op) {
        Tok t = peek();
        return t.type == T.OP && t.text.equals(op);
    }

    private boolean startsOperand() {
        T t = peek().type;
        return t == T.NUM || t == T.ID || t == T.LP;
    }

    private Node expr() {
        Node n = term();
        while (isOp("+") || isOp("-")) {
            String op = next().text;
            n = Node.bin(op, n, term());
        }
        return n;
    }

    private Node term() {
        Node n = unary();
        while (true) {
            if (isOp("*") || isOp("/")) {
                String op = next().text;
                n = Node.bin(op, n, unary());
            } else if (startsOperand()) {
                n = Node.bin("*", n, unary()); // неявное умножение
            } else {
                break;
            }
        }
        return n;
    }

    private Node unary() {
        if (isOp("-")) {
            next();
            return Node.neg(unary());
        }
        if (isOp("+")) {
            next();
            return unary();
        }
        return power();
    }

    private Node power() {
        Node base = postfix();
        if (isOp("^")) {
            next();
            return Node.bin("^", base, unary());
        }
        return base;
    }

    private Node postfix() {
        Node n = primary();
        while (isOp("!") || isOp("%")) {
            String op = next().text;
            n = "!".equals(op) ? Node.fun("!", n) : Node.bin("/", n, Node.num(Rational.of(100)));
        }
        return n;
    }

    private Node primary() {
        Tok t = peek();
        switch (t.type) {
            case NUM:
                next();
                return Node.num(Rational.parseDecimal(t.text));
            case LP: {
                next();
                Node n = expr();
                if (peek().type != T.RP) throw new MathError("Не закрыта скобка");
                next();
                return n;
            }
            case ID: {
                next();
                String w = t.text;
                if ("pi".equals(w) || "π".equals(w)) return Node.constant("pi");
                if ("e".equals(w)) return Node.constant("e");
                if (Functions.isFunction(w)) {
                    Node arg;
                    if (peek().type == T.LP) {
                        next();
                        arg = expr();
                        if (peek().type != T.RP) throw new MathError("Не закрыта скобка у функции " + w);
                        next();
                    } else {
                        arg = postfix();
                    }
                    return Node.fun(w, arg);
                }
                if (w.length() == 1 && Character.isLetter(w.charAt(0))) return Node.var(w);
                throw new MathError("Неизвестное слово «" + w + "»");
            }
            case END:
                throw new MathError("Выражение обрывается");
            default:
                throw new MathError("Ожидалось число или выражение, найдено «" + t.text + "»");
        }
    }
}
