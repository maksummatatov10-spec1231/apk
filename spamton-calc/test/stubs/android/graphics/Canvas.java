package android.graphics;

/**
 * Заглушка Canvas для JVM-харнесса (в APK не попадает).
 * Не рисует сама: записывает команды в ops-журнал, а картинку делает scripts/render_ops.py.
 */
public class Canvas {
    private final StringBuilder ops = new StringBuilder();

    public String ops() { return ops.toString(); }

    private static String hex(int argb) {
        return String.format("%08X", argb);
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        ops.append("R ").append(l).append(' ').append(t).append(' ').append(r).append(' ').append(b)
           .append(' ').append(hex(p.color)).append('\n');
    }

    public void drawText(String s, float x, float y, Paint p) {
        ops.append("T ").append(x).append(' ').append(y).append(' ').append(p.textSize)
           .append(' ').append(p.bold ? 1 : 0).append(' ').append(hex(p.color)).append(' ')
           .append(s.replace("\n", " ").replace("\t", " ")).append('\n');
    }

    public int save() {
        ops.append("S\n");
        return 0;
    }

    public void clipRect(float l, float t, float r, float b) {
        ops.append("C ").append(l).append(' ').append(t).append(' ').append(r).append(' ').append(b).append('\n');
    }

    public void restore() {
        ops.append("E\n");
    }
}
