package com.netapps.httpserver.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Utility for formatting and parsing HTTP dates according to RFC 1123.
 */
public final class DateUtil {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);

    private DateUtil() {
    }

    /**
     * Formats an epoch millisecond timestamp into an RFC 1123 date string.
     *
     * @param epochMillis milliseconds since epoch
     * @return formatted HTTP date string
     */
    public static String formatHttpDate(long epochMillis) {
        return HTTP_DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Parses an RFC 1123 HTTP date header (e.g. If-Modified-Since).
     *
     * @param dateStr raw date string
     * @return epoch milliseconds or -1 if invalid
     */
    public static long parseHttpDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return -1;
        }
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr.trim(), HTTP_DATE_FORMATTER);
            return zdt.toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }
}
