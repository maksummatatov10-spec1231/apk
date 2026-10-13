package android.graphics;

/** Заглушка Rect для JVM-харнесса (в APK не попадает): только поля и конструктор. */
public class Rect {
    public int left, top, right, bottom;

    public Rect() {}

    public Rect(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }
}
