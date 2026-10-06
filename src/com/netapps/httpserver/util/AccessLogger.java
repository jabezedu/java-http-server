package com.netapps.httpserver.util;

import com.netapps.httpserver.http.HttpRequest;
import com.netapps.httpserver.http.HttpResponse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Thread-safe logging engine writing HTTP requests in Common Log Format (CLF)
 * to ./logs/access.log, errors to ./logs/error.log, and emitting console summaries.
 */
public final class AccessLogger {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter CLF_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.US).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CONSOLE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static PrintWriter accessWriter;
    private static PrintWriter errorWriter;

    static {
        try {
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            accessWriter = new PrintWriter(new BufferedWriter(new FileWriter(new File(logsDir, "access.log"), true)), true);
            errorWriter = new PrintWriter(new BufferedWriter(new FileWriter(new File(logsDir, "error.log"), true)), true);
        } catch (IOException e) {
            System.err.println("[AccessLogger] Warning: Could not initialize log files: " + e.getMessage());
        }
    }

    private AccessLogger() {
    }

    /**
     * Records a completed HTTP request and response exchange.
     *
     * @param request  HttpRequest model
     * @param response HttpResponse model
     * @param durationMs request processing time in milliseconds
     */
    public static void logRequest(HttpRequest request, HttpResponse response, long durationMs) {
        String clientIp = (request != null) ? request.getRemoteAddress() : "127.0.0.1";
        String requestLine = (request != null)
                ? (request.getRawMethod() + " " + request.getRawUri() + " " + request.getHttpVersion())
                : "-";
        int statusCode = (response != null) ? response.getStatus().getCode() : 500;
        String contentLen = (response != null && response.getHeader("Content-Length") != null)
                ? response.getHeader("Content-Length")
                : "-";

        String clfDate = CLF_DATE_FORMATTER.format(Instant.now());
        String clfEntry = String.format("%s - - [%s] \"%s\" %d %s",
                clientIp, clfDate, requestLine, statusCode, contentLen);

        String consoleDate = CONSOLE_DATE_FORMATTER.format(Instant.now());
        String consoleEntry = String.format("[%s] [%s] %s -> %d (%dms)",
                consoleDate, clientIp, requestLine, statusCode, durationMs);

        synchronized (LOCK) {
            System.out.println(consoleEntry);
            if (accessWriter != null) {
                accessWriter.println(clfEntry);
            }
        }
    }

    /**
     * Records an error message to ./logs/error.log and standard error.
     *
     * @param message error description
     * @param cause   optional exception cause
     */
    public static void logError(String message, Throwable cause) {
        String timestamp = CONSOLE_DATE_FORMATTER.format(Instant.now());
        String logLine = String.format("[%s] [ERROR] %s%s",
                timestamp, message, (cause != null ? (" (" + cause.getMessage() + ")") : ""));

        synchronized (LOCK) {
            System.err.println(logLine);
            if (errorWriter != null) {
                errorWriter.println(logLine);
                if (cause != null) {
                    cause.printStackTrace(errorWriter);
                }
            }
        }
    }

    /**
     * Flushes and terminates open log file handles.
     */
    public static void close() {
        synchronized (LOCK) {
            if (accessWriter != null) {
                accessWriter.flush();
                accessWriter.close();
                accessWriter = null;
            }
            if (errorWriter != null) {
                errorWriter.flush();
                errorWriter.close();
                errorWriter = null;
            }
        }
    }
}
