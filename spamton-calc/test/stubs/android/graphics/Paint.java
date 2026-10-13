package android.graphics;

/**
 * Заглушка Paint для JVM-харнесса: цвет, размер и жирность текста.
 * Ширина символа моноширинного шрифта DejaVu Sans Mono ≈ 0.602 em (как в рендере PNG).
 */
public class Paint {
    static final float MONO_ADVANCE = 0.602f;

    int color = 0xFF000000;
    float textSize = 12f;
    boolean bold;
    Shader shader;

    public void setColor(int c) { color = c; }
    public void setShader(Shader s) { shader = s; }
    public void setTextSize(float s) { textSize = s; }
    public void setTypeface(Typeface t) { }
    public void setFakeBoldText(boolean b) { bold = b; }
    public void setAntiAlias(boolean b) { }
    public void setFilterBitmap(boolean b) { }

    public float measureText(String s) {
        return s.length() * MONO_ADVANCE * textSize;
    }
}
