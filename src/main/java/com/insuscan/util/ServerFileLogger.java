package com.insuscan.util;

import org.springframework.stereotype.Component;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes logs to a local file "insuscan_server_log.txt" for easy retrieval/debugging.
 * This is IN ADDITION to standard SLF4J logging.
 */
@Component
public class ServerFileLogger {

    private static final String LOG_FILE = "insuscan_server_log.txt";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public synchronized void append(String message) {
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(FMT);
            out.println(String.format("[%s] %s", timestamp, message));
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }
    
    public void log(String format, Object... args) {
        String msg = String.format(format.replace("{}", "%s"), args);
        append(msg);
    }
}
