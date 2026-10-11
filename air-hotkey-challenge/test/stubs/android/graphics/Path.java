package android.graphics;
public class Path {
    private final StringBuilder sb = new StringBuilder();
    public void reset() { sb.setLength(0); }
    public void moveTo(float x, float y) { sb.append(String.format(java.util.Locale.ROOT, "M %.2f %.2f ", x, y)); }
    public void lineTo(float x, float y) { sb.append(String.format(java.util.Locale.ROOT, "L %.2f %.2f ", x, y)); }
    public void cubicTo(float a, float b, float c, float d, float e, float f) {
        sb.append(String.format(java.util.Locale.ROOT, "C %.2f %.2f %.2f %.2f %.2f %.2f ", a, b, c, d, e, f));
    }
    String data() { return sb.toString().trim().replace(" ", "\u0002"); }
}
