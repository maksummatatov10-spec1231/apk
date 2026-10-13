package com.example.calcpro;

/** Самопроверка логики калькулятора: сценарии нажатий и ожидаемый результат на дисплее. */
public final class CalcEngineCheck {
    private static int pass, fail;

    /** Прогоняет последовательность клавиш (через пробел) и сверяет результат «=». */
    private static void check(String keys, String expected) {
        CalcEngine e = new CalcEngine();
        CalcEngine.Outcome last = null;
        for (String k : keys.split(" ")) {
            switch (k) {
                case "=": last = e.equals(); break;
                case "⌫": e.backspace(); break;
                case "CE": e.clearEntry(); break;
                case "C": e.clearAll(); break;
                case "±": e.negate(); break;
                case "1/x": e.reciprocal(); break;
                case "M+": e.memoryAdd(true); break;
                case "M−": e.memoryAdd(false); break;
                case "MR": e.memoryRecall(); break;
                case "MC": e.memoryClear(); break;
                case ",": e.press("."); break;
                default: e.press(k);
            }
        }
        String got = last == null ? "(нет =)" : last.decimal == null && last.error == null ? "пусто"
                : last.ok() ? last.decimal
                : last.error != null ? "ОШИБКА: " + last.error : "нужны уравнения";
        if (expected == null && last != null && last.error != null) got = "ОШИБКА";
        boolean ok = expected == null ? got.startsWith("ОШИБКА") : expected.equals(got);
        if (ok) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL  " + keys + "   →   " + got + "   (ожидалось: " + expected + ")");
        }
    }

    public static void main(String[] args) {
        // базовая арифметика
        check("2 + 3 =", "5");
        check("7 × 8 =", "56");
        check("9 ÷ 2 =", "4,5");
        check("1 ÷ 3 =", "0,333333");
        check("2 + 3 × 4 =", "14");
        check("( 2 + 3 ) × 4 =", "20");
        check("2 × ( 3 + 4 =", "14");
        check("− 3 + 5 =", "2");
        check("( − 3 ) × 2 =", "−6");
        check("0 . 5 + 0 . 2 5 =", "0,75");
        check("2 , 5 + 1 =", "3,5");
        check("0 . . 5 =", "0,5");
        check("1 ÷ 0 =", null);
        // проценты, степени, факториал, π
        check("200 × 15 % =", "30");
        check("50 % =", "0,5");
        check("9 ^ 9 =", "387420489");
        check("2 ^ 3 ^ 2 =", "512");
        check("5 ! =", "120");
        check("π × 2 =", "6,28319");
        check("10^ 2 =", "100");
        check("e ^ 1 =", "2,71828");
        // корни
        check("√( 16 ) =", "4");
        check("√( 50 ) =", "7,071068");
        // trig (градусы по умолчанию)
        check("sin( 30 ) =", "0,5");
        check("cos( 60 ) =", "0,5");
        check("tan( 45 ) =", "1");
        check("asin( 0.5 ) =", "30");
        check("sin( 30 ) + cos( 60 ) =", "1");
        check("sin( ( 30 ) ) =", "0,5");
        check("2 sin( 30 ) =", "1");
        check("log( 100 ) =", "2");
        check("ln( e ) =", "1");
        // ввод, исправления
        check("5 + =", "5");
        check("2 + 3 ⌫ =", "2");
        check("− 5 + 3 ⌫ ⌫ =", "−5");
        check("1 2 + 3 4 CE =", "12");
        check("1 2 + 3 4 CE CE =", "12");
        check("2 + 3 × ÷ 2 =", "3,5");
        check("2 × − 3 =", "−6");
        check("2 + 3 C 7 =", "7");
        // ±
        check("5 ± + 3 =", "−2");
        check("2 × ( 3 ) ± =", "−6");
        check("( 2 + 3 ) ± =", "−5");
        // 1/x
        check("4 1/x =", "0,25");
        check("1 0 0 1/x =", "0,01");
        // продолжение с результатом
        check("2 + 3 = × 2 =", "10");
        check("2 + 3 = + 1 =", "6");
        check("2 + 3 = 7 =", "7");
        check("9 ^ 2 = ⌫ ⌫ 1 =", "1");
        // память
        check("5 M+ C 3 M+ MR =", "8");
        check("5 M+ MR × 2 =", "10");
        check("2 + 3 = M+ C 1 MR =", "5");
        check("MR =", "пусто");
        // точка и операторы подряд
        check("2 × 3 × = ", "6");
        check("2 × 3 ÷ × 4 =", "24");
        check("2 + × 3 =", "6");
        // ОШИБКИ НЕ ДОЛЖНЫ ЛОМАТЬ СЛЕДУЮЩИЙ ВВОД
        check("1 ÷ 0 = 4 =", "4");
        check("5 M+ C 3 M− MR =", "2");
        System.out.println("Калькулятор: пройдено " + pass + ", провалено " + fail);
        if (fail > 0) System.exit(1);
    }
}
