package com.bookmatrix.demo;

import com.bookmatrix.model.BaseEntity;
import com.bookmatrix.model.Booking;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.User;

import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Classes, objects, constructors, this, static, encapsulation, inheritance, polymorphism, abstraction, interfaces. */
public class OopDemo implements ConceptDemo {

    /** A tiny class used only to show "static": one counter shared by ALL Ticket objects. */
    static class Ticket {
        private static int issued = 0;          // belongs to the class
        private final int number;               // belongs to each object

        Ticket() {
            issued++;
            this.number = issued;
        }

        static int issuedSoFar() {
            return issued;
        }

        int number() {
            return number;
        }
    }

    @Override public String name() { return "oop"; }
    @Override public String title() { return "Object-oriented programming with BookMatrix classes"; }
    @Override public String topics() { return "Classes, Objects, Methods, Constructors, this, static, Encapsulation, Inheritance, Polymorphism, Abstraction, Interfaces, Overriding"; }

    @Override
    public void run(DemoContext ctx) {
        PrintStream out = ctx.out();

        section(out, "Classes, objects and constructors");
        User user = new User("Asha Rao", "asha@example.com", "$2a$10$hash-not-a-real-password");
        Event event = new Event();
        event.setTitle("Indie Night Live");
        event.setVenueName("Blue Frog");
        event.setCity("Pune");
        event.setStartsAt(LocalDateTime.now().plusDays(10));
        event.setEndsAt(LocalDateTime.now().plusDays(10).plusHours(3));
        event.setTicketPrice(new BigDecimal("799.00"));
        event.setCapacity(200);
        event.setTicketsSold(150);
        Booking booking = new Booking();
        booking.setReference("BM7K2Q9XWD");
        booking.setQuantity(2);
        out.println("new User(name, email, hash) -> constructor stored the values using this.fullName = fullName");
        out.println("user.getFullName() = " + user.getFullName());

        section(out, "Encapsulation");
        out.println("Fields are private. capacity=" + event.getCapacity() + ", sold=" + event.getTicketsSold());
        out.println("Available tickets are COMPUTED by a method, never set directly: " + event.getAvailableTickets());

        section(out, "Inheritance + polymorphism (method overriding)");
        List<BaseEntity> entities = new ArrayList<>();
        entities.add(user);       // a User IS-A BaseEntity
        entities.add(event);      // an Event IS-A BaseEntity
        entities.add(booking);    // a Booking IS-A BaseEntity
        for (BaseEntity entity : entities) {
            // same call, different behaviour: Java picks the overriding describe() at run time
            out.printf("%-8s describe() -> %s%n", entity.getClass().getSimpleName(), entity.describe());
        }

        section(out, "Abstraction");
        out.println("BaseEntity is abstract? " + Modifier.isAbstract(BaseEntity.class.getModifiers()));
        out.println("So 'new BaseEntity()' does not compile; only concrete subclasses can be created.");

        section(out, "Interfaces");
        out.println("Event implements Comparable<Event>: " + (event instanceof Comparable<?>));
        out.println("This demo implements ConceptDemo: " + (this instanceof ConceptDemo));
        Event earlier = new Event();
        earlier.setTitle("Morning Yoga");
        earlier.setStartsAt(event.getStartsAt().minusDays(2));
        out.println("event.compareTo(earlier) = " + event.compareTo(earlier) + "  (positive: event comes later)");

        section(out, "static vs instance");
        Ticket first = new Ticket();
        Ticket second = new Ticket();
        out.println("first.number()=" + first.number() + ", second.number()=" + second.number()
                + ", Ticket.issuedSoFar()=" + Ticket.issuedSoFar() + " (shared by the class)");
        out.println("Explanation: 'issued' is static, so there is ONE copy for the class; 'number' is per object.");
    }
}
