package com.example.calcpro.math;

/** Ошибка разбора или вычисления, сообщение показывается пользователю. */
public final class MathError extends RuntimeException {
    public MathError(String message) {
        super(message);
    }
}
