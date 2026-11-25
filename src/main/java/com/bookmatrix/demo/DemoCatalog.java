package com.bookmatrix.demo;

import com.bookmatrix.dto.Views.DemoInfo;
import com.bookmatrix.dto.Views.DemoOutput;
import com.bookmatrix.exception.NotFoundException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs demos for the admin "Java Lab" page and captures what they print. */
@Component
public class DemoCatalog {

    private final Environment env;

    public DemoCatalog(Environment env) {
        this.env = env;
    }

    public List<DemoInfo> list() {
        return DemoRegistry.all().stream().map(d -> new DemoInfo(d.name(), d.title(), d.topics())).toList();
    }

    /** synchronized: one demo at a time, so outputs never mix. Each run gets 30 seconds at most. */
    public synchronized DemoOutput run(String name) {
        ConceptDemo demo = DemoRegistry.find(name).orElseThrow(() -> new NotFoundException("No demo called '" + name + "'."));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        DemoContext ctx = new DemoContext(out, env.getProperty("spring.datasource.url"),
                env.getProperty("spring.datasource.username"), env.getProperty("spring.datasource.password"));
        long start = System.currentTimeMillis();
        boolean success = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> {
                demo.run(ctx);
                return null;
            });
            future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            success = false;
            out.println("Demo failed: " + e.getCause());
        } catch (TimeoutException e) {
            success = false;
            out.println("Demo stopped: it took longer than 30 seconds.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            success = false;
        } finally {
            executor.shutdownNow();
        }
        return new DemoOutput(demo.name(), demo.title(), buffer.toString(StandardCharsets.UTF_8),
                System.currentTimeMillis() - start, success);
    }
}
