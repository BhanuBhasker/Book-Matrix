package com.bookmatrix.unit;

import com.bookmatrix.demo.ConceptDemo;
import com.bookmatrix.demo.DemoContext;
import com.bookmatrix.demo.DemoRegistry;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Every syllabus demo must run without throwing and print its key results (JDBC is tested with MySQL elsewhere). */
class DemosRunTest {

    private static final Map<String, String> EXPECTED = Map.of(
            "oop", "describe() ->",
            "arrays", "First block of 4 adjacent free seats",
            "strings", "a.equals(c)   : true",
            "exceptions", "close database connection",
            "collections", "unmodifiableList rejects add()",
            "threads", "Observed: [NEW, RUNNABLE, TIMED_WAITING, BLOCKED, WAITING, TERMINATED]",
            "race", "synchronized method    successful bookings=1, tickets left=0",
            "producer-consumer", "allThreadsFinished=true",
            "streams", "terminal toList()",
            "generics", "ClassCastException");

    @TestFactory
    Stream<DynamicTest> everyDemoRuns() {
        return DemoRegistry.all().stream().filter(d -> !d.name().equals("jdbc")).map(demo ->
                DynamicTest.dynamicTest(demo.name(), () -> {
                    String output = run(demo);
                    assertThat(output).isNotBlank();
                    if (EXPECTED.containsKey(demo.name())) {
                        assertThat(output).contains(EXPECTED.get(demo.name()));
                    }
                }));
    }

    static String run(ConceptDemo demo) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        demo.run(new DemoContext(new PrintStream(buffer, true, StandardCharsets.UTF_8), null, null, null));
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
