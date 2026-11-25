package com.bookmatrix.demo;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw JDBC from first principles: DriverManager, Connection, Statement, PreparedStatement,
 * executeQuery/executeUpdate, ResultSet, typed getters, object mapping, transactions.
 * Works on a TEMPORARY table, which MySQL deletes automatically when the connection closes,
 * so it never touches real BookMatrix data.
 */
public class JdbcDemo implements ConceptDemo {

    /** Java object that one database row is mapped into. */
    public record DemoTicket(int id, String holder, int seats, BigDecimal price) { }

    @Override public String name() { return "jdbc"; }
    @Override public String title() { return "JDBC step by step"; }
    @Override public String topics() { return "JDBC architecture, Drivers, Database URL, DriverManager, Connection, Statement, PreparedStatement, executeQuery, executeUpdate, Parameter binding, CRUD, ResultSet, Cursor iteration, Typed getters, Object mapping, Transactions, Commit, Rollback, Resource management"; }

    @Override
    public void run(DemoContext ctx) throws SQLException {
        PrintStream out = ctx.out();
        if (!ctx.hasDatabase()) {
            out.println("NOT RUN: no database settings (DB_URL/DB_USERNAME/DB_PASSWORD) were found.");
            return;
        }

        section(out, "JDBC architecture");
        out.println("Java code -> JDBC API (java.sql interfaces) -> DriverManager -> MySQL driver (Type 4,");
        out.println("pure Java) -> network -> MySQL server. Our code only uses the interfaces.");
        out.println("Database URL: " + ctx.dbUrl().replaceAll("\\?.*", "") + "   (format jdbc:mysql://host:port/database)");

        // try-with-resources: the Connection is closed automatically, even if an exception happens
        try (Connection conn = DriverManager.getConnection(ctx.dbUrl(), ctx.dbUser(), ctx.dbPassword())) {
            DatabaseMetaData meta = conn.getMetaData();
            out.println("Connected to " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
            out.println("Driver: " + meta.getDriverName() + " " + meta.getDriverVersion()
                    + " (JDBC " + meta.getJDBCMajorVersion() + "." + meta.getJDBCMinorVersion() + ")");

            section(out, "Statement: fixed SQL (no user input) - create a temporary table");
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TEMPORARY TABLE demo_tickets ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, holder VARCHAR(50) NOT NULL, "
                        + "seats INT NOT NULL, price DECIMAL(8,2) NOT NULL)");
                out.println("CREATE TEMPORARY TABLE demo_tickets ... done");
            }

            section(out, "CREATE: PreparedStatement + parameter binding + executeUpdate()");
            String insert = "INSERT INTO demo_tickets (holder, seats, price) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                Object[][] rows = {{"Asha", 2, "799.00"}, {"Ravi", 4, "799.00"}, {"Meera", 1, "1499.50"}};
                for (Object[] row : rows) {
                    ps.setString(1, (String) row[0]);     // 1st ?
                    ps.setInt(2, (Integer) row[1]);       // 2nd ?
                    ps.setBigDecimal(3, new BigDecimal((String) row[2]));
                    int changed = ps.executeUpdate();
                    out.println("inserted " + row[0] + " -> executeUpdate() returned " + changed + " row(s)");
                }
            }

            section(out, "READ: executeQuery() + ResultSet cursor + typed getters + object mapping");
            List<DemoTicket> tickets = readAll(conn);
            tickets.forEach(t -> out.println("  " + t));

            section(out, "UPDATE and DELETE");
            try (PreparedStatement ps = conn.prepareStatement("UPDATE demo_tickets SET seats = seats + ? WHERE holder = ?")) {
                ps.setInt(1, 1);
                ps.setString(2, "Asha");
                out.println("UPDATE changed " + ps.executeUpdate() + " row(s)");
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM demo_tickets WHERE holder = ?")) {
                ps.setString(1, "Meera");
                out.println("DELETE removed " + ps.executeUpdate() + " row(s)");
            }
            out.println("now: " + readAll(conn));

            section(out, "Why PreparedStatement stops SQL injection");
            String attack = "x' OR '1'='1";
            out.println("Concatenated SQL would be: SELECT * FROM demo_tickets WHERE holder = '" + attack + "'  <- matches EVERY row");
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM demo_tickets WHERE holder = ?")) {
                ps.setString(1, attack);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    out.println("With a bound parameter it is just text -> rows matched: " + rs.getInt(1));
                }
            }

            section(out, "TRANSACTIONS: commit");
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                bindInsert(ps, "Group booking A", 3);
                bindInsert(ps, "Group booking B", 3);
                conn.commit();
                out.println("2 inserts committed together -> rows now: " + count(conn));
            }

            section(out, "TRANSACTIONS: rollback when something fails half-way");
            int before = count(conn);
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                bindInsert(ps, "Will be undone", 2);
                out.println("inserted one row inside the transaction (rows visible to us: " + count(conn) + ")");
                bindInsert(ps, null, 2);               // NOT NULL violation -> SQLException
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                out.println("second insert failed (" + e.getClass().getSimpleName() + ") -> rollback()");
                out.println("rows before=" + before + ", after rollback=" + count(conn) + "  (all-or-nothing)");
            } finally {
                conn.setAutoCommit(true);
            }

            section(out, "Reading real BookMatrix tables with a plain Statement");
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM categories), (SELECT COUNT(*) FROM events), "
                         + "(SELECT COUNT(*) FROM bookings)")) {
                if (rs.next()) {
                    out.println("categories=" + rs.getLong(1) + ", events=" + rs.getLong(2) + ", bookings=" + rs.getLong(3));
                }
            }
        }
        out.println();
        out.println("Connection closed by try-with-resources; the temporary table disappeared with it.");
    }

    private static void bindInsert(PreparedStatement ps, String holder, int seats) throws SQLException {
        ps.setString(1, holder);
        ps.setInt(2, seats);
        ps.setBigDecimal(3, new BigDecimal("500.00"));
        ps.executeUpdate();
    }

    private static int count(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM demo_tickets")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<DemoTicket> readAll(Connection conn) throws SQLException {
        List<DemoTicket> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, holder, seats, price FROM demo_tickets ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {                                   // move the cursor to the next row
                result.add(new DemoTicket(rs.getInt("id"), rs.getString("holder"),
                        rs.getInt("seats"), rs.getBigDecimal("price")));
            }
        }
        return result;
    }
}
