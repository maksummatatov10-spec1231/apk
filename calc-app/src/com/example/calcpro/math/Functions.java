package com.example.calcpro.math;

/** Встроенные функции одного аргумента. */
public final class Functions {
    private Functions() {}

    public static boolean isFunction(String name) {
        switch (name) {
            case "sin": case "cos": case "tan": case "asin": case "acos": case "atan":
            case "ln": case "log": case "lg": case "exp": case "sqrt": case "cbrt": case "abs":
                return true;
            default:
                return false;
        }
    }

    public static double apply(String name, double x) {
        switch (name) {
            case "sin": return Math.sin(x);
            case "cos": return Math.cos(x);
            case "tan":
                if (Math.abs(Math.cos(x)) < 1e-15) throw new MathError("tan не определён в этой точке");
                return Math.tan(x);
            case "asin":
                if (Math.abs(x) > 1) throw new MathError("asin определён на [−1; 1]");
                return Math.asin(x);
            case "acos":
                if (Math.abs(x) > 1) throw new MathError("acos определён на [−1; 1]");
                return Math.acos(x);
            case "atan": return Math.atan(x);
            case "ln":
                if (x <= 0) throw new MathError("ln определён для положительных чисел");
                return Math.log(x);
            case "log":
            case "lg":
                if (x <= 0) throw new MathError("log определён для положительных чисел");
                return Math.log10(x);
            case "exp": return Math.exp(x);
            case "sqrt":
                if (x < 0) throw new MathError("Корень из отрицательного числа в действительных числах не существует");
                return Math.sqrt(x);
            case "cbrt": return Math.cbrt(x);
            case "abs": return Math.abs(x);
            case "!": {
                if (x < 0 || x != Math.floor(x) || x > 170) throw new MathError("Факториал — для целых неотрицательных чисел");
                double f = 1;
                for (int i = 2; i <= (int) x; i++) f *= i;
                return f;
            }
            default: throw new MathError("Неизвестная функция " + name);
        }
    }
}
