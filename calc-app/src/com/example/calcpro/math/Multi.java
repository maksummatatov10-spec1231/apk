package com.example.calcpro.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Уравнения и системы с несколькими переменными (x, y, z).
 * 1) Многочлен от нескольких переменных: точно выносим общий одночлен, остальное — численно.
 * 2) Нелинейная система: численный поиск (метод Ньютона–Леверберга–Марквардта с сеткой стартовых точек).
 * Точные символьные решения произвольных нелинейных систем не существуют, поэтому там ответ численный.
 */
final class Multi {
    private Multi() {}

    private static final double[] GRID = {-10, -5, -3, -2, -1, -0.5, 0, 0.5, 1, 2, 3, 5, 10};
    private static final int MAX_SOLUTIONS = 40;
    private static final double TOL = 1e-8;

    /** Вектор невязок F(x); null, если в точке выражение не определено. */
    interface Residual {
        double[] at(double[] x);
    }

    /** Точка входа: fs — левые части (f = 0), vars — переменные по порядку. */
    static void solve(List<Node> fs, List<String> vars, List<String> lines, Solver.Result r) {
        int n = vars.size();
        int m = fs.size();
        if (m == 1) {
            r.heading = "Уравнение с несколькими переменными";
        } else {
            r.heading = "Система уравнений";
        }
        r.steps.add("Исходная система:");
        for (String line : lines) r.steps.add("   " + line);
        if (n == 2) {
            for (Node f : fs) r.implicit.add(f);
        }

        if (m == 1) {
            MP p = MP.from(fs.get(0), vars);
            if (p != null) {
                solvePolynomial(p, vars, r);
                addNotebook(r);
                return;
            }
        }

        r.steps.add("Нелинейная система: точного общего метода нет, решаю численно (метод Ньютона из множества стартовых точек).");
        List<double[]> sols = search(nodeResidual(fs, vars), n, m);
        if (m < n) {
            r.steps.add("Уравнений меньше, чем переменных, поэтому решений бесконечно много. Ниже примеры.");
            if (sols.isEmpty()) {
                r.answers.add("Действительных решений не найдено (численный поиск)");
            } else {
                r.answers.add("Примеры решений:");
                for (int i = 0; i < Math.min(5, sols.size()); i++) r.answers.add(point(sols.get(i), vars));
            }
        } else {
            if (sols.isEmpty()) {
                r.answers.add("Действительных решений не найдено. Поиск численный, редкие корни он мог пропустить.");
            } else {
                String cap = sols.size() >= MAX_SOLUTIONS ? " (показаны первые " + MAX_SOLUTIONS + ")" : "";
                r.answers.add("Найдено решений: " + sols.size() + cap + ", численно");
                for (int i = 0; i < sols.size(); i++) {
                    r.answers.add("Решение " + (i + 1) + ": " + point(sols.get(i), vars));
                }
            }
        }
        if (n == 2) for (double[] s : sols) r.points.add(s);
        addNotebook(r);
    }

    private static void addNotebook(Solver.Result r) {
        for (String a : r.answers) r.notebook.add(a);
    }

    /** Одно уравнение-многочлен: выносим общий одночлен, остаток — численно. */
    private static void solvePolynomial(MP p, List<String> vars, Solver.Result r) {
        r.steps.add("Раскрываем скобки и приводим подобные: " + p.format() + " = 0");
        r.notebook.add(p.format() + " = 0");
        if (p.isZero()) {
            r.answers.add("Ответ: подходит любой набор значений");
            return;
        }
        int n = vars.size();
        int[] k = new int[n];
        Arrays.fill(k, Integer.MAX_VALUE);
        for (List<Integer> key : p.t.keySet()) {
            for (int j = 0; j < n; j++) k[j] = Math.min(k[j], key.get(j));
        }
        boolean hasMono = false;
        for (int j = 0; j < n; j++) if (k[j] > 0) hasMono = true;
        MP rest = p.divide(k);

        List<String> conds = new ArrayList<>();
        if (hasMono) {
            String mono = monoText(k, vars);
            r.steps.add("Выносим общий множитель " + mono + ": " + p.format() + " = " + mono
                    + " · (" + rest.format() + ") = 0");
            r.steps.add("Произведение равно нулю, когда один из множителей равен нулю.");
            for (int j = 0; j < n; j++) if (k[j] > 0) conds.add(vars.get(j) + " = 0");
        }

        if (rest.isConstant()) {
            if (!hasMono) {
                r.answers.add("Решений нет: " + p.format() + " = 0 неверно при любых значениях");
                return;
            }
            r.answers.add("Ответ: " + joinOr(conds) + " (остальные переменные — любые)");
            return;
        }

        String[] iso = isolate(rest, vars);
        if (iso != null) {
            r.steps.add("Выражаем одну переменную через остальные: " + iso[0]);
            if (hasMono) r.answers.add("Ответ: " + joinOr(conds) + " (остальные переменные — любые)");
            r.answers.add("Ответ: " + iso[0] + (iso[1].isEmpty() ? "" : " (при " + iso[1] + ")"));
            return;
        }

        r.steps.add("Остаётся " + rest.format() + " = 0: это кривая (поверхность), её точки ищу численно.");
        if (hasMono) r.answers.add("Ответ: " + joinOr(conds) + " (остальные переменные — любые)");
        List<double[]> sols = search(polyResidual(rest), vars.size(), 1);
        if (sols.isEmpty()) {
            r.answers.add("На этой кривой действительных точек не найдено (численный поиск)");
        } else {
            r.answers.add("Примеры точек на кривой " + rest.format() + " = 0:");
            for (int i = 0; i < Math.min(5, sols.size()); i++) r.answers.add(point(sols.get(i), vars));
        }
    }

    /**
     * Если многочлен линеен по одной переменной v с одночленным коэффициентом (например xy − 3 = 0),
     * выражает v точно: {«y = 3/x», «x ≠ 0»}. Иначе null.
     */
    static String[] isolate(MP rest, List<String> vars) {
        int n = vars.size();
        for (int j = 0; j < n; j++) {
            boolean linear = true;
            boolean hasV = false;
            for (List<Integer> key : rest.t.keySet()) {
                int e = key.get(j);
                if (e > 1) linear = false;
                if (e == 1) hasV = true;
            }
            if (!linear || !hasV) continue;

            List<Integer> eB = null;       // показатели одночлена при v (v убран)
            Rational cB = null;
            boolean multi = false;
            Map<List<Integer>, Rational> a = new LinkedHashMap<>();
            for (Map.Entry<List<Integer>, Rational> e : rest.t.entrySet()) {
                List<Integer> z = new ArrayList<>(e.getKey());
                z.set(j, 0);
                if (e.getKey().get(j) == 1) {
                    if (eB != null) {
                        multi = true;
                        break;
                    }
                    eB = z;
                    cB = e.getValue();
                } else {
                    a.put(z, e.getValue());
                }
            }
            if (multi || eB == null) continue;

            // v = −A / (cB · x^eB): приводим к общему знаменателю D = x^dv
            int[] dv = new int[n];
            for (List<Integer> z : a.keySet()) {
                for (int i = 0; i < n; i++) dv[i] = Math.max(dv[i], eB.get(i) - z.get(i));
            }
            dv[j] = 0;
            Rational scale = Rational.ONE.div(cB).neg();
            MP num = new MP(vars);
            for (Map.Entry<List<Integer>, Rational> e : a.entrySet()) {
                List<Integer> key = new ArrayList<>();
                for (int i = 0; i < n; i++) key.add(e.getKey().get(i) + dv[i] - eB.get(i));
                key.set(j, 0);
                num.addTerm(key, e.getValue().mul(scale));
            }
            String name = vars.get(j);
            if (num.isZero()) return new String[] {name + " = 0", ""};

            boolean hasDen = false;
            for (int d : dv) if (d > 0) hasDen = true;
            String numText = num.format();
            boolean wrap = num.t.size() > 1 || numText.contains("/");
            if (!hasDen) return new String[] {name + " = " + numText, ""};
            String den = monoText(dv, vars);
            String formula = name + " = " + (wrap ? "(" + numText + ")" : numText) + "/" + den;
            return new String[] {formula, den + " ≠ 0"};
        }
        return null;
    }

    private static String joinOr(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(" или ");
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static Residual nodeResidual(List<Node> fs, List<String> vars) {
        return new Residual() {
            @Override
            public double[] at(double[] x) {
                Map<String, Double> env = new HashMap<>();
                for (int j = 0; j < vars.size(); j++) env.put(vars.get(j), x[j]);
                double[] out = new double[fs.size()];
                try {
                    for (int i = 0; i < fs.size(); i++) out[i] = fs.get(i).eval(env);
                } catch (RuntimeException e) {
                    return null;
                }
                for (double v : out) if (Double.isNaN(v) || Double.isInfinite(v)) return null;
                return out;
            }
        };
    }

    private static Residual polyResidual(MP p) {
        return new Residual() {
            @Override
            public double[] at(double[] x) {
                double v = p.eval(x);
                if (Double.isNaN(v) || Double.isInfinite(v)) return null;
                return new double[] {v};
            }
        };
    }

    // ---------- численный поиск ----------

    /** Ищет решения F(x)=0 из сетки стартовых точек; возвращает различные найденные решения. */
    static List<double[]> search(Residual f, int n, int m) {
        List<double[]> found = new ArrayList<>();
        int total = 1;
        for (int j = 0; j < n; j++) total *= GRID.length;
        for (int idx = 0; idx < total && found.size() < MAX_SOLUTIONS; idx++) {
            double[] x = new double[n];
            int q = idx;
            for (int j = 0; j < n; j++) {
                x[j] = GRID[q % GRID.length];
                q /= GRID.length;
            }
            double[] sol = newton(f, x, m);
            if (sol != null && !known(found, sol)) found.add(sol);
        }
        return found;
    }

    private static boolean known(List<double[]> found, double[] s) {
        for (double[] t : found) {
            boolean same = true;
            for (int j = 0; j < s.length; j++) {
                if (Math.abs(t[j] - s[j]) > 1e-4) {
                    same = false;
                    break;
                }
            }
            if (same) return true;
        }
        return false;
    }

    /** Метод Левенберга–Марквардта. Возвращает точку, где невязка ≈ 0, иначе null. */
    private static double[] newton(Residual f, double[] x0, int m) {
        int n = x0.length;
        double[] x = x0.clone();
        double[] fx = f.at(x);
        if (fx == null) return null;
        double lambda = 1e-3;
        for (int it = 0; it < 200; it++) {
            if (maxAbs(fx) < TOL) break;
            double[][] jac = jacobian(f, x, m);
            if (jac == null) return null;
            double[] d = lmStep(jac, fx, lambda, n, m);
            if (d == null) {
                lambda *= 10;
                if (lambda > 1e12) return null;
                continue;
            }
            double[] xn = new double[n];
            for (int j = 0; j < n; j++) xn[j] = x[j] + d[j];
            double[] fn = f.at(xn);
            if (fn != null && maxAbs(fn) < maxAbs(fx)) {
                x = xn;
                fx = fn;
                lambda = Math.max(lambda / 3, 1e-12);
            } else {
                lambda *= 4;
                if (lambda > 1e12) return null;
            }
        }
        if (maxAbs(fx) >= TOL) return null;
        for (double v : x) if (Math.abs(v) > 1e6 || Double.isNaN(v)) return null;
        return x;
    }

    private static double maxAbs(double[] v) {
        double s = 0;
        for (double d : v) s = Math.max(s, Math.abs(d));
        return s;
    }

    /** Якобиан центральными разностями. */
    private static double[][] jacobian(Residual f, double[] x, int m) {
        int n = x.length;
        double[][] jac = new double[m][n];
        for (int j = 0; j < n; j++) {
            double h = 1e-6 * (1 + Math.abs(x[j]));
            double[] xp = x.clone();
            double[] xm = x.clone();
            xp[j] += h;
            xm[j] -= h;
            double[] fp = f.at(xp);
            double[] fm = f.at(xm);
            if (fp == null || fm == null) return null;
            for (int i = 0; i < m; i++) jac[i][j] = (fp[i] - fm[i]) / (2 * h);
        }
        return jac;
    }

    private static double[] lmStep(double[][] jac, double[] fx, double lambda, int n, int m) {
        if (m >= n) {
            double[][] a = new double[n][n];
            double[] g = new double[n];
            for (int p = 0; p < n; p++) {
                for (int q = 0; q < n; q++) {
                    double s = 0;
                    for (int i = 0; i < m; i++) s += jac[i][p] * jac[i][q];
                    a[p][q] = s;
                }
                a[p][p] += lambda;
                double s = 0;
                for (int i = 0; i < m; i++) s += jac[i][p] * fx[i];
                g[p] = -s;
            }
            return solveLinear(a, g);
        }
        double[][] a = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int k = 0; k < m; k++) {
                double s = 0;
                for (int j = 0; j < n; j++) s += jac[i][j] * jac[k][j];
                a[i][k] = s;
            }
            a[i][i] += lambda;
        }
        double[] negF = new double[m];
        for (int i = 0; i < m; i++) negF[i] = -fx[i];
        double[] w = solveLinear(a, negF);
        if (w == null) return null;
        double[] d = new double[n];
        for (int j = 0; j < n; j++) {
            double s = 0;
            for (int i = 0; i < m; i++) s += jac[i][j] * w[i];
            d[j] = s;
        }
        return d;
    }

    /** Гаусс с выбором главного элемента; null, если система вырождена. */
    private static double[] solveLinear(double[][] a0, double[] b0) {
        int n = b0.length;
        double[][] a = new double[n][];
        for (int i = 0; i < n; i++) a[i] = a0[i].clone();
        double[] b = b0.clone();
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int i = c + 1; i < n; i++) if (Math.abs(a[i][c]) > Math.abs(a[piv][c])) piv = i;
            if (Math.abs(a[piv][c]) < 1e-300) return null;
            double[] tr = a[c]; a[c] = a[piv]; a[piv] = tr;
            double tb = b[c]; b[c] = b[piv]; b[piv] = tb;
            for (int i = c + 1; i < n; i++) {
                double f = a[i][c] / a[c][c];
                for (int j = c; j < n; j++) a[i][j] -= f * a[c][j];
                b[i] -= f * b[c];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = b[i];
            for (int j = i + 1; j < n; j++) s -= a[i][j] * x[j];
            x[i] = s / a[i][i];
        }
        return x;
    }

    // ---------- вывод ----------

    static String point(double[] x, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < x.length; j++) {
            if (j > 0) sb.append(", ");
            sb.append(vars.get(j)).append(" = ").append(num(x[j]));
        }
        return sb.toString();
    }

    private static String num(double v) {
        double r = Math.round(v * 1e6) / 1e6;
        if (Math.abs(r) < 5e-7) r = 0.0;
        return Fmt.num(r);
    }

    static String monoText(int[] k, List<String> vars) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < k.length; j++) {
            if (k[j] == 0) continue;
            sb.append(vars.get(j));
            if (k[j] > 1) sb.append(Fmt.sup(k[j]));
        }
        return sb.length() == 0 ? "1" : sb.toString();
    }

    /** Многочлен от нескольких переменных с рациональными коэффициентами. */
    static final class MP {
        final List<String> vars;
        final Map<List<Integer>, Rational> t = new LinkedHashMap<>();

        private MP(List<String> vars) {
            this.vars = vars;
        }

        private List<Integer> zeros() {
            List<Integer> z = new ArrayList<>();
            for (int j = 0; j < vars.size(); j++) z.add(0);
            return z;
        }

        static MP constant(Rational c, List<String> vars) {
            MP p = new MP(vars);
            if (!c.isZero()) p.t.put(p.zeros(), c);
            return p;
        }

        static MP variable(String name, List<String> vars) {
            MP p = new MP(vars);
            List<Integer> key = p.zeros();
            key.set(vars.indexOf(name), 1);
            p.t.put(key, Rational.ONE);
            return p;
        }

        private void addTerm(List<Integer> key, Rational c) {
            Rational s = t.containsKey(key) ? t.get(key).add(c) : c;
            if (s.isZero()) t.remove(key);
            else t.put(key, s);
        }

        MP add(MP o) {
            MP r = copy();
            for (Map.Entry<List<Integer>, Rational> e : o.t.entrySet()) r.addTerm(e.getKey(), e.getValue());
            return r;
        }

        MP sub(MP o) {
            return add(o.neg());
        }

        MP neg() {
            MP r = new MP(vars);
            for (Map.Entry<List<Integer>, Rational> e : t.entrySet()) r.t.put(e.getKey(), e.getValue().neg());
            return r;
        }

        MP mul(MP o) {
            MP r = new MP(vars);
            for (Map.Entry<List<Integer>, Rational> a : t.entrySet()) {
                for (Map.Entry<List<Integer>, Rational> b : o.t.entrySet()) {
                    List<Integer> key = new ArrayList<>();
                    for (int j = 0; j < vars.size(); j++) key.add(a.getKey().get(j) + b.getKey().get(j));
                    r.addTerm(key, a.getValue().mul(b.getValue()));
                }
            }
            return r;
        }

        MP pow(int e) {
            MP r = constant(Rational.ONE, vars);
            for (int i = 0; i < e; i++) r = r.mul(this);
            return r;
        }

        MP scale(Rational c) {
            MP r = new MP(vars);
            for (Map.Entry<List<Integer>, Rational> e : t.entrySet()) r.addTerm(e.getKey(), e.getValue().mul(c));
            return r;
        }

        /** Делит каждый член на одночлен k (k не больше степеней членов). */
        MP divide(int[] k) {
            MP r = new MP(vars);
            for (Map.Entry<List<Integer>, Rational> e : t.entrySet()) {
                List<Integer> key = new ArrayList<>();
                for (int j = 0; j < vars.size(); j++) key.add(e.getKey().get(j) - k[j]);
                r.t.put(key, e.getValue());
            }
            return r;
        }

        private MP copy() {
            MP r = new MP(vars);
            r.t.putAll(t);
            return r;
        }

        boolean isZero() {
            return t.isEmpty();
        }

        boolean isConstant() {
            for (List<Integer> key : t.keySet()) {
                for (int v : key) if (v != 0) return false;
            }
            return true;
        }

        double eval(double[] x) {
            double s = 0;
            for (Map.Entry<List<Integer>, Rational> e : t.entrySet()) {
                double term = e.getValue().toDouble();
                for (int j = 0; j < x.length; j++) term *= Math.pow(x[j], e.getKey().get(j));
                s += term;
            }
            return s;
        }

        /** Переводит выражение в многочлен; null, если там есть не-полиномиальные части. */
        static MP from(Node n, List<String> vars) {
            switch (n.kind) {
                case NUM:
                    return constant(n.num, vars);
                case VAR:
                    return vars.contains(n.name) ? variable(n.name, vars) : null;
                case NEG: {
                    MP a = from(n.a, vars);
                    return a == null ? null : a.neg();
                }
                case BIN: {
                    MP a = from(n.a, vars);
                    MP b = from(n.b, vars);
                    if (a == null || b == null) return null;
                    switch (n.name) {
                        case "+": return a.add(b);
                        case "-": return a.sub(b);
                        case "*": return a.mul(b);
                        case "/":
                            if (!b.isConstant() || b.isZero()) return null;
                            return a.scale(Rational.ONE.div(b.t.isEmpty() ? Rational.ZERO : b.t.values().iterator().next()));
                        case "^": {
                            if (!b.isConstant() || b.isZero()) return b.isZero() ? constant(Rational.ONE, vars) : null;
                            Rational e = b.t.values().iterator().next();
                            if (!e.isInteger() || e.signum() < 0 || e.numerator().intValue() > 12) return null;
                            return a.pow(e.numerator().intValue());
                        }
                        default:
                            return null;
                    }
                }
                default:
                    return null;
            }
        }

        /** Текст многочлена, члены по убыванию степени. */
        String format() {
            if (t.isEmpty()) return "0";
            List<List<Integer>> keys = new ArrayList<>(t.keySet());
            Collections.sort(keys, new java.util.Comparator<List<Integer>>() {
                @Override
                public int compare(List<Integer> a, List<Integer> b) {
                    int da = 0, db = 0;
                    for (int v : a) da += v;
                    for (int v : b) db += v;
                    if (da != db) return db - da;
                    for (int j = 0; j < a.size(); j++) {
                        if (!a.get(j).equals(b.get(j))) return b.get(j) - a.get(j);
                    }
                    return 0;
                }
            });
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (List<Integer> key : keys) {
                Rational c = t.get(key);
                boolean negative = c.signum() < 0;
                Rational mag = negative ? c.neg() : c;
                boolean constant = true;
                for (int v : key) if (v != 0) constant = false;
                int[] exps = new int[key.size()];
                for (int j = 0; j < exps.length; j++) exps[j] = key.get(j);
                String mono = constant ? "" : monoText(exps, vars);
                String body;
                if (constant) body = Fmt.rat(mag);
                else if (mag.equals(Rational.ONE)) body = mono;
                else body = Fmt.rat(mag) + mono;
                if (first) {
                    sb.append(negative ? "−" : "").append(body);
                    first = false;
                } else {
                    sb.append(negative ? " − " : " + ").append(body);
                }
            }
            return sb.toString();
        }
    }
}
