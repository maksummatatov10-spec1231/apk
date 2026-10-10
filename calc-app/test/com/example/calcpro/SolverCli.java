package com.example.calcpro;

import com.example.calcpro.math.MathError;
import com.example.calcpro.math.Solver;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

/**
 * Консольная обёртка над тем же решателем, что и приложение (без Android).
 * Читает запросы построчно из stdin, на каждый запрос печатает одну строку JSON:
 * {"query","heading","answers","steps","graph","error"}.
 * Режим --catalog печатает примеры из приложения (раздел «Уравнения» и «Примеры»).
 */
public final class SolverCli {
    private SolverCli() {}

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        if (args.length > 0 && "--catalog".equals(args[0])) {
            out.println(catalogJson());
            return;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            out.println(solveJson(line));
        }
    }

    static String solveJson(String query) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"query\":").append(str(query));
        try {
            Solver.Result r = Solver.solve(query);
            sb.append(",\"heading\":").append(str(r.heading));
            sb.append(",\"answers\":").append(arr(r.answers));
            sb.append(",\"steps\":").append(arr(r.steps));
            sb.append(",\"graph\":").append(r.graph != null ? "true" : "false");
            sb.append(",\"error\":null");
        } catch (MathError e) {
            sb.append(",\"heading\":null,\"answers\":[],\"steps\":[],\"graph\":false");
            sb.append(",\"error\":").append(str(e.getMessage()));
        } catch (RuntimeException e) {
            sb.append(",\"heading\":null,\"answers\":[],\"steps\":[],\"graph\":false");
            sb.append(",\"error\":").append(str("Не удалось решить: проверьте запись (" + e + ")"));
        }
        return sb.append('}').toString();
    }

    static String catalogJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean firstGroup = true;
        for (String group : new String[] {"equations", "examples"}) {
            Catalog.Section[] sections = "equations".equals(group) ? Catalog.EQUATIONS : Catalog.EXAMPLES;
            for (Catalog.Section s : sections) {
                for (Catalog.Item it : s.items) {
                    if (!firstGroup) sb.append(',');
                    firstGroup = false;
                    sb.append("{\"group\":").append(str(group))
                      .append(",\"section\":").append(str(s.title))
                      .append(",\"expr\":").append(str(it.expr))
                      .append(",\"note\":").append(str(it.note)).append('}');
                }
            }
        }
        return sb.append(']').toString();
    }

    private static String arr(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(str(items.get(i)));
        }
        return sb.append(']').toString();
    }

    static String str(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
