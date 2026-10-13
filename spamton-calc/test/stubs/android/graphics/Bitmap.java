package android.graphics;

/** Заглушка Bitmap для JVM-харнесса: только путь к файлу и размер (картинку рисует render_ops.py). */
public final class Bitmap {
    final String path;
    final int width, height;

    public Bitmap(String path, int width, int height) {
        this.path = path;
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
