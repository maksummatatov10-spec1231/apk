package com.example.calcpro.math;

/** Самопроверка математического ядра (запускается на JVM, без Android): scripts/test-math.sh */
public final class SolverCheck {
    private static int failed = 0;
    private static int passed = 0;

    private static void expectAnswer(String input, String mustContain) {
        try {
            Solver.Result r = Solver.solve(input);
            boolean ok = false;
            for (String a : r.answers) if (a.contains(mustContain)) ok = true;
            report(ok, input, "ответ содержит «" + mustContain + "»", r.answers.toString());
        } catch (RuntimeException e) {
            report(false, input, "ответ содержит «" + mustContain + "»", "исключение: " + e.getMessage());
        }
    }

    private static void expectValue(String input, String expectedAnswer) {
        try {
            Solver.Result r = Solver.solve(input);
            report(r.answers.size() == 1 && r.answers.get(0).equals(expectedAnswer), input,
                    expectedAnswer, r.answers.toString());
        } catch (RuntimeException e) {
            report(false, input, expectedAnswer, "исключение: " + e.getMessage());
        }
    }

    private static void expectError(String input) {
        try {
            Solver.solve(input);
            report(false, input, "ошибка", "решено без ошибки");
        } catch (MathError e) {
            report(true, input, "ошибка", e.getMessage());
        } catch (RuntimeException e) {
            report(false, input, "ошибка MathError", "другое исключение: " + e);
        }
    }

    private static void report(boolean ok, String input, String expected, String got) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "  OK   " : "  FAIL ") + input + "   →   " + got + (ok ? "" : "   (ожидалось: " + expected + ")"));
    }

    public static void main(String[] args) {
        System.out.println("Выражения:");
        expectValue("2 + 3 · 4", "Ответ: 14");
        expectValue("(2 + 3) · 4 ÷ 5", "Ответ: 4");
        expectAnswer("1/3 + 1/6", "Ответ: 1/2");
        expectValue("2^10", "Ответ: 1024");
        expectValue("-2^2", "Ответ: −4");
        expectValue("5!", "Ответ: 120");
        expectValue("2,5 × 4", "Ответ: 10");
        expectAnswer("sin(pi/2)", "Ответ: 1");
        expectAnswer("sqrt(16) + 1", "Ответ: 5");
        expectAnswer("√(2)", "Ответ: √2 ≈ 1,414214");
        expectAnswer("корень из 50", "Ответ: 5√2 ≈ 7,071068");
        expectAnswer("√50", "≈ 7,07 (до сотых)");
        expectAnswer("квадратный корень из 72", "Ответ: 6√2");
        expectAnswer("корень из 12", "Ответ: 2√3");
        expectValue("2π", "Ответ: " + Fmt.num(2 * Math.PI));
        expectError("1 ÷ 0");
        expectError("sqrt(-4)");
        expectError("2 +");

        System.out.println("Линейные уравнения:");
        expectAnswer("2x + 6 = 0", "x = −3");
        expectAnswer("3x − 4 = 2x + 5", "x = 9");
        expectAnswer("x/2 = 3/4", "x = 3/2");

        System.out.println("Квадратные уравнения:");
        expectAnswer("x^2 - 5x + 6 = 0", "x₁ = 2");
        expectAnswer("x^2 - 5x + 6 = 0", "x₂ = 3");
        expectAnswer("2x² − 3x + 1 = 0", "x₁ = 1/2");
        expectAnswer("x² + 1 = 0", "x₁,₂ = ±1i");
        expectAnswer("x² + 2x + 1 = 0", "x = −1");
        expectAnswer("x^2 = 2", "x₁ ≈ −1,41421");
        expectAnswer("x^2 = 2", "x₂ ≈ 1,41421");

        System.out.println("Многочлены третьей и четвёртой степени:");
        expectAnswer("x³ − 6x² + 11x − 6 = 0", "x₁ = 1");
        expectAnswer("x³ − 6x² + 11x − 6 = 0", "x₂ = 2");
        expectAnswer("x³ − 6x² + 11x − 6 = 0", "x₃ = 3");
        expectAnswer("x^4 - 10x^2 + 9 = 0", "x₁ = −3");
        expectAnswer("x^3 - 2 = 0", "x₁ ≈ 1,25992");
        expectAnswer("x^3 - 2 = 0", "± 1,09112i");

        System.out.println("Тождества и нелинейные уравнения:");
        expectAnswer("x + 1 = x + 1", "любое число");
        expectAnswer("x = x + 1", "Решений нет");
        expectAnswer("2 + 2 = 4", "верно");
        expectAnswer("sin(x) = 0,5", "x₁ ≈ −9,94838");
        expectAnswer("sin(x) = 0,5", "x₂ ≈ −5,75959");
        expectAnswer("x^2 = sin(x)", "x₂ ≈ 0,876726");

        System.out.println("Системы линейных уравнений (Крамер):");
        expectAnswer("x + y = 5; 2x − y = 1", "x = 2");
        expectAnswer("x + y = 5; 2x − y = 1", "y = 3");
        expectAnswer("x + y + z = 6\n2x − y + z = 3\nx + 2y − z = 2", "x = 1");
        expectAnswer("x + y + z = 6\n2x − y + z = 3\nx + 2y − z = 2", "y = 2");
        expectAnswer("x + y + z = 6\n2x − y + z = 3\nx + 2y − z = 2", "z = 3");
        expectAnswer("x + y = 1; 2x + 2y = 2", "D = 0");

        System.out.println("Ошибки ввода:");
        expectError("x = 1 = 2");
        expectError("2 + ");
        expectError("x*y = 3");
        expectError("3 @ 4");

        System.out.println();
        System.out.println("Пройдено: " + passed + ", провалено: " + failed);
        if (failed > 0) System.exit(1);
    }
}
