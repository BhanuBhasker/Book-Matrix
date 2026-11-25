package com.bookmatrix.demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point for the syllabus demonstrations:
 *   mvn -q compile exec:java                      -> list demos
 *   mvn -q compile exec:java -Dexec.args="all"    -> run every demo
 *   mvn -q compile exec:java -Dexec.args="race jdbc"
 * Database settings for the JDBC demo are read from environment variables or the .env file.
 */
public final class DemoRunner {

    private DemoRunner() {
    }

    public static void main(String[] args) throws IOException {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Map<String, String> env = loadEnv(new File(".env"));
        DemoContext ctx = new DemoContext(out, env.get("DB_URL"), env.get("DB_USERNAME"), env.get("DB_PASSWORD"));

        if (args.length == 0 || args[0].equals("list")) {
            out.println("BookMatrix Java syllabus demos. Run with -Dexec.args=\"<name> ...\" or \"all\":");
            for (ConceptDemo d : DemoRegistry.all()) {
                out.printf("  %-18s %s%n", d.name(), d.title());
            }
            return;
        }
        List<ConceptDemo> selected = args[0].equals("all") ? DemoRegistry.all()
                : java.util.Arrays.stream(args).map(name -> DemoRegistry.find(name)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown demo '" + name + "'. Use 'list'.")))
                .toList();
        int failures = 0;
        for (ConceptDemo demo : selected) {
            out.println();
            out.println("████ " + demo.title().toUpperCase() + " ████");
            out.println("Topics: " + demo.topics());
            try {
                demo.run(ctx);
            } catch (Exception e) {
                failures++;
                out.println("Demo failed: " + e);
            }
        }
        if (failures > 0) {
            System.exit(1);
        }
    }

    /** Reads KEY=VALUE lines from .env with a BufferedReader; real environment variables win. */
    static Map<String, String> loadEnv(File file) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    int eq = line.indexOf('=');
                    if (line.isEmpty() || line.startsWith("#") || eq < 1) {
                        continue;
                    }
                    values.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            }
        }
        for (String key : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD")) {
            String fromEnv = System.getenv(key);
            if (fromEnv != null && !fromEnv.isBlank()) {
                values.put(key, fromEnv);
            }
        }
        return values;
    }
}
