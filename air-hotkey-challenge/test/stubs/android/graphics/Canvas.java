package android.graphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Заглушка Canvas для симулятора: записывает команды рисования в текстовый список,
 * который потом превращает в PNG скрипт tools/render_frames.py. Формат строки:
 * «вид аргументы | цвет стиль толщина шейдер текст шадоу».
 */
public class Canvas {
    public static final List<String> ops = new ArrayList<String>();

    public void save() { ops.add("S"); }
    public void restore() { ops.add("R"); }
    public void translate(float x, float y) { ops.add(String.format(Locale.ROOT, "T %.2f %.2f", x, y)); }

    private static String spec(Paint p) {
        String st = p.style == Paint.Style.STROKE ? "S" : "F";
        String sh = p.shader == null ? "-" : p.shader.spec();
        return String.format(Locale.ROOT, "%08x %s %.2f %s %.2f %d %s %.2f %08x",
                p.color, st, p.strokeWidth, sh, p.textSize, p.bold ? 1 : 0,
                p.align.name().substring(0, 1), p.shadowRadius, p.shadowColor);
    }

    private static String f(float v) { return String.format(Locale.ROOT, "%.2f", v); }

    public void drawCircle(float x, float y, float r, Paint p) {
        ops.add("circle " + f(x) + " " + f(y) + " " + f(r) + " | " + spec(p));
    }
    public void drawRect(float l, float t, float r, float b, Paint p) {
        ops.add("rect " + f(l) + " " + f(t) + " " + f(r) + " " + f(b) + " | " + spec(p));
    }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        ops.add("line " + f(x0) + " " + f(y0) + " " + f(x1) + " " + f(y1) + " | " + spec(p));
    }
    public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        ops.add("rrect " + f(l) + " " + f(t) + " " + f(r) + " " + f(b) + " " + f(rx) + " | " + spec(p));
    }
    public void drawArc(float l, float t, float r, float b, float s, float e, boolean c, Paint p) {
        ops.add("arc " + f(l) + " " + f(t) + " " + f(r) + " " + f(b) + " " + f(s) + " " + f(e) + " | " + spec(p));
    }
    public void drawPath(Path path, Paint p) {
        ops.add("path " + path.data() + " | " + spec(p));
    }
    public void drawText(String s, float x, float y, Paint p) {
        ops.add("text " + f(x) + " " + f(y) + " " + s.replace(" ", "\u0001") + " | " + spec(p));
    }
}
