package com.bookmatrix.demo;

import com.bookmatrix.exception.CsvFormatException;
import com.bookmatrix.fileio.CsvParser;
import com.bookmatrix.fileio.CsvWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Byte streams, character streams, buffering, StreamTokenizer and CSV round-trips, in a temp folder. */
public class FileHandlingDemo implements ConceptDemo {

    record Attendee(String name, String note, int tickets) { }

    @Override public String name() { return "files"; }
    @Override public String title() { return "File handling and CSV"; }
    @Override public String topics() { return "Byte streams, Character streams, FileInputStream, FileOutputStream, FileReader, FileWriter, Buffered streams, StreamTokenizer, File reading, File writing, CSV, Collection import/export, Resource management"; }

    @Override
    public void run(DemoContext ctx) throws IOException {
        PrintStream out = ctx.out();
        File dir = Files.createTempDirectory("bookmatrix-files-demo").toFile();
        out.println("Working in temporary folder: " + dir.getAbsolutePath());
        try {
            section(out, "Character streams: FileWriter + BufferedWriter, FileReader + BufferedReader");
            File notes = new File(dir, "event-notes.txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(notes, StandardCharsets.UTF_8))) {
                writer.write("Indie Night - doors open 7 PM");
                writer.newLine();
                writer.write("Bring your booking reference");
                writer.newLine();
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(notes, StandardCharsets.UTF_8))) {
                String line;
                int n = 1;
                while ((line = reader.readLine()) != null) {
                    out.println("  line " + n++ + ": " + line);
                }
            }

            section(out, "Byte streams: FileOutputStream / FileInputStream (raw bytes, e.g. images)");
            File original = new File(dir, "ticket.bin");
            byte[] bytes = new byte[1024];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (i % 256);
            }
            try (OutputStream os = new FileOutputStream(original)) {
                os.write(bytes);
            }
            File copy = new File(dir, "ticket-copy.bin");
            long copied = 0;
            try (InputStream in = new BufferedInputStream(new FileInputStream(original));
                 OutputStream os = new BufferedOutputStream(new FileOutputStream(copy))) {
                byte[] buffer = new byte[256];
                int read;
                while ((read = in.read(buffer)) != -1) {     // -1 means end of file
                    os.write(buffer, 0, read);
                    copied += read;
                }
            }
            out.println("copied " + copied + " bytes with buffered byte streams; sizes equal: "
                    + (original.length() == copy.length()));

            section(out, "StreamTokenizer: split text into words and numbers");
            try (Reader r = new FileReader(writeText(dir, "order.txt", "Order 3 tickets at 799.50 each for Indie Night"),
                    StandardCharsets.UTF_8)) {
                StreamTokenizer tokenizer = new StreamTokenizer(r);
                List<String> words = new ArrayList<>();
                List<Double> numbers = new ArrayList<>();
                while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
                    if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
                        words.add(tokenizer.sval);
                    } else if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
                        numbers.add(tokenizer.nval);
                    }
                }
                out.println("words=" + words);
                out.println("numbers=" + numbers);
            }

            section(out, "CSV export of a collection (commas, quotes, newlines, empty values)");
            List<Attendee> attendees = List.of(
                    new Attendee("Asha Rao", "Front row, please", 2),
                    new Attendee("Ravi \"RK\" Kumar", "", 1),
                    new Attendee("Meera", "Line one\nLine two", 4));
            File csv = new File(dir, "attendees.csv");
            try (Writer w = new BufferedWriter(new FileWriter(csv, StandardCharsets.UTF_8))) {
                CsvWriter.writeRow(w, List.of("name", "note", "tickets"));
                for (Attendee a : attendees) {
                    CsvWriter.writeRow(w, List.of(a.name(), a.note(), a.tickets()));
                }
            }
            out.println("file contents:");
            out.print(Files.readString(csv.toPath()).replace("\r\n", "\n").indent(2));

            section(out, "CSV import back into a collection");
            List<Attendee> back = new ArrayList<>();
            try (Reader r = new BufferedReader(new FileReader(csv, StandardCharsets.UTF_8))) {
                List<CsvParser.CsvRecord> records = CsvParser.parse(r);
                for (CsvParser.CsvRecord rec : records.subList(1, records.size())) {
                    back.add(new Attendee(rec.fields().get(0), rec.fields().get(1), Integer.parseInt(rec.fields().get(2))));
                }
            } catch (CsvFormatException e) {
                out.println("CSV error: " + e.getMessage());
            }
            out.println("round trip identical: " + back.equals(attendees));

            section(out, "Errors handled safely");
            try (Reader r = new FileReader(new File(dir, "missing.csv"))) {
                out.println(r.read());
            } catch (FileNotFoundException e) {
                out.println("FileNotFoundException: missing.csv does not exist");
            }
            try {
                CsvParser.parse(new java.io.StringReader("a,b\n\"never closed,1\n"));
            } catch (CsvFormatException e) {
                out.println("CsvFormatException: " + e.getMessage());
            }
        } finally {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    Files.deleteIfExists(f.toPath());
                }
            }
            Files.deleteIfExists(dir.toPath());
            out.println();
            out.println("Temporary files deleted.");
        }
    }

    private static File writeText(File dir, String name, String text) throws IOException {
        File f = new File(dir, name);
        try (Writer w = new FileWriter(f, StandardCharsets.UTF_8)) {
            w.write(text);
        }
        return f;
    }
}
