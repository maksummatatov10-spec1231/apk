package android.graphics;

/** Заглушка RectF для JVM-харнесса (в APK не попадает). */
public class RectF {
    public float left, top, right, bottom;

    public RectF() {}

    public void set(float l, float t, float r, float b) {
        left = l; top = t; right = r; bottom = b;
    }
}
