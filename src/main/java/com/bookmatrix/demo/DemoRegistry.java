package com.bookmatrix.demo;

import java.util.List;
import java.util.Optional;

/** The list of all demonstrations, in the order they appear in the syllabus. */
public final class DemoRegistry {

    private DemoRegistry() {
    }

    public static List<ConceptDemo> all() {
        return List.of(
                new OopDemo(),
                new SeatMapDemo(),
                new StringsDemo(),
                new ExceptionsDemo(),
                new CollectionsDemo(),
                new ThreadBasicsDemo(),
                new RaceConditionDemo(),
                new ProducerConsumerDemo(),
                new JdbcDemo(),
                new StreamsDemo(),
                new GenericsDemo(),
                new FileHandlingDemo());
    }

    public static Optional<ConceptDemo> find(String name) {
        return all().stream().filter(d -> d.name().equalsIgnoreCase(name)).findFirst();
    }
}
