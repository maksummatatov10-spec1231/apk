package android.content;
public class SharedPreferences {
    private int best = 0;
    public int getInt(String k, int d) { return best; }
    public Editor edit() { return new Editor(this); }
    public static class Editor {
        private final SharedPreferences owner;
        Editor(SharedPreferences o) { owner = o; }
        public Editor putInt(String k, int v) { owner.best = v; return this; }
        public void apply() {}
    }
}
