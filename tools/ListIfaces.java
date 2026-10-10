import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.*;
public class ListIfaces {
    static void check(Path p) {
        String s = p.toString();
        if (!s.endsWith(".class") || s.endsWith("module-info.class")) return;
        String n = s.substring("/modules/".length());
        n = n.substring(n.indexOf('/') + 1);
        n = n.substring(0, n.length() - 6);
        try (InputStream in = Files.newInputStream(p)) {
            DataInputStream d = new DataInputStream(in);
            d.readInt(); d.readUnsignedShort(); d.readUnsignedShort();
            int cp = d.readUnsignedShort();
            for (int i = 1; i < cp; i++) {
                int tag = d.readUnsignedByte();
                switch (tag) {
                    case 1: d.readFully(new byte[d.readUnsignedShort()]); break;
                    case 3: case 4: d.readInt(); break;
                    case 5: case 6: d.readLong(); i++; break;
                    case 7: case 8: case 16: case 19: case 20: d.readUnsignedShort(); break;
                    case 9: case 10: case 11: case 12: case 17: case 18: d.readInt(); break;
                    case 15: d.readUnsignedByte(); d.readUnsignedShort(); break;
                    default: return;
                }
            }
            int acc = d.readUnsignedShort();
            if ((acc & 0x200) != 0) System.out.println(n);
        } catch (IOException e) { }
    }
    public static void main(String[] args) throws IOException {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        Files.walkFileTree(fs.getPath("/modules"), new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                check(file); return FileVisitResult.CONTINUE;
            }
        });
    }
}
