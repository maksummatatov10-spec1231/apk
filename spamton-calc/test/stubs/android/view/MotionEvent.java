package android.view;

/** Заглушка касания для тестов. */
public final class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;

    private final int action;
    private final float x, y;

    private MotionEvent(int action, float x, float y) {
        this.action = action;
        this.x = x;
        this.y = y;
    }

    public static MotionEvent obtain(int action, float x, float y) {
        return new MotionEvent(action, x, y);
    }

    public int getActionMasked() { return action; }
    public float getX() { return x; }
    public float getY() { return y; }
}
