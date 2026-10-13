package android.graphics;

/** Заглушка Path для JVM-харнесса (в APK не попадает): замкнутый многоугольник. */
public class Path {
    final java.util.ArrayList<float[]> points = new java.util.ArrayList<>();
    boolean closed;

    public void reset() { points.clear(); closed = false; }
    public void moveTo(float x, float y) { reset(); points.add(new float[] {x, y}); }
    public void lineTo(float x, float y) { points.add(new float[] {x, y}); }
    public void close() { closed = true; }
}
