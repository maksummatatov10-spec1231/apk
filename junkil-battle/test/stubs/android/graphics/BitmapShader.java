package android.graphics;

/** Заглушка BitmapShader: запоминает картинку, рисует её плиткой render_ops.py. */
public class BitmapShader extends Shader {
    final Bitmap bitmap;

    public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
        this.bitmap = bitmap;
    }
}
