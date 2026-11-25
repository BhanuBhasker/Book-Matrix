package com.bookmatrix.demo;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Every collection from the syllabus, each with booking-themed data, an operation and the result. */
public class CollectionsDemo implements ConceptDemo {

    /** Comparable: a ticket request's natural order is by booking time (earliest first). */
    record TicketRequest(String customer, int quantity, int priority, long bookedAtMillis)
            implements Comparable<TicketRequest> {
        @Override
        public int compareTo(TicketRequest other) {
            return Long.compare(this.bookedAtMillis, other.bookedAtMillis);
        }
    }

    record ShowListing(String title, String city, BigDecimal price) { }

    @Override public String name() { return "collections"; }
    @Override public String title() { return "Java Collections Framework"; }
    @Override public String topics() { return "ArrayList, LinkedList, HashSet, LinkedHashSet, TreeSet, HashMap, LinkedHashMap, TreeMap, Queue, Deque, PriorityQueue, Iterator, ListIterator, Comparable, Comparator, Collections utilities"; }

    @Override
    public void run(DemoContext ctx) {
        PrintStream out = ctx.out();

        section(out, "ArrayList - ordered, index access, fast get(i)");
        List<ShowListing> listings = new ArrayList<>();
        listings.add(new ShowListing("Indie Night", "Pune", new BigDecimal("799")));
        listings.add(new ShowListing("Jazz Brunch", "Mumbai", new BigDecimal("1200")));
        listings.add(new ShowListing("Stand-up Saturday", "Bengaluru", new BigDecimal("499")));
        out.println("get(1) = " + listings.get(1).title() + ", size = " + listings.size());
        out.println("Explanation: backed by an array, great for lists that are mostly read (like search results).");

        section(out, "LinkedList - cheap insert/remove at both ends");
        LinkedList<String> waitlist = new LinkedList<>(List.of("Ravi", "Meera"));
        waitlist.addFirst("VIP Anil");
        waitlist.addLast("Sara");
        out.println("waitlist = " + waitlist + ", removeFirst() -> " + waitlist.removeFirst());

        section(out, "HashSet - no duplicates (used to detect duplicate CSV rows)");
        Set<String> emails = new HashSet<>();
        for (String e : List.of("a@x.com", "b@x.com", "a@x.com")) {
            out.println("add(" + e + ") -> " + emails.add(e));
        }
        out.println("unique emails = " + emails.size());

        section(out, "LinkedHashSet - no duplicates AND keeps insertion order");
        Set<String> recentlyViewed = new LinkedHashSet<>(List.of("Jazz Brunch", "Indie Night", "Jazz Brunch", "Comedy"));
        out.println(recentlyViewed);

        section(out, "TreeSet - sorted, no duplicates");
        Set<String> cities = new TreeSet<>(List.of("Pune", "Delhi", "Mumbai", "Bengaluru", "Pune"));
        out.println(cities);

        section(out, "HashMap - key -> value lookup in O(1)");
        Map<String, Integer> ticketsByEvent = new HashMap<>();
        ticketsByEvent.put("Indie Night", 150);
        ticketsByEvent.put("Jazz Brunch", 80);
        ticketsByEvent.merge("Indie Night", 2, Integer::sum);   // someone booked 2 more
        out.println("Indie Night sold = " + ticketsByEvent.get("Indie Night") + ", missing key -> "
                + ticketsByEvent.getOrDefault("Unknown", 0));

        section(out, "LinkedHashMap - remembers insertion order (validation errors keep form order)");
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("fullName", "Enter your name.");
        errors.put("email", "Enter a valid email address.");
        errors.put("password", "Use at least 8 characters.");
        out.println(errors.keySet());

        section(out, "TreeMap - sorted by key (tickets per category on the dashboard)");
        Map<String, Integer> perCategory = new TreeMap<>(Map.of("Theatre", 40, "Comedy", 95, "Music", 310));
        out.println(perCategory + "  firstKey=" + ((TreeMap<String, Integer>) perCategory).firstKey());

        section(out, "Queue (FIFO) - booking requests served in arrival order");
        Queue<String> requests = new ArrayDeque<>();
        requests.offer("REQ-1");
        requests.offer("REQ-2");
        requests.offer("REQ-3");
        out.println("poll() -> " + requests.poll() + ", remaining " + requests);

        section(out, "Deque - stack (LIFO) and queue in one: an 'undo' history");
        Deque<String> undo = new ArrayDeque<>();
        undo.push("set price 799");
        undo.push("set capacity 200");
        out.println("pop() -> " + undo.pop() + " (last action undone first)");

        section(out, "PriorityQueue - always hands out the highest priority first");
        PriorityQueue<TicketRequest> pq = new PriorityQueue<>(
                Comparator.comparingInt(TicketRequest::priority).reversed().thenComparing(Comparator.naturalOrder()));
        pq.add(new TicketRequest("Regular Ravi", 2, 1, 100));
        pq.add(new TicketRequest("VIP Meera", 4, 5, 300));
        pq.add(new TicketRequest("Regular Sara", 1, 1, 50));
        StringBuilder order = new StringBuilder();
        while (!pq.isEmpty()) {
            order.append(pq.poll().customer()).append(pq.isEmpty() ? "" : " -> ");
        }
        out.println(order);

        section(out, "Iterator - safe removal while looping");
        List<Integer> quantities = new ArrayList<>(List.of(2, 0, 4, 0, 1));
        Iterator<Integer> it = quantities.iterator();
        while (it.hasNext()) {
            if (it.next() == 0) {
                it.remove();               // removing via the list inside a for-each would throw ConcurrentModificationException
            }
        }
        out.println("invalid zero quantities removed -> " + quantities);

        section(out, "ListIterator - walk both ways and edit in place");
        List<String> titles = new ArrayList<>(List.of("indie night", "jazz brunch"));
        ListIterator<String> li = titles.listIterator();
        while (li.hasNext()) {
            li.set(li.next().toUpperCase());
        }
        StringBuilder backwards = new StringBuilder();
        while (li.hasPrevious()) {
            backwards.append(li.previous()).append(' ');
        }
        out.println("upper-cased " + titles + ", backwards: " + backwards.toString().trim());

        section(out, "Comparable (natural order) vs Comparator (any order you choose)");
        List<TicketRequest> byTime = new ArrayList<>(List.of(
                new TicketRequest("C", 2, 1, 30), new TicketRequest("A", 1, 2, 10), new TicketRequest("B", 3, 1, 20)));
        Collections.sort(byTime);    // uses compareTo
        out.println("natural (time): " + byTime.stream().map(TicketRequest::customer).toList());
        byTime.sort(Comparator.comparingInt(TicketRequest::quantity).reversed());
        out.println("comparator (quantity desc): " + byTime.stream().map(TicketRequest::customer).toList());
        listings.sort(Comparator.comparing(ShowListing::price));
        out.println("listings by price: " + listings.stream().map(ShowListing::title).toList());

        section(out, "Collections utility methods");
        List<Integer> prices = new ArrayList<>(List.of(799, 1200, 499, 1500));
        out.println("max=" + Collections.max(prices) + " min=" + Collections.min(prices));
        Collections.sort(prices);
        out.println("sorted=" + prices + " binarySearch(1200)=" + Collections.binarySearch(prices, 1200));
        Collections.reverse(prices);
        out.println("reversed=" + prices + " frequency(499)=" + Collections.frequency(prices, 499));
        List<String> readOnly = Collections.unmodifiableList(new ArrayList<>(List.of("Music", "Comedy")));
        try {
            readOnly.add("Hack");
        } catch (UnsupportedOperationException e) {
            out.println("unmodifiableList rejects add() -> UnsupportedOperationException");
        }
    }
}
