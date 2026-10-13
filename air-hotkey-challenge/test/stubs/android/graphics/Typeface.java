package android.graphics;
public class Typeface {
    public static final int NORMAL = 0;
    boolean bold;
    public static Typeface create(String f, int style) { Typeface t = new Typeface(); t.bold = f.contains("black"); return t; }
}
