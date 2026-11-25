package com.bookmatrix.demo;

import com.bookmatrix.analytics.EventAnalytics;
import com.bookmatrix.model.Event;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Lambdas, functional interfaces, method references and the Stream API. */
public class StreamsDemo implements ConceptDemo {

    /** A custom functional interface: exactly one abstract method. */
    @FunctionalInterface
    interface PriceRule {
        BigDecimal apply(BigDecimal price);
    }

    @Override public String name() { return "streams"; }
    @Override public String title() { return "Lambda expressions and the Stream API"; }
    @Override public String topics() { return "Lambda syntax, Functional interfaces, Method references, Stream sources, Pipelines, Lazy execution, Intermediate operations, Terminal operations, filter, map, sorted, distinct, limit, skip"; }

    @Override
    public void run(DemoContext ctx) {
        PrintStream out = ctx.out();
        LocalDateTime now = LocalDateTime.now();
        List<Event> events = sampleEvents(now);

        section(out, "Lambda syntax and functional interfaces");
        Predicate<Event> cheap = e -> e.getTicketPrice().compareTo(new BigDecimal("600")) < 0;   // one parameter
        BiFunction<Integer, BigDecimal, BigDecimal> total = (qty, price) -> price.multiply(BigDecimal.valueOf(qty));
        Supplier<String> greeting = () -> "Welcome to BookMatrix";                              // no parameters
        Consumer<String> printer = s -> out.println("  " + s);
        PriceRule earlyBird = price -> price.multiply(new BigDecimal("0.90"));                  // custom interface
        printer.accept(greeting.get());
        printer.accept("3 x Rs.799 = " + total.apply(3, new BigDecimal("799")));
        printer.accept("early-bird price of 1000 = " + earlyBird.apply(new BigDecimal("1000")));
        printer.accept("is 'Stand-up Saturday' cheap? " + cheap.test(events.get(2)));

        section(out, "Method references");
        Function<Event, String> titleOf = Event::getTitle;          // instance method of any Event
        Function<String, Integer> parse = Integer::parseInt;        // static method
        Supplier<List<String>> newList = ArrayList::new;            // constructor
        out.println("Event::getTitle -> " + titleOf.apply(events.get(0)) + ", Integer::parseInt(\"42\") -> "
                + parse.apply("42") + ", ArrayList::new -> " + newList.get());

        section(out, "Stream sources");
        out.println("List.stream()  : " + events.stream().count() + " events");
        out.println("Stream.of(...) : " + Stream.of("Pune", "Mumbai").collect(Collectors.joining(", ")));
        out.println("Arrays.stream  : " + Arrays.stream(new int[]{2, 4, 1}).sum() + " tickets");
        out.println("IntStream.range: " + IntStream.rangeClosed(1, 5).boxed().toList());
        out.println("Stream.iterate : " + Stream.iterate(1, n -> n * 2).limit(6).toList());

        section(out, "Lazy execution: nothing runs until a terminal operation");
        Stream<String> pipeline = events.stream()
                .peek(e -> out.println("  looking at " + e.getTitle()))
                .filter(e -> e.getCity().equals("Pune"))
                .map(Event::getTitle);
        out.println("pipeline built - notice no 'looking at' lines yet");
        out.println("terminal toList() -> " + pipeline.toList());

        section(out, "filter / map / sorted / distinct / limit / skip");
        out.println("filter(Music)          : " + events.stream().filter(EventAnalytics.inCategory("Music")).map(Event::getTitle).toList());
        out.println("map(title length)      : " + events.stream().map(e -> e.getTitle().length()).toList());
        out.println("sorted(by price)       : " + events.stream().sorted((a, b) -> a.getTicketPrice().compareTo(b.getTicketPrice()))
                .map(e -> e.getTitle() + " " + e.getTicketPrice()).toList());
        out.println("distinct cities        : " + EventAnalytics.distinctCities(events));
        out.println("limit(2) soonest       : " + events.stream().sorted().limit(2).map(Event::getTitle).toList());
        out.println("skip(2) (page 2 of 2)  : " + EventAnalytics.page(events.stream().sorted().toList(), 2, 2)
                .stream().map(Event::getTitle).toList());

        section(out, "Terminal operations");
        out.println("count()                : " + events.stream().filter(cheap).count());
        out.println("anyMatch(sold out)     : " + events.stream().anyMatch(e -> e.getAvailableTickets() == 0));
        out.println("reduce(sum of sold)    : " + events.stream().map(Event::getTicketsSold).reduce(0, Integer::sum));
        out.println("average price          : " + String.format("%.2f", EventAnalytics.averageTicketPrice(events).orElse(0)));
        out.println("groupingBy(category)   : " + EventAnalytics.ticketsByCategory(events));
        out.println("high priced (>=1000)   : " + EventAnalytics.highPriced(events, new BigDecimal("1000")).stream().map(Event::getTitle).toList());
        out.println();
        out.println("The same EventAnalytics methods power the admin dashboard with real data from MySQL.");
    }

    static List<Event> sampleEvents(LocalDateTime now) {
        Object[][] data = {
                {"Indie Night", "Music", "Pune", "799", 200, 150, 10},
                {"Jazz Brunch", "Music", "Mumbai", "1200", 80, 80, 5},
                {"Stand-up Saturday", "Comedy", "Bengaluru", "499", 120, 60, 3},
                {"Hamlet Reimagined", "Theatre", "Pune", "1500", 300, 90, 20},
                {"Data Summit", "Conference", "Hyderabad", "2500", 500, 410, 30}};
        List<Event> events = new ArrayList<>();
        long id = 1;
        for (Object[] d : data) {
            Event e = new Event();
            e.setId(id++);
            e.setTitle((String) d[0]);
            e.setCategoryName((String) d[1]);
            e.setCity((String) d[2]);
            e.setTicketPrice(new BigDecimal((String) d[3]));
            e.setCapacity((Integer) d[4]);
            e.setTicketsSold((Integer) d[5]);
            e.setStartsAt(now.plusDays((Integer) d[6]));
            e.setEndsAt(now.plusDays((Integer) d[6]).plusHours(3));
            events.add(e);
        }
        return events;
    }
}
