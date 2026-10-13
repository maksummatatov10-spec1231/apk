#!/usr/bin/env python3
"""Случайная проверка решателя: генерирует уравнения и сверяет ответы приложения с sympy.

Запуск (из calc-app):  /tmp/venv/bin/python lab/stress.py [число_случаев] [seed]
Использует эталоны из lab/verify.py (тот же Java-решатель через SolverCli).
"""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify  # noqa: E402


def term(c, power=None):
    """Одночлен от x: c, cx, cx^2 (в синтаксисе приложения)."""
    if power is None:
        return f"{c}"
    if power == 1:
        return f"{c}x" if c not in (1, -1) else ("x" if c == 1 else "-x")
    return f"{c}x^{power}" if c not in (1, -1) else ("x^%d" % power if c == 1 else "-x^%d" % power)


def poly(rng, deg, lo=-6, hi=6):
    """Многочлен степени deg со старшим коэффициентом ≠ 0, в виде строки."""
    coefs = [rng.randint(lo, hi) for _ in range(deg + 1)]
    while coefs[0] == 0:
        coefs[0] = rng.randint(lo, hi)
    parts = []
    for power in range(deg, -1, -1):
        c = coefs[deg - power]
        if c == 0:
            continue
        t = term(abs(c), power if power else None) if power else str(abs(c))
        if power == 1 and abs(c) == 1:
            t = "x"
        if power > 1 and abs(c) == 1:
            t = f"x^{power}"
        sign = "−" if c < 0 else "+"
        parts.append((sign, t))
    out = ""
    for i, (sign, t) in enumerate(parts):
        if i == 0:
            out += ("−" if sign == "−" else "") + t
        else:
            out += f" {sign} {t}"
    return out


def gen(rng):
    """Возвращает (запрос, вид) для случайного уравнения."""
    kind = rng.choice(["sq_sq", "sq_lin", "sq_const", "cbrt_cbrt", "poly"])
    if kind == "sq_sq":
        a = poly(rng, 2)
        b = poly(rng, 1)
        return f"√({a}) = √({b})", "radical"
    if kind == "sq_lin":
        a = poly(rng, 2)
        d, e = rng.randint(1, 5), rng.randint(-6, 6)
        rhs = f"{d}x" + (f" + {e}" if e > 0 else (f" − {-e}" if e < 0 else ""))
        return f"√({a}) = {rhs}", "radical"
    if kind == "sq_const":
        a = poly(rng, 2)
        return f"√({a}) = {rng.randint(0, 5)}", "radical"
    if kind == "cbrt_cbrt":
        a = poly(rng, 2)
        b = poly(rng, 1)
        return f"∛({a}) = ∛({b})", "radical"
    return poly(rng, rng.choice([2, 3, 4])) + " = 0", "poly"


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 300
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    rng = random.Random(seed)
    cases = []
    while len(cases) < n:
        q, kind = gen(rng)
        if q not in [c[0] for c in cases]:
            cases.append((q, kind))

    results = verify.run_app([q for q, _ in cases])
    failed = skipped = 0
    for (q, kind), res in zip(cases, results):
        try:
            if kind == "radical" and not res.get("error") and any("нет" in a for a in res.get("answers", [])):
                # приложение: «Решений нет» — эталон обязан дать пустой список корней
                truth = verify.truth_radical(q)
                ok, detail = (not truth), f"эталон корней: {len(truth)}"
            else:
                ok, detail = verify.check(q, kind, res)
        except Exception as e:  # эталон не разобрал запись
            ok, detail = None, f"эталон не разобрал: {e}"
        if ok is None:
            skipped += 1
            continue
        if not ok:
            failed += 1
            print(f"FAIL {q}  [{kind}]  {detail}")
            print("     приложение:", "; ".join(res.get("answers", [])) or res.get("error"))
    print(f"\nСлучаев: {len(cases)}, расхождений: {failed}, пропущено эталоном: {skipped}, seed={seed}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
