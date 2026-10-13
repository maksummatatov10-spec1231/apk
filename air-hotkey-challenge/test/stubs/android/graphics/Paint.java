package android.graphics;

/** Заглушка для симулятора: состояние кисти; рисуется через Canvas (Java2D). */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Align { LEFT, CENTER, RIGHT }

    int color = 0xFF000000;
    Style style = Style.FILL;
    float strokeWidth = 1f;
    Shader shader;
    float textSize = 12f;
    boolean bold;
    Align align = Align.LEFT;
    float shadowRadius;
    int shadowColor;

    public Paint() {}
    public Paint(int flags) {}
    public void reset() { color = 0xFF000000; style = Style.FILL; strokeWidth = 1f; shader = null;
        textSize = 12f; bold = false; align = Align.LEFT; shadowRadius = 0; }
    public void setAntiAlias(boolean b) {}
    public void setShader(Shader s) { shader = s; }
    public void setStyle(Style s) { style = s; }
    public void setColor(int c) { color = c; }
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public void setTypeface(Typeface t) { bold = t != null && t.bold; }
    public void setTextSize(float s) { textSize = s; }
    public void setTextAlign(Align a) { align = a; }
    public void setShadowLayer(float r, float dx, float dy, int c) { shadowRadius = r; shadowColor = c; }
    public void clearShadowLayer() { shadowRadius = 0; }
}
