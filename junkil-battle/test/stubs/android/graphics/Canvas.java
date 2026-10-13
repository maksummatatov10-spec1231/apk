package android.graphics;

import java.util.ArrayDeque;

/**
 * Заглушка Canvas для JVM-харнесса (в APK не попадает).
 * Ведёт матрицу (translate/scale), пишет команды в ops-журнал в абсолютных координатах;
 * картинку строит scripts/render_ops.py.
 */
public class Canvas {
    private final StringBuilder ops = new StringBuilder();
    private final ArrayDeque<float[]> stack = new ArrayDeque<>();
    private float tx, ty, sx = 1f, sy = 1f;

    public String ops() { return ops.toString(); }

    private static String hex(int argb) {
        return String.format("%08X", argb);
    }

    public int save() {
        stack.push(new float[] {tx, ty, sx, sy});
        ops.append("S\n");
        return stack.size();
    }

    public void restore() {
        if (!stack.isEmpty()) {
            float[] m = stack.pop();
            tx = m[0]; ty = m[1]; sx = m[2]; sy = m[3];
        }
        ops.append("E\n");
    }

    public void translate(float dx, float dy) {
        tx += dx * sx;
        ty += dy * sy;
    }

    public void scale(float a, float b) {
        sx *= a;
        sy *= b;
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        ops.append("R ").append(tx + l * sx).append(' ').append(ty + t * sy).append(' ')
           .append(tx + r * sx).append(' ').append(ty + b * sy)
           .append(' ').append(hex(p.color)).append('\n');
    }

    public void drawText(String s, float x, float y, Paint p) {
        ops.append("T ").append(tx + x * sx).append(' ').append(ty + y * sy).append(' ')
           .append(p.textSize).append(' ').append(p.bold ? 1 : 0).append(' ').append(hex(p.color)).append(' ')
           .append(s.replace("\n", " ").replace("\t", " ")).append('\n');
    }

    public void drawBitmap(Bitmap bm, float left, float top, Paint p) {
        ops.append("I ").append(bm.path).append(' ').append(tx + left * sx).append(' ').append(ty + top * sy)
           .append(' ').append(bm.getWidth() * sx).append(' ').append(bm.getHeight() * sy).append('\n');
    }

    /** Кусок битмапа src (пиксели) в прямоугольник dst (координаты холста). */
    public void drawBitmap(Bitmap bm, Rect src, RectF dst, Paint p) {
        ops.append("J ").append(bm.path).append(' ')
           .append(src.left).append(' ').append(src.top).append(' ').append(src.right).append(' ').append(src.bottom).append(' ')
           .append(tx + dst.left * sx).append(' ').append(ty + dst.top * sy).append(' ')
           .append(tx + dst.right * sx).append(' ').append(ty + dst.bottom * sy).append('\n');
    }

    /** Круг (заливка): O cx cy r ARGB. */
    public void drawCircle(float cx, float cy, float r, Paint p) {
        ops.append("O ").append(tx + cx * sx).append(' ').append(ty + cy * sy).append(' ')
           .append(r * sx).append(' ').append(hex(p.color)).append('\n');
    }

    /**
     * Многоугольник из Path: P ARGB x y ... (заливка цветом) или PT путь_к_текстуре ARGB x y ...
     * (заливка плиткой текстуры, как BitmapShader с REPEAT).
     */
    public void drawPath(Path path, Paint p) {
        StringBuilder sb = new StringBuilder();
        if (p.shader instanceof BitmapShader) {
            sb.append("PT ").append(((BitmapShader) p.shader).bitmap.path).append(' ');
        } else {
            sb.append("P ");
        }
        sb.append(hex(p.color));
        for (float[] pt : path.points) {
            sb.append(' ').append(tx + pt[0] * sx).append(' ').append(ty + pt[1] * sy);
        }
        ops.append(sb).append('\n');
    }

    public void clipRect(float l, float t, float r, float b) {
        ops.append("C ").append(tx + l * sx).append(' ').append(ty + t * sy).append(' ')
           .append(tx + r * sx).append(' ').append(ty + b * sy).append('\n');
    }
}
