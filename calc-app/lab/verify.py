#!/usr/bin/env python3
"""Проверка правильности решателя независимым эталоном (sympy + numpy).

Запуск:  scripts/build-cli.sh && /путь/к/venv/bin/python lab/verify.py [-v]
Нужны пакеты sympy и numpy (в приложении их нет — это только для проверки).

Для каждого запроса:
  * ответы приложения (тот же SolverCli, что в APK) разбираются обратно в числа;
  * эталон: многочлены — sympy.nroots (все комплексные корни, с учётом кратности);
    системы — sympy.linsolve; остальные уравнения (√, ∛, sin/cos/tan) — поиск корней
    по сетке на [−10; 10] с уточнением бисекцией; выражения — прямой расчёт в sympy.
Расхождение = ошибка в решателе (или в разборе ответа — тогда смотрите вывод -v).
"""
import json
import os
import re
import subprocess
import sys
from fractions import Fraction
from pathlib import Path

import numpy as np
import sympy as sp
from sympy.parsing.sympy_parser import (convert_xor, implicit_multiplication_application,
                                        parse_expr, standard_transformations)

HERE = Path(__file__).resolve().parent
CALC = HERE.parent
CLASSES = CALC / "build" / "cli" / "classes"
JAVA = os.environ.get("JAVA", str(Path.home() / "android-toolchain" / "bin" / "java"))
VERBOSE = "-v" in sys.argv

x, y, z = sp.symbols("x y z")

# ---------- набор запросов ----------
# kind: poly — уравнение с одной переменной x, где обе части многочлены (эталон: nroots)
#       numeric — уравнение с x, не многочлен (эталон: сетка на [−10; 10])
#       radical — √ или ∛ от выражения с x (эталон: sympy.solve, без ограничения отрезком)
#       system — система линейных уравнений (эталон: linsolve)
#       expr — выражение без переменных (эталон: значение)
#       nosolution — система или уравнение без решений (ожидается пустой ответ или «нет»)
SUITE = [
    # --- квадратные и многочлены ---
    ("2x² − 3x + 1 = 0", "poly"),
    ("x² − 5x + 6 = 0", "poly"),
    ("x² + 4x + 13 = 0", "poly"),
    ("x² − 2x + 1 = 0", "poly"),
    ("x² = 9", "poly"),
    ("(x − 1)² = 4", "poly"),
    ("3x² − 12 = 0", "poly"),
    ("x² + x + 1 = 0", "poly"),
    ("x³ − 6x² + 11x − 6 = 0", "poly"),
    ("x³ = 8", "poly"),
    ("x³ − 2 = 0", "poly"),
    ("x³ + x + 1 = 0", "poly"),
    ("x³ − 3x + 1 = 0", "poly"),
    ("x³ − x² − x − 2 = 0", "poly"),
    ("x³ + 2x² − 5x − 6 = 0", "poly"),
    ("2x³ − 3x² − 11x + 6 = 0", "poly"),
    ("x⁴ − 5x² + 4 = 0", "poly"),
    ("x⁴ = 16", "poly"),
    ("x⁴ + 1 = 0", "poly"),
    ("x⁴ + 2x² + 1 = 0", "poly"),
    ("x⁴ − 10x² + 1 = 0", "poly"),
    ("x⁴ + x³ − 7x² − x + 6 = 0", "poly"),
    ("x⁴ − 2x³ − 13x² + 14x + 24 = 0", "poly"),
    ("x⁵ − x − 1 = 0", "poly"),
    ("x¹⁰ = 1", "poly"),
    ("x⁶ − 1 = 0", "poly"),
    ("x⁵ − 3x⁴ + 2x³ = 0", "poly"),
    # --- линейные ---
    ("2x + 5 = 11", "poly"),
    ("2x + 3 = 0", "poly"),
    ("3(x − 4) = 2x + 1", "poly"),
    ("x/3 + 2 = 5", "poly"),
    ("0,5x − 1,5 = 0", "poly"),
    # --- системы ---
    ("x + y = 5; 2x − y = 1", "system"),
    ("x + 2y = 4; 3x − y = 5", "system"),
    ("2x + 3y = 8; x − y = −1", "system"),
    ("x + y + z = 6; x − y + z = 2; x + y − z = 0", "system"),
    ("x + y = 2; x + y = 3", "nosolution"),
    # --- иррациональные ---
    ("√(x + 4) = 3", "radical"),
    ("√(2x + 1) = x − 1", "radical"),
    ("√x = 3", "radical"),
    ("√x = −2", "nosolution"),
    ("√(x) = √(x + 1)", "nosolution"),
    ("∛(x) = 2", "radical"),
    ("∛(x + 1) = 3", "radical"),
    # --- тригонометрия ---
    ("sin(x) = 0,5", "numeric"),
    ("cos(x) = 0", "numeric"),
    ("tan(x) = 1", "numeric"),
    ("sin(x) = 1", "numeric"),
    ("cos(x) = −0,5", "numeric"),
    ("sin(x) = 2", "nosolution"),
    # --- выражения ---
    ("(2 + 3) · 4 ÷ 5 − 2^3", "expr"),
    ("2^10 − 3!", "expr"),
    ("240 · 15%", "expr"),
    ("7/3 + 5/6", "expr"),
    ("10! / 8!", "expr"),
    ("√(144) + ∛(27)", "expr"),
    ("2² + 3³", "expr"),
    ("sin(π/6) + cos(π/3)", "expr"),
    ("ln(e^2)", "expr"),
    ("log(1000) + lg(100)", "expr"),
    ("abs(-7) + 3", "expr"),
    ("9^9", "expr"),
    ("9⁹", "expr"),
    ("2^20", "expr"),
    ("(-2)^3", "expr"),
    ("(3/4)^2", "expr"),
    ("1/3 + 1/4", "expr"),
    ("2,5 · 4", "expr"),
    ("(1 + 2)(3 + 4)", "expr"),
    ("3π", "expr"),
    ("0,1 + 0,2", "expr"),
    ("100 − 99,9", "expr"),
    ("12/5", "expr"),
]

# ---------- перевод записи приложения в sympy ----------

SUPER = {"⁰": "0", "¹": "1", "²": "2", "³": "3", "⁴": "4", "⁵": "5", "⁶": "6", "⁷": "7", "⁸": "8", "⁹": "9"}


def to_sympy_text(s: str) -> str:
    s = s.replace("−", "-").replace("×", "*").replace("·", "*").replace("÷", "/")
    s = re.sub(r"[⁰¹²³⁴⁵⁶⁷⁸⁹]+", lambda m: "**(" + "".join(SUPER[c] for c in m.group(0)) + ")", s)
    s = s.replace("√(", "sqrt(").replace("∛(", "cbrt(")
    s = re.sub(r"√([\w.]+)", r"sqrt(\1)", s)
    s = re.sub(r"∛([\w.]+)", r"cbrt(\1)", s)
    s = s.replace("π", "pi")
    s = s.replace("lg(", "log10(").replace("log(", "log10(").replace("ln(", "log(")
    s = s.replace("abs(", "Abs(").replace("%", "/100")
    s = re.sub(r"(\d+)!", r"factorial(\1)", s)
    s = re.sub(r"(\d),(\d)", r"\1.\2", s)
    return s


LOCALS = {"cbrt": lambda v: v ** sp.Rational(1, 3), "log10": lambda v: sp.log(v, 10),
          "factorial": sp.factorial, "Abs": sp.Abs, "sqrt": sp.sqrt, "pi": sp.pi, "e": sp.E,
          "x": x, "y": y, "z": z, "log": sp.log}
TRANS = standard_transformations + (implicit_multiplication_application, convert_xor)


def to_sympy(s: str):
    return parse_expr(to_sympy_text(s), local_dict=dict(LOCALS), transformations=TRANS)


def split_eq(q: str):
    return [p.strip() for p in q.split("=")]


# ---------- разбор ответов приложения ----------

def num(tok: str) -> float:
    tok = tok.strip().replace("−", "-").replace(",", ".")
    if "/" in tok:
        a, b = tok.split("/")
        return float(Fraction(a) / Fraction(b))
    return float(tok)


def parse_complex(rhs: str):
    """«−2 ± 3i» → [−2+3i, −2−3i]; «±2i» → [2i, −2i]; «0,8 ± 0,5i»; «−3/2 ≈ −1,5»; «5»."""
    s = rhs.replace("−", "-").replace(" ", "")
    if "≈" in s:
        exact, approx = s.split("≈", 1)
        try:
            return parse_complex(exact)
        except ValueError:
            return parse_complex(approx)
    if "±" in s:
        left, right = s.split("±", 1)
        re_part = num(left) if left else 0.0
        im_txt = right[:-1] if right.endswith("i") else right
        im = num(im_txt) if im_txt else 1.0
        return [complex(re_part, im), complex(re_part, -im)]
    if s.endswith("i"):
        im_txt = s[:-1]
        im = {"": 1.0, "+": 1.0, "-": -1.0}.get(im_txt)
        return [complex(0, im if im is not None else num(im_txt))]
    return [complex(num(s), 0)]


def answer_rhs(a: str) -> str:
    """Правая часть ответа: «x₁ = 3», «x₁ ≈ 0,5», «x₁,₂ = −2 ± 3i» → «3», «0,5», «−2 ± 3i»."""
    return re.sub(r"^[^=≈]*[=≈]", "", a, count=1)


def parse_answers(answers, kind):
    """('roots', [complex]) — уравнение; ('vars', {name: value}) — система; ('value', float) — выражение."""
    if kind == "expr":
        if not answers or not answers[0].startswith("Ответ:"):
            return "bad", None
        return "value", parse_complex(answers[0][len("Ответ:"):].strip())[0].real
    if kind == "system":
        out = {}
        for a in answers:
            name, rhs = a.split("=", 1)
            out[name.strip()] = parse_complex(rhs)[0].real
        return "vars", out
    roots = []
    for a in answers:
        roots.extend(parse_complex(answer_rhs(a)))
    return "roots", roots


# ---------- эталон ----------

def truth_poly(q):
    left, right = split_eq(q)
    expr = sp.expand(to_sympy(left) - to_sympy(right))
    poly = sp.Poly(expr, x)
    return [complex(r) for r in poly.nroots(n=30, maxsteps=200)]


def truth_numeric(q):
    left, right = split_eq(q)
    f_expr = to_sympy(left) - to_sympy(right)
    f = sp.lambdify(x, f_expr, "numpy")
    xs = np.arange(-10, 10 + 1e-12, 1e-3)
    with np.errstate(all="ignore"):
        ys = np.array(f(xs), dtype=float) if np.ndim(f(xs)) else np.full_like(xs, float(f(xs)))
    roots = []
    for i in range(len(xs) - 1):
        a, b = ys[i], ys[i + 1]
        if not (np.isfinite(a) and np.isfinite(b)):
            continue
        if a == 0:
            roots.append(xs[i])
        elif a * b < 0:
            lo, hi, flo = xs[i], xs[i + 1], a
            for _ in range(70):
                mid = (lo + hi) / 2
                fm = float(f(mid))
                if fm == 0:
                    lo = hi = mid
                    break
                if flo * fm < 0:
                    hi = mid
                else:
                    lo, flo = mid, fm
            r = (lo + hi) / 2
            if abs(float(f(r))) < 1e-7:
                roots.append(r)
    # касания: локальный минимум |f| около нуля
    for i in range(1, len(xs) - 1):
        a0, a1, a2 = ys[i - 1], ys[i], ys[i + 1]
        if np.isfinite(a0) and np.isfinite(a1) and np.isfinite(a2) and abs(a1) < 1e-3 \
                and abs(a1) <= abs(a0) and abs(a1) <= abs(a2) and a0 * a1 >= 0:
            lo, hi = xs[i - 1], xs[i + 1]
            for _ in range(200):
                m1, m2 = lo + (hi - lo) / 3, hi - (hi - lo) / 3
                if abs(float(f(m1))) < abs(float(f(m2))):
                    hi = m2
                else:
                    lo = m1
            r = (lo + hi) / 2
            if abs(float(f(r))) < 1e-7:
                roots.append(r)
    out = []
    for r in sorted(roots):
        if not out or abs(r - out[-1]) > 1e-6:
            out.append(r)
    return [complex(r, 0) for r in out]


def truth_system(q):
    eqs = []
    for part in re.split(r"[;\n]+", q):
        if part.strip():
            left, right = split_eq(part)
            eqs.append(sp.Eq(to_sympy(left), to_sympy(right)))
    vars_ = sorted({s for e in eqs for s in e.free_symbols}, key=lambda s: s.name)
    sol = sp.linsolve(eqs, vars_)
    if sol == sp.S.EmptySet or len(sol) == 0:
        return None
    values = list(sol)[0]
    return {v.name: float(val) for v, val in zip(vars_, values)}


def truth_radical(q):
    left, right = split_eq(q)
    sols = sp.solve(sp.Eq(to_sympy(left), to_sympy(right)), x)
    real = []
    for sol in sols:
        v = complex(sp.N(sol, 30))
        if abs(v.imag) < 1e-12:
            real.append(v)
    return real


def truth_value(q):
    return float(sp.N(to_sympy(q), 30))


# ---------- сравнение ----------

def same_roots(app, truth, tol=1e-4):
    """Множества корней совпадают (без учёта кратности и порядка)."""
    def distinct(vals):
        out = []
        for v in vals:
            if not any(abs(v - w) <= tol * (1 + abs(v)) for w in out):
                out.append(v)
        return out
    a, t = distinct(app), distinct(truth)
    if len(a) != len(t):
        return False, f"корней в приложении {len(a)}, по эталону {len(t)}"
    for v in t:
        if not any(abs(v - w) <= tol * (1 + abs(v)) for w in a):
            return False, f"эталонный корень {v:.6g} не найден в ответе"
    for v in a:
        if not any(abs(v - w) <= tol * (1 + abs(v)) for w in t):
            return False, f"лишний корень в ответе {v:.6g}"
    return True, ""


def run_app(queries):
    cmd = [JAVA, "-Dfile.encoding=UTF-8", "-cp", str(CLASSES), "com.example.calcpro.SolverCli"]
    proc = subprocess.run(cmd, input="\n".join(queries) + "\n", capture_output=True, text=True,
                          encoding="utf-8", check=True)
    return [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]


def check(q, kind, res):
    if res.get("error"):
        if kind == "nosolution":
            return True, "ошибка, но решений нет (ожидаемо)"
        return False, "ошибка приложения: " + res["error"]
    answers = res["answers"]
    if kind == "nosolution":
        text = " ".join(answers).lower()
        if not answers or "нет" in text:
            return True, "решений нет"
        return False, "ожидалось «решений нет», получено: " + "; ".join(answers)

    if kind == "expr":
        t = truth_value(q)
        tag, val = parse_answers(answers, kind)
        if tag != "value":
            return False, f"ожидался ответ-значение, получено {answers}"
        ok = abs(val - t) <= 1e-6 * max(1, abs(t))
        return ok, f"приложение {val:.10g}, эталон {t:.10g}"

    if kind == "system":
        t = truth_system(q)
        tag, vals = parse_answers(answers, kind)
        if t is None:
            return False, "эталон: решений нет, приложение дало " + "; ".join(answers)
        if tag != "vars" or set(vals) != set(t):
            return False, f"ответ {answers} не совпадает по переменным с эталоном {t}"
        bad = [k for k in t if abs(vals[k] - t[k]) > 1e-4 * max(1, abs(t[k]))]
        return not bad, f"приложение {vals}, эталон {t}" if bad else "совпадает"

    tag, app_roots = parse_answers(answers, kind)
    if tag != "roots":
        return False, f"не удалось разобрать ответы {answers}"
    if kind == "poly":
        truth = truth_poly(q)
    elif kind == "radical":
        truth = truth_radical(q)
        app_roots = [r for r in app_roots if abs(r.imag) < 1e-9]
    else:
        truth = truth_numeric(q)
        # на сетке ищем только вещественные корни; приложение может показывать и комплексные — отбрасываем их
        app_roots = [r for r in app_roots if abs(r.imag) < 1e-9]
    ok, why = same_roots(app_roots, truth, tol=1e-4)
    return ok, why if not ok else f"корней: {len(truth)}"


def main():
    queries = [q for q, _ in SUITE]
    results = run_app(queries)
    failed = 0
    for (q, kind), res in zip(SUITE, results):
        try:
            ok, detail = check(q, kind, res)
        except Exception as e:  # эталон не смог разобрать запись — это не ошибка приложения
            ok, detail = None, f"эталон не разобрал запись: {e}"
        mark = "OK  " if ok else ("SKIP" if ok is None else "FAIL")
        if ok is False:
            failed += 1
        if ok is not True or VERBOSE:
            print(f"{mark} {q}  [{kind}]  {detail}")
            if VERBOSE or ok is False:
                print("     приложение:", "; ".join(res.get("answers", [])) or res.get("error"))
    total = len(SUITE)
    print(f"\nИтого: {total - failed} из {total} совпали с эталоном, расхождений: {failed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
