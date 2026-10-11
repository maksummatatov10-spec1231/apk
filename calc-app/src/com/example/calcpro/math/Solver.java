package com.example.calcpro.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Решатель: выражения с пошаговым вычислением, уравнения и линейные системы с подробным решением. */
public final class Solver {

    /** Результат решения: заголовок, шаги, ответы, функция для графика и отметки корней. */
    public static final class Result {
        public String heading = "";
        public final List<String> steps = new ArrayList<>();
        public final List<String> answers = new ArrayList<>();
        public Node graph;
        public final List<Double> marks = new ArrayList<>();
        /** Запись для тетради: только уравнения, неравенства и ответы, без пояснений. */
        public final List<String> notebook = new ArrayList<>();
    }

    private Solver() {}

    public static Result solve(String raw) {
        String s = raw == null ? "" : normalizeRoots(raw.trim());
        if (s.isEmpty()) throw new MathError("Введите выражение или уравнение");
        Result root = solveSquareRoot(s);
        if (root != null) return root;
        List<String> parts = new ArrayList<>();
        for (String p : s.split("[;\\n]+")) {
            if (!p.trim().isEmpty()) parts.add(p.trim());
        }
        if (parts.size() >= 2) return solveSystem(parts);
        if (s.indexOf('=') >= 0) {
            Matcher m = GRAPH_FORM.matcher(s);
            if (m.matches()) return solveGraphForm(m.group(1));
            return solveEquation(s);
        }
        return solveExpression(s);
    }

    /** «корень из 50», «квадратный корень 50» → «√50». */
    private static final Pattern ROOT_WORD = Pattern.compile(
            "(?:квадратн[а-яё]*\\s+)?корень\\s*(?:из\\s*)?(?=[0-9(])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static String normalizeRoots(String s) {
        return ROOT_WORD.matcher(s).replaceAll("√");
    }

    /** Простой корень из натурального числа: √50, √(50). */
    private static final Pattern PLAIN_SQRT = Pattern.compile("√\\s*\\(?\\s*([0-9]+)\\s*\\)?");

    /**
     * √n для n, не являющегося полным квадратом: точный вид k√m (m без квадратных множителей)
     * и десятичные приближения. Для полного квадрата возвращает null — решает общий путь.
     */
    private static Result solveSquareRoot(String s) {
        Matcher m = PLAIN_SQRT.matcher(s);
        if (!m.matches()) return null;
        long n;
        try {
            n = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (n < 2 || n > 1_000_000_000_000L) return null;
        long root = Math.round(Math.sqrt((double) n));
        if (root * root == n) return null;

        long k = 1, rest = n;
        for (long d = 2; d * d <= rest; d++) {
            while (rest % (d * d) == 0) {
                rest /= d * d;
                k *= d;
            }
        }
        long sq = k * k;
        String exact = k == 1 ? "√" + rest : k + "√" + rest;
        double value = Math.sqrt((double) n);

        Result r = new Result();
        r.heading = "Корень из " + n;
        r.steps.add("√" + n);
        if (k > 1) {
            r.steps.add("= √" + sq + " · √" + rest);
            r.steps.add("= " + exact);
        } else {
            r.steps.add(exact + " — уже в простейшем виде");
        }
        r.answers.add("Ответ: " + exact + " ≈ " + decimal(value, 6));
        r.answers.add("≈ " + decimal(value, 2) + " (до сотых)");
        r.answers.add("≈ " + decimal(value, 3) + " (до тысячных)");
        r.answers.add("≈ " + decimal(value, 4) + " (до десятитысячных)");
        // Запись для тетради: только формулы.
        if (k > 1) {
            r.notebook.add("√" + n + " = √" + sq + " · √" + rest + " = " + exact);
        }
        r.notebook.add("√" + n + " ≈ " + decimal(value, 4));
        return r;
    }

    private static String decimal(double x, int digits) {
        return new java.math.BigDecimal(x).setScale(digits, java.math.RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    /** Запись графика: «y = f(x)» или «f(x) = …». */
    private static final Pattern GRAPH_FORM = Pattern.compile("\\s*(?:y|f\\(x\\))\\s*=(.+)", Pattern.DOTALL);

    private static Result solveGraphForm(String rhs) {
        Node n = Parser.parse(rhs);
        Set<String> vars = new TreeSet<>();
        n.collectVars(vars);
        if (!(vars.isEmpty() || (vars.size() == 1 && vars.contains("x")))) {
            throw new MathError("График строится по переменной x: правая часть должна зависеть только от x");
        }
        Result r = new Result();
        r.heading = "График функции";
        r.steps.add("f(x) = " + n.show());
        r.graph = n;
        return r;
    }

    // ---------- выражения ----------

    private static Result solveExpression(String s) {
        Node n = Parser.parse(s);
        Result r = new Result();
        if (n.hasVar("x")) {
            r.heading = "График функции";
            r.steps.add("f(x) = " + n.show());
            r.graph = n;
            return r;
        }
        r.heading = "Вычисление";
        r.steps.add(n.show());
        Node cur = n;
        for (int guard = 0; guard < 500; guard++) {
            Node next = cur.stepOnce();
            if (next == null) break;
            cur = next;
            r.steps.add("= " + cur.show());
        }
        Rational ex = n.exact(null);
        if (ex != null) {
            r.answers.add("Ответ: " + Fmt.ratApprox(ex));
        } else {
            r.answers.add("Ответ: " + Fmt.num(n.eval(Collections.<String, Double>emptyMap())));
        }
        return r;
    }

    // ---------- уравнения с одной переменной ----------

    private static Result solveEquation(String s) {
        String[] sides = s.split("=", -1);
        if (sides.length != 2) throw new MathError("Уравнение должно содержать ровно один знак «=»");
        if (sides[0].trim().isEmpty() || sides[1].trim().isEmpty()) throw new MathError("Пустая часть уравнения");
        Node left = Parser.parse(sides[0]);
        Node right = Parser.parse(sides[1]);
        Result r = new Result();
        r.heading = "Уравнение";
        r.steps.add("Исходное уравнение: " + left.show() + " = " + right.show());
        r.notebook.add(left.show() + " = " + right.show());

        Set<String> vars = new TreeSet<>();
        left.collectVars(vars);
        right.collectVars(vars);
        if (vars.isEmpty()) {
            Rational le = left.exact(null);
            Rational re = right.exact(null);
            boolean ok = (le != null && re != null)
                    ? le.equals(re)
                    : Math.abs(left.eval(Collections.<String, Double>emptyMap())
                        - right.eval(Collections.<String, Double>emptyMap())) < 1e-9;
            r.answers.add(ok ? "Равенство верно — подходит любое x" : "Равенство неверно — решений нет");
            return r;
        }
        if (!(vars.size() == 1 && vars.contains("x"))) {
            throw new MathError("Уравнение с одной переменной решаю для x. Для нескольких переменных используйте систему (с новой строкой или «;»)");
        }

        Node f = Node.bin("-", left, right);
        Poly pl = left.toPoly();
        Poly pr = right.toPoly();
        if (pl != null && pr != null) {
            Poly p = pl.sub(pr);
            if (!right.isZeroLiteral()) {
                String rs = right.isNumber() || right.kind == Node.Kind.VAR ? right.show() : "(" + right.show() + ")";
                r.steps.add("Переносим всё в левую часть: " + left.show() + " − " + rs + " = 0");
            }
            r.steps.add("Раскрываем скобки и приводим подобные: " + p.format() + " = 0");
            r.notebook.add(p.format() + " = 0");
            solvePoly(p, r);
        } else if (!solveRadical(left, right, f, r)) {
            solveNumeric(f, r);
        }
        normalizeRoots(r);
        for (String a : r.answers) r.notebook.add(a);
        r.graph = f;
        return r;
    }

    /**
     * Нумерует корни единообразно: x₁, x₂, … по возрастанию действительной части.
     * Комплексные корни ставятся в конец. Работает, если все ответы — корни.
     */
    private static void normalizeRoots(Result r) {
        List<String> answers = r.answers;
        if (answers.size() < 2) return;
        for (String a : answers) if (!a.startsWith("x")) return;
        List<String> reals = new ArrayList<>();
        List<Double> keys = new ArrayList<>();
        List<String> complexes = new ArrayList<>();
        int k = 0;
        for (String a : answers) {
            if (a.contains("i")) {
                complexes.add(a);
            } else {
                reals.add(a);
                keys.add(k < r.marks.size() ? r.marks.get(k) : 0.0);
                k++;
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < reals.size(); i++) order.add(i);
        Collections.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer i, Integer j) {
                return Double.compare(keys.get(i), keys.get(j));
            }
        });
        List<String> out = new ArrayList<>();
        for (int i : order) out.add(reals.get(i));
        out.addAll(complexes);
        for (int i = 0; i < out.size(); i++) out.set(i, relabel(out.get(i), i + 1));
        answers.clear();
        answers.addAll(out);
    }

    private static String relabel(String a, int n) {
        int eq = a.indexOf(" = ");
        int approx = a.indexOf(" ≈ ");
        int cut = eq < 0 ? approx : (approx < 0 ? eq : Math.min(eq, approx));
        if (cut < 0) return a;
        return "x" + Fmt.sub(n) + a.substring(cut);
    }

    private static void solvePoly(Poly p, Result r) {
        if (p.isZero()) {
            r.answers.add("x — любое число (получилось 0 = 0)");
            return;
        }
        int d = p.degree();
        if (d == 0) {
            r.answers.add("Решений нет: " + p.format() + " = 0 не выполняется ни при каком x");
            return;
        }
        if (d == 1) {
            Rational a = p.coef(1);
            Rational b = p.coef(0);
            Rational root = b.neg().div(a);
            r.steps.add("Линейное уравнение: " + Fmt.rat(a) + "·x = " + Fmt.rat(b.neg()));
            r.steps.add("x = −b/a = " + Fmt.par(b.neg()) + "/" + Fmt.par(a) + " = " + Fmt.ratApprox(root));
            r.answers.add("x = " + Fmt.ratApprox(root));
            r.marks.add(root.toDouble());
            return;
        }
        if (d == 2) {
            quadratic(p, r);
            return;
        }

        r.steps.add("Степень многочлена: " + d + ". Ищем рациональные корни по теореме о рациональном корне.");
        Rational root = findRationalRoot(p, r);
        if (root != null) {
            Poly q = p.divideByLinear(root);
            r.steps.add("Нашли корень x = " + Fmt.par(root) + ". Делим многочлен на (x − " + Fmt.par(root) + "):");
            r.steps.add("(x − " + Fmt.par(root) + ") · (" + q.format() + ") = 0");
            r.answers.add("x = " + Fmt.ratApprox(root));
            r.marks.add(root.toDouble());
            solvePoly(q, r);
            return;
        }
        r.steps.add("Рациональных корней нет — находим все корни численно (метод Дюрана–Кернера).");
        for (double[] z : durandKerner(p)) {
            if (Math.abs(z[1]) < 1e-9) {
                r.answers.add("x ≈ " + Fmt.num(z[0]));
                r.marks.add(z[0]);
            } else if (z[1] > 0) {
                // комплексно-сопряжённые корни выводим парой «a ± bi», второй не дублируем
                r.answers.add("x ≈ " + Fmt.complex(z[0], z[1]));
            }
        }
    }

    private static void quadratic(Poly p, Result r) {
        Rational a = p.coef(2);
        Rational b = p.coef(1);
        Rational c = p.coef(0);
        r.steps.add("Квадратное уравнение: a = " + Fmt.rat(a) + ", b = " + Fmt.rat(b) + ", c = " + Fmt.rat(c));
        Rational disc = b.mul(b).sub(Rational.of(4).mul(a).mul(c));
        r.notebook.add("D = " + Fmt.rat(disc));
        r.steps.add("Дискриминант: D = b² − 4ac = " + Fmt.par(b) + "² − 4·" + Fmt.par(a) + "·" + Fmt.par(c)
                + " = " + Fmt.rat(disc));
        Rational twoA = Rational.of(2).mul(a);

        if (disc.signum() < 0) {
            double re = -b.toDouble() / twoA.toDouble();
            double im = Math.sqrt(-disc.toDouble()) / Math.abs(twoA.toDouble());
            r.steps.add("D < 0 — действительных корней нет, находим комплексные: x = (−b ± i√(−D)) / 2a");
            r.answers.add("x₁,₂ = " + Fmt.complex(re, im));
            return;
        }
        if (disc.isZero()) {
            Rational root = b.neg().div(twoA);
            r.steps.add("D = 0 — один корень (двукратный): x = −b/2a = " + Fmt.ratApprox(root));
            r.answers.add("x = " + Fmt.ratApprox(root));
            r.marks.add(root.toDouble());
            return;
        }

        BigInteger sq = exactSqrt(disc);
        if (sq != null) {
            Rational s = Rational.of(sq);
            Rational x1 = b.neg().sub(s).div(twoA);
            Rational x2 = b.neg().add(s).div(twoA);
            r.steps.add("√D = " + Fmt.rat(s) + " (точный квадрат)");
            r.steps.add("x = (−b ± √D) / 2a = (" + Fmt.rat(b.neg()) + " ± " + Fmt.rat(s) + ") / " + Fmt.par(twoA));
            addSorted(r, x1, x2);
        } else {
            double sd = Math.sqrt(disc.toDouble());
            r.steps.add("√D ≈ " + Fmt.num(sd) + ", значит x = (−b ± √D) / 2a");
            double x1 = (-b.toDouble() - sd) / twoA.toDouble();
            double x2 = (-b.toDouble() + sd) / twoA.toDouble();
            double lo = Math.min(x1, x2), hi = Math.max(x1, x2);
            r.answers.add("x₁ ≈ " + Fmt.num(lo));
            r.answers.add("x₂ ≈ " + Fmt.num(hi));
            r.marks.add(lo);
            r.marks.add(hi);
        }
    }

    private static void addSorted(Result r, Rational x1, Rational x2) {
        Rational lo = x1.compareTo(x2) <= 0 ? x1 : x2;
        Rational hi = x1.compareTo(x2) <= 0 ? x2 : x1;
        r.answers.add("x₁ = " + Fmt.ratApprox(lo));
        r.answers.add("x₂ = " + Fmt.ratApprox(hi));
        r.marks.add(lo.toDouble());
        r.marks.add(hi.toDouble());
    }

    /** Целый квадратный корень для неотрицательного рационального с целым значением, иначе null. */
    private static BigInteger exactSqrt(Rational v) {
        if (!v.isInteger() || v.signum() < 0) return null;
        BigInteger n = v.numerator();
        if (n.bitLength() > 60) return null;
        long x = n.longValue();
        long s = (long) Math.sqrt((double) x);
        while (s * s > x) s--;
        while ((s + 1) * (s + 1) <= x) s++;
        return s * s == x ? BigInteger.valueOf(s) : null;
    }

    /** Ищет рациональный корень среди ±делитель(свободного)/делитель(старшего). */
    private static Rational findRationalRoot(Poly p, Result r) {
        BigInteger lcm = BigInteger.ONE;
        for (int i = 0; i < p.size(); i++) {
            BigInteger den = p.coef(i).denominator();
            lcm = lcm.divide(lcm.gcd(den)).multiply(den);
        }
        int n = p.size() - 1;
        BigInteger a0 = p.coef(0).numerator().multiply(lcm.divide(p.coef(0).denominator()));
        BigInteger an = p.coef(n).numerator().multiply(lcm.divide(p.coef(n).denominator()));
        if (a0.signum() == 0) {
            r.steps.add("Свободный член равен 0, значит x = 0 — корень.");
            return Rational.ZERO;
        }
        if (a0.bitLength() > 40 || an.bitLength() > 40) return null;
        List<Long> dp = divisors(a0.abs().longValue());
        List<Long> dq = divisors(an.abs().longValue());
        Set<Rational> seen = new HashSet<>();
        List<String> tried = new ArrayList<>();
        Rational found = null;
        for (long pp : dp) {
            for (long qq : dq) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    Rational cand = Rational.of(sign * pp, qq);
                    if (!seen.add(cand)) continue;
                    if (tried.size() < 12) tried.add(Fmt.rat(cand));
                    if (found == null && p.eval(cand).isZero()) found = cand;
                }
            }
        }
        r.steps.add("Проверяем кандидаты: " + join(tried) + (seen.size() > 12 ? " и др." : ""));
        return found;
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    private static List<Long> divisors(long n) {
        List<Long> out = new ArrayList<>();
        for (long i = 1; i * i <= n && out.size() < 400; i++) {
            if (n % i == 0) {
                out.add(i);
                if (i != n / i) out.add(n / i);
            }
        }
        return out;
    }

    /** Все комплексные корни монического многочлена, метод Дюрана–Кернера. Результат: {re, im}. */
    private static List<double[]> durandKerner(Poly p) {
        int n = p.degree();
        double lead = p.coef(n).toDouble();
        double[] a = new double[n + 1];
        double maxAbs = 0;
        for (int i = 0; i <= n; i++) {
            a[i] = p.coef(i).toDouble() / lead;
            maxAbs = Math.max(maxAbs, Math.abs(a[i]));
        }
        double radius = 1 + maxAbs;
        double[][] z = new double[n][2];
        for (int k = 0; k < n; k++) {
            double ang = 2 * Math.PI * k / n + 0.4;
            z[k][0] = radius * Math.cos(ang);
            z[k][1] = radius * Math.sin(ang);
        }
        for (int iter = 0; iter < 2000; iter++) {
            double maxDelta = 0;
            for (int i = 0; i < n; i++) {
                double[] val = evalMonic(a, z[i]);
                double[] den = {1, 0};
                for (int j = 0; j < n; j++) {
                    if (j == i) continue;
                    den = cmul(den, new double[]{z[i][0] - z[j][0], z[i][1] - z[j][1]});
                }
                double[] delta = cdiv(val, den);
                z[i][0] -= delta[0];
                z[i][1] -= delta[1];
                maxDelta = Math.max(maxDelta, Math.hypot(delta[0], delta[1]));
            }
            if (maxDelta < 1e-14) break;
        }
        List<double[]> out = new ArrayList<>();
        for (double[] v : z) out.add(new double[]{v[0], v[1]});
        return out;
    }

    private static double[] evalMonic(double[] a, double[] z) {
        double[] acc = {0, 0};
        for (int i = a.length - 1; i >= 0; i--) {
            acc = cmul(acc, z);
            acc[0] += a[i];
        }
        return acc;
    }

    private static double[] cmul(double[] x, double[] y) {
        return new double[]{x[0] * y[0] - x[1] * y[1], x[0] * y[1] + x[1] * y[0]};
    }

    private static double[] cdiv(double[] x, double[] y) {
        double d = y[0] * y[0] + y[1] * y[1];
        return new double[]{(x[0] * y[0] + x[1] * y[1]) / d, (x[1] * y[0] - x[0] * y[1]) / d};
    }

    /** Нелинейное уравнение: сканирование знака на [−50; 50] и уточнение бисекцией. */
    /**
     * Уравнение вида √(A) = P или ∛(A) = P (P — многочлен): возводим в степень, получаем многочлен,
     * его корни проверяем подстановкой в исходное уравнение (отсекаем посторонние корни и ОДЗ).
     * Возвращает false, если уравнение не такого вида.
     */
    private static boolean solveRadical(Node left, Node right, Node f, Result r) {
        Poly inner;
        Poly p;
        int n;
        List<Poly> domain = new ArrayList<>(); // подкоренные выражения √ — должны быть ≥ 0
        Poly nonneg = null;                    // правая часть одиночного √ — тоже ≥ 0
        if (isRadical(left) && isRadical(right) && left.name.equals(right.name)) {
            // √(A) = √(B): под корнями одинаковой степени — приравниваем подкоренные выражения
            inner = left.a.toPoly();
            p = right.a.toPoly();
            n = 1;
            if (inner == null || p == null) return false;
            if ("sqrt".equals(left.name)) {
                domain.add(inner);
                domain.add(p);
            }
            r.steps.add("Корни одинаковой степени: приравниваем подкоренные выражения.");
        } else {
            Node rad;
            Node other;
            if (isRadical(left) && right.toPoly() != null) {
                rad = left;
                other = right;
            } else if (isRadical(right) && left.toPoly() != null) {
                rad = right;
                other = left;
            } else {
                return false;
            }
            inner = rad.a.toPoly();
            p = other.toPoly();
            if (inner == null || p == null) return false;
            n = "sqrt".equals(rad.name) ? 2 : 3;
            if (n == 2) {
                domain.add(inner);
                nonneg = p;
            }
            r.steps.add("Избавляемся от корня " + ("sqrt".equals(rad.name) ? "√" : "∛")
                    + ": возводим обе части в степень " + n + ".");
        }
        Poly eq = inner.sub(p.pow(n));
        r.steps.add("Получаем: " + inner.format() + " = " + p.pow(n).format());
        if (eq.degree() == 0) {
            if (eq.isZero()) return false;   // тождество: решения — вся область, отдаём численному методу
            r.steps.add("Получаем неверное равенство " + eq.format() + " = 0: решений нет.");
            r.answers.add("Решений нет");
            return true;
        }
        r.steps.add("Переносим всё в левую часть: " + eq.format() + " = 0");
        r.notebook.add(eq.format() + " = 0");

        // ОДЗ: подкоренные выражения (и правая часть одиночного √) неотрицательны
        List<String> odz = new ArrayList<>();
        for (Poly d : domain) odz.add(d.format() + " ≥ 0");
        if (nonneg != null) odz.add(nonneg.format() + " ≥ 0");
        if (!odz.isEmpty()) {
            String odzText = String.join(", ", odz);
            r.steps.add("ОДЗ: " + odzText);
            r.notebook.add("ОДЗ: " + odzText);
        }

        // Корни: квадратное — по формуле с дискриминантом, иначе численно
        List<Double> cands = new ArrayList<>();
        if (eq.degree() == 1) {
            cands.add(eq.coef(0).neg().div(eq.coef(1)).toDouble());
        } else if (eq.degree() == 2) {
            Rational a2 = eq.coef(2), b2 = eq.coef(1), c2 = eq.coef(0);
            Rational disc = b2.mul(b2).sub(Rational.of(4).mul(a2).mul(c2));
            r.steps.add("D = b² − 4ac = " + Fmt.rat(disc));
            r.notebook.add("D = " + Fmt.rat(disc));
            if (disc.signum() >= 0) {
                double sd = Math.sqrt(disc.toDouble());
                double twoA = 2 * a2.toDouble();
                double x1 = (-b2.toDouble() - sd) / twoA;
                double x2 = (-b2.toDouble() + sd) / twoA;
                r.steps.add("x₁ = (−b − √D) / 2a = " + Fmt.num(x1) + ",  x₂ = (−b + √D) / 2a = " + Fmt.num(x2));
                cands.add(x1);
                if (Math.abs(x1 - x2) > 1e-12) cands.add(x2);
            }
        } else {
            for (double[] c : durandKerner(eq)) {
                if (Math.abs(c[1]) <= 1e-7) cands.add(c[0]);
            }
        }

        List<Double> xs = new ArrayList<>();
        for (double c : cands) {
            double x = c;
            // Корень, близкий к целому (например 2 − 1e-16), снапим: иначе подкоренное выражение
            // становится чуть отрицательным, и √ даёт NaN, а верный корень теряется.
            double nearest = Math.rint(x);
            if (Math.abs(x - nearest) < 1e-9) x = nearest;

            // проверка каждого корня: ОДЗ и подстановка в исходное уравнение
            List<String> parts = new ArrayList<>();
            boolean inDom = true;
            for (Poly d : domain) {
                double val = polyAt(d, x);
                parts.add(d.format() + " = " + Fmt.num(val) + " ≥ 0");
                if (val < -1e-9) inDom = false;
            }
            if (nonneg != null) {
                double val = polyAt(nonneg, x);
                parts.add(nonneg.format() + " = " + Fmt.num(val) + " ≥ 0");
                if (val < -1e-9) inDom = false;
            }
            Double v = evalAt(f, x);
            boolean valid;
            if (v != null) {
                valid = Math.abs(v) <= 1e-6;                // подстановка в исходное уравнение
            } else {
                valid = inDom;                              // NaN: только ОДЗ проверяем алгебраически
            }
            String label = "x = " + Fmt.num(x);
            if (valid && inDom) {
                addRoot(xs, x);
                r.steps.add("Проверка " + label + ": " + (parts.isEmpty() ? "" : String.join(", ", parts) + " — ОДЗ выполнена, ")
                        + "корень подходит");
                if (!parts.isEmpty()) r.notebook.add(label + ": " + String.join(", ", parts));
            } else {
                r.steps.add("Проверка " + label + ": " + (parts.isEmpty() ? "" : String.join(", ", parts) + " — ")
                        + (inDom ? "подстановка в исходное уравнение неверна" : "не входит в ОДЗ")
                        + ", посторонний корень");
            }
        }
        if (xs.isEmpty()) {
            r.steps.add("Все корни отброшены проверкой: решений нет.");
            r.answers.add("Решений нет");
            return true;
        }
        Collections.sort(xs);
        for (double x : xs) {
            double nearest = Math.rint(x);
            boolean integer = Math.abs(x - nearest) < 1e-6;
            r.answers.add(integer ? "x = " + Fmt.num(nearest) : "x ≈ " + Fmt.num(x));
            r.marks.add(x);
        }
        return true;
    }

    private static double polyAt(Poly p, double x) {
        double acc = 0;
        for (int i = p.size() - 1; i >= 0; i--) acc = acc * x + p.coef(i).toDouble();
        return acc;
    }

    private static boolean inDomain(List<Poly> domain, double x) {
        for (Poly d : domain) {
            if (polyAt(d, x) < -1e-9) return false;
        }
        return true;
    }

    private static boolean isRadical(Node n) {
        return n.kind == Node.Kind.FUN && ("sqrt".equals(n.name) || "cbrt".equals(n.name));
    }

    /**
     * Численный поиск корней на [−10; 10]: шаг 0,01, смена знака (бисекция) и касания
     * (минимум |f(x)| около нуля). Точки разрыва (например, tan) отсекаются проверкой |f| в корне.
     */
    private static void solveNumeric(Node f, Result r) {
        r.steps.add("Уравнение не сводится к многочлену. Ищем корни численно: сканируем f(x) = "
                + f.show() + " на отрезке [−10; 10] с шагом 0,01, ищем смену знака и касания; "
                + "корни уточняем бисекцией. Точки разрыва отбрасываем.");
        double step = 0.01;
        List<Double> roots = new ArrayList<>();
        Double ppF = null;          // f(x − 2·шаг)
        Double prevF = null;        // f(x − шаг)
        double prevX = -10;
        for (int i = 0; i <= 2000 && roots.size() < 20; i++) {
            double x = -10 + i * step;
            Double fx = evalAt(f, x);
            if (fx != null && fx == 0.0) {
                addRoot(roots, x);
            } else if (fx != null && prevF != null && prevF * fx < 0) {
                double root = bisect(f, prevX, x);
                if (isRoot(f, root)) addRoot(roots, root);
            }
            // касание: |f| имеет локальный минимум около нуля в точке prevX
            if (fx != null && prevF != null && ppF != null
                    && Math.abs(prevF) <= Math.abs(ppF) && Math.abs(prevF) <= Math.abs(fx)
                    && Math.abs(prevF) < 1e-3) {
                double root = minimizeAbs(f, prevX - step, x);
                if (isRoot(f, root)) addRoot(roots, root);
            }
            ppF = prevF;
            prevF = fx;
            prevX = x;
        }
        if (roots.isEmpty()) {
            r.answers.add("Решений на отрезке [−10; 10] нет");
            return;
        }
        Collections.sort(roots);
        for (double root : roots) {
            r.answers.add("x ≈ " + Fmt.num(root));
            r.marks.add(root);
        }
    }

    private static boolean isRoot(Node f, double x) {
        Double v = evalAt(f, x);
        return v != null && Math.abs(v) < 1e-7;
    }

    /** Точка минимума |f| на отрезке (тернарный поиск, |f| унимодальна рядом с касанием). */
    private static double minimizeAbs(Node f, double lo, double hi) {
        for (int it = 0; it < 200; it++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            if (absAt(f, m1) < absAt(f, m2)) hi = m2;
            else lo = m1;
        }
        return (lo + hi) / 2;
    }

    private static double absAt(Node f, double x) {
        Double v = evalAt(f, x);
        return v == null ? Double.MAX_VALUE : Math.abs(v);
    }

    private static void addRoot(List<Double> roots, double x) {
        for (double y : roots) if (Math.abs(x - y) < 1e-6) return;
        roots.add(x);
    }

    private static double bisect(Node f, double a, double b) {
        double lo = a, hi = b;
        Double flo = evalAt(f, lo);
        for (int it = 0; it < 100; it++) {
            double mid = (lo + hi) / 2;
            Double fm = evalAt(f, mid);
            if (fm == null) break;
            if (flo != null && flo * fm <= 0) {
                hi = mid;
            } else {
                lo = mid;
                flo = fm;
            }
        }
        return (lo + hi) / 2;
    }

    private static Double evalAt(Node f, double x) {
        Map<String, Double> env = new HashMap<>();
        env.put("x", x);
        try {
            double v = f.eval(env);
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            return v;
        } catch (MathError e) {
            return null;
        }
    }

    // ---------- системы линейных уравнений ----------

    private static Result solveSystem(List<String> eqs) {
        Result r = new Result();
        r.heading = "Система уравнений";
        List<Node> fs = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        Set<String> varSet = new TreeSet<>();
        for (String e : eqs) {
            String[] sides = e.split("=", -1);
            if (sides.length != 2) throw new MathError("В каждой строке системы должен быть ровно один знак «=»: «" + e + "»");
            Node l = Parser.parse(sides[0]);
            Node rr = Parser.parse(sides[1]);
            l.collectVars(varSet);
            rr.collectVars(varSet);
            fs.add(Node.bin("-", l, rr));
            lines.add(l.show() + " = " + rr.show());
        }
        List<String> vars = new ArrayList<>();
        for (String v : new String[]{"x", "y", "z"}) {
            if (varSet.contains(v)) vars.add(v);
        }
        for (String v : varSet) {
            if (!vars.contains(v)) throw new MathError("В системе допустимы переменные x, y, z");
        }
        int n = vars.size();
        if (n == 0) throw new MathError("В системе нет переменных");
        if (fs.size() != n) {
            throw new MathError("Уравнений: " + fs.size() + ", переменных: " + n + ". Для единственного решения их должно быть поровну");
        }

        r.steps.add("Исходная система:");
        for (String line : lines) r.steps.add("   " + line);

        Rational[][] a = new Rational[n][n];
        Rational[] b = new Rational[n];
        for (int i = 0; i < n; i++) {
            Node f = fs.get(i);
            Map<String, Rational> zero = new HashMap<>();
            for (String v : vars) zero.put(v, Rational.ZERO);
            Rational c0 = f.exact(zero);
            if (c0 == null) throw new MathError("Система должна быть линейной с рациональными коэффициентами");
            for (int j = 0; j < n; j++) {
                Map<String, Rational> unit = new HashMap<>(zero);
                unit.put(vars.get(j), Rational.ONE);
                Rational v1 = f.exact(unit);
                if (v1 == null) throw new MathError("Система должна быть линейной с рациональными коэффициентами");
                a[i][j] = v1.sub(c0);
            }
            // проверка линейности: подставляем точку (2, 3, 5)
            Map<String, Rational> test = new HashMap<>();
            Rational expect = c0;
            for (int j = 0; j < n; j++) {
                Rational tv = Rational.of(j + 2);
                test.put(vars.get(j), tv);
                expect = expect.add(a[i][j].mul(tv));
            }
            Rational got = f.exact(test);
            if (got == null || !got.equals(expect)) throw new MathError("Пока решаю только линейные системы");
            b[i] = c0.neg();
        }

        r.steps.add("Приводим к виду a·x + b·y + … = c:");
        for (int i = 0; i < n; i++) r.steps.add("   " + rowText(a[i], vars) + " = " + Fmt.rat(b[i]));

        Rational d = det(a, n);
        r.steps.add("Определитель матрицы коэффициентов: D = " + Fmt.rat(d));
        if (d.isZero()) {
            r.answers.add("D = 0 — единственного решения нет (система несовместна или имеет бесконечно много решений)");
            return r;
        }
        r.steps.add("Метод Крамера: x = Dx / D, где Dx — определитель, в котором столбец x заменён столбцом свободных членов.");
        for (int j = 0; j < n; j++) {
            Rational[][] m = new Rational[n][n];
            for (int i = 0; i < n; i++)
                for (int k = 0; k < n; k++)
                    m[i][k] = (k == j) ? b[i] : a[i][k];
            Rational dj = det(m, n);
            Rational xj = dj.div(d);
            r.steps.add("D" + vars.get(j) + " = " + Fmt.rat(dj) + ";   " + vars.get(j) + " = " + Fmt.rat(dj)
                    + " / " + Fmt.par(d) + " = " + Fmt.ratApprox(xj));
            r.answers.add(vars.get(j) + " = " + Fmt.ratApprox(xj));
            r.marks.add(xj.toDouble());
        }
        return r;
    }

    private static String rowText(Rational[] row, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < row.length; j++) {
            Rational k = row[j];
            if (k.isZero()) continue;
            boolean neg = k.signum() < 0;
            Rational abs = k.abs();
            if (sb.length() == 0) {
                if (neg) sb.append('−');
            } else {
                sb.append(neg ? " − " : " + ");
            }
            if (!abs.equals(Rational.ONE)) sb.append(Fmt.par(abs));
            sb.append(vars.get(j));
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    private static Rational det(Rational[][] m, int n) {
        if (n == 1) return m[0][0];
        Rational res = Rational.ZERO;
        for (int c = 0; c < n; c++) {
            Rational[][] sub = new Rational[n - 1][n - 1];
            for (int i = 1; i < n; i++) {
                int cc = 0;
                for (int j = 0; j < n; j++) {
                    if (j == c) continue;
                    sub[i - 1][cc++] = m[i][j];
                }
            }
            Rational term = m[0][c].mul(det(sub, n - 1));
            res = (c % 2 == 0) ? res.add(term) : res.sub(term);
        }
        return res;
    }
}
