package com.netapps.httpserver.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Hand-crafted RFC 7230 compliant HTTP stream parser. Parses request lines,
 * case-insensitive header maps, validates limits, and handles Content-Length payloads.
 */
public class HttpParser {

    private static final int MAX_HEADER_SIZE = 16384; // 16 KB max headers safety limit

    /**
     * Parses an incoming HTTP request from a socket input stream.
     *
     * @param in              socket stream
     * @param remoteAddr      client IP address
     * @param maxBodySize     maximum allowed request body size in bytes
     * @return fully parsed HttpRequest instance
     * @throws HttpException on protocol, validation, or size errors
     * @throws IOException   on socket timeout or connection failures
     */
    public static HttpRequest parse(InputStream in, String remoteAddr, long maxBodySize)
            throws HttpException, IOException {

        // 1. Read request line
        String requestLine = readLine(in, MAX_HEADER_SIZE);
        if (requestLine == null || requestLine.trim().isEmpty()) {
            return null; // Connection closed or empty ping
        }

        String[] parts = requestLine.split("\\s+");
        if (parts.length != 3) {
            throw new HttpException(StatusCode.BAD_REQUEST, "Malformed HTTP request line: " + requestLine);
        }

        String rawMethod = parts[0];
        String rawUri = parts[1];
        String httpVersion = parts[2].toUpperCase();

        // Validate HTTP version
        if (!"HTTP/1.0".equals(httpVersion) && !"HTTP/1.1".equals(httpVersion)) {
            throw new HttpException(StatusCode.HTTP_VERSION_NOT_SUPPORTED,
                    "HTTP version not supported: " + httpVersion + ". Only HTTP/1.0 and HTTP/1.1 are accepted.");
        }

        HttpMethod method = HttpMethod.fromString(rawMethod);

        // 2. Read headers
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int totalHeaderBytes = requestLine.length();

        while (true) {
            String headerLine = readLine(in, MAX_HEADER_SIZE - totalHeaderBytes);
            if (headerLine == null || headerLine.isEmpty()) {
                break; // Blank line marks end of headers
            }
            totalHeaderBytes += headerLine.length();
            if (totalHeaderBytes > MAX_HEADER_SIZE) {
                throw new HttpException(StatusCode.BAD_REQUEST, "Total request headers exceed maximum allowed limit");
            }

            int colonIdx = headerLine.indexOf(':');
            if (colonIdx <= 0) {
                throw new HttpException(StatusCode.BAD_REQUEST, "Malformed HTTP header: " + headerLine);
            }

            String headerName = headerLine.substring(0, colonIdx).trim();
            String headerValue = headerLine.substring(colonIdx + 1).trim();
            headers.put(headerName, headerValue);
        }

        // 3. Inspect headers for chunked transfer or payload sizes
        String transferEncoding = headers.get("Transfer-Encoding");
        if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
            throw new HttpException(StatusCode.NOT_IMPLEMENTED, "Chunked Transfer-Encoding is not supported");
        }

        // 4. Parse request body if Content-Length is provided
        byte[] body = new byte[0];
        String contentLengthStr = headers.get("Content-Length");
        if (contentLengthStr != null) {
            long contentLength;
            try {
                contentLength = Long.parseLong(contentLengthStr.trim());
            } catch (NumberFormatException e) {
                throw new HttpException(StatusCode.BAD_REQUEST, "Invalid Content-Length value: " + contentLengthStr);
            }

            if (contentLength < 0) {
                throw new HttpException(StatusCode.BAD_REQUEST, "Negative Content-Length not permitted");
            }

            if (contentLength > maxBodySize) {
                throw new HttpException(StatusCode.PAYLOAD_TOO_LARGE,
                        "Request entity too large: " + contentLength + " bytes exceeds limit of " + maxBodySize + " bytes");
            }

            if (contentLength > 0) {
                body = new byte[(int) contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = in.read(body, totalRead, (int) (contentLength - totalRead));
                    if (read == -1) {
                        throw new HttpException(StatusCode.BAD_REQUEST,
                                "Premature end of stream while reading body: expected " + contentLength + " bytes, read " + totalRead);
                    }
                    totalRead += read;
                }
            }
        }

        // 5. Parse path and query
        String path = rawUri;
        String query = "";
        int queryIdx = rawUri.indexOf('?');
        if (queryIdx >= 0) {
            path = rawUri.substring(0, queryIdx);
            query = (queryIdx < rawUri.length() - 1) ? rawUri.substring(queryIdx + 1) : "";
        }

        return new HttpRequest(method, rawMethod, rawUri, path, query, httpVersion, headers, body, remoteAddr);
    }

    /**
     * Reads a single line terminated by CRLF (\r\n) or LF (\n) directly from the stream.
     */
    private static String readLine(InputStream in, int maxLen) throws HttpException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int prev = -1;
        int count = 0;

        try {
            while (true) {
                int curr = in.read();
                if (curr == -1) {
                    if (baos.size() == 0) {
                        return null; // Clean EOF
                    }
                    break;
                }
                count++;
                if (count > maxLen) {
                    throw new HttpException(StatusCode.BAD_REQUEST, "Line length exceeded maximum buffer limit");
                }

                if (curr == '\n') {
                    // Strips potential preceding \r
                    byte[] bytes = baos.toByteArray();
                    int len = bytes.length;
                    if (len > 0 && bytes[len - 1] == '\r') {
                        return new String(bytes, 0, len - 1, StandardCharsets.UTF_8);
                    }
                    return new String(bytes, 0, len, StandardCharsets.UTF_8);
                }

                baos.write(curr);
                prev = curr;
            }
        } catch (SocketTimeoutException e) {
            throw new HttpException(StatusCode.REQUEST_TIMEOUT, "Socket read timeout while parsing request", e);
        }

        byte[] bytes = baos.toByteArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
