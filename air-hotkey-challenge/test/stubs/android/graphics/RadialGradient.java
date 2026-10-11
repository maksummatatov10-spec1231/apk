package android.graphics;
public class RadialGradient extends Shader {
    final float x, y, r; final int c0, c1;
    public RadialGradient(float x, float y, float r, int c0, int c1, TileMode m) {
        this.x = x; this.y = y; this.r = r; this.c0 = c0; this.c1 = c1; }
    String spec() { return String.format(java.util.Locale.ROOT, "rad:%.1f,%.1f,%.1f,%08x,%08x", x, y, r, c0, c1); }
}
