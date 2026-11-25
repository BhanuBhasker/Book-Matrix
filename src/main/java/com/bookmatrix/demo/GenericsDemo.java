package com.bookmatrix.demo;

import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** Generic classes and methods, bounded types, wildcards, diamond operator and type safety. */
public class GenericsDemo implements ConceptDemo {

    /** Generic class with one type parameter. */
    public static class Box<T> {
        private final T value;

        public Box(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }
    }

    /** Generic class with two type parameters. */
    public record Pair<K, V>(K key, V value) { }

    @Override public String name() { return "generics"; }
    @Override public String title() { return "Generics"; }
    @Override public String topics() { return "Generic classes, Generic methods, Type safety, Diamond operator, Bounded generics, Wildcards"; }

    /** Generic method: <T> is declared before the return type. */
    public static <T> T firstOrDefault(List<T> list, T fallback) {
        return list.isEmpty() ? fallback : list.get(0);
    }

    /** Bounded type: T must be a Number (Integer, Double, BigDecimal...), so doubleValue() exists. */
    public static <T extends Number> double sum(List<T> numbers) {
        double total = 0;
        for (T n : numbers) {
            total += n.doubleValue();
        }
        return total;
    }

    /** Bounded by Comparable: works for any type that knows how to compare itself. */
    public static <T extends Comparable<? super T>> T max(List<T> items) {
        T best = items.get(0);
        for (T item : items) {
            if (item.compareTo(best) > 0) {
                best = item;
            }
        }
        return best;
    }

    /** Upper-bounded wildcard (? extends Number): we only READ numbers out of the list. */
    public static double average(List<? extends Number> values) {
        return values.isEmpty() ? 0 : sum(values) / values.size();
    }

    /** Lower-bounded wildcard (? super Integer): we only PUT Integers into the list. */
    public static void addTicketNumbers(List<? super Integer> target, int count) {
        for (int i = 1; i <= count; i++) {
            target.add(i);
        }
    }

    /** Unbounded wildcard: any list at all. */
    public static int countAll(List<?> anything) {
        return anything.size();
    }

    @Override
    public void run(DemoContext ctx) {
        PrintStream out = ctx.out();

        section(out, "Generic classes + diamond operator");
        Box<String> reference = new Box<>("BM7K2Q9XWD");        // <> = diamond: type inferred
        Box<Integer> quantity = new Box<>(3);
        Pair<String, Integer> sold = new Pair<>("Indie Night", 150);
        out.println("Box<String>=" + reference.get() + ", Box<Integer>=" + quantity.get() + ", Pair=" + sold);

        section(out, "Generic methods");
        out.println("firstOrDefault([], \"none\")      = " + firstOrDefault(List.<String>of(), "none"));
        out.println("firstOrDefault([Pune, Delhi])   = " + firstOrDefault(List.of("Pune", "Delhi"), "none"));

        section(out, "Bounded generics");
        out.println("sum(List<Integer>)  = " + sum(List.of(2, 4, 1)));
        out.println("sum(List<Double>)   = " + sum(List.of(799.5, 1200.0)));
        out.println("max(List<String>)   = " + max(List.of("Comedy", "Music", "Theatre")));

        section(out, "Wildcards");
        out.println("average(List<? extends Number>) = " + average(List.of(799, 1200, 499)));
        List<Number> numbers = new ArrayList<>();
        addTicketNumbers(numbers, 3);                             // List<Number> is a List<? super Integer>
        out.println("addTicketNumbers(List<? super Integer>) -> " + numbers);
        out.println("countAll(List<?>) = " + countAll(List.of("a", 1, 2.5)));

        section(out, "Type safety");
        List<String> titles = new ArrayList<>();
        titles.add("Indie Night");
        // titles.add(42);   <- does not compile: the compiler protects us
        String first = titles.get(0);                             // no cast needed
        out.println("List<String> needs no casts: " + first);
        @SuppressWarnings({"rawtypes", "unchecked"})
        List raw = new ArrayList();                               // raw type: no checking at all
        raw.add(42);
        try {
            @SuppressWarnings("unchecked")
            String broken = ((List<String>) raw).get(0);
            out.println(broken);
        } catch (ClassCastException e) {
            out.println("Raw type let an Integer in -> ClassCastException at run time. Generics prevent this.");
        }

        section(out, "Generics in the real application");
        PageResult<String> page = PageResult.of(List.of("Indie Night", "Jazz Brunch"), new PageRequest(1, 12), 2);
        PageResult<Integer> lengths = page.map(String::length);
        out.println("PageResult<String> " + page.items() + " -> map(String::length) -> PageResult<Integer> " + lengths.items());
        out.println("Also: ApiResponse<T>, Dao<T, ID>, RowMapper<T>, SqlFunction<T, R> in the production code.");
    }
}
