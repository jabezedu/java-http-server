package com.netapps.httpserver.http;

import com.netapps.httpserver.util.DateUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an HTTP response, managing status, headers, standard headers,
 * and streaming output (in-memory bytes or 8 KB chunked file ranges).
 */
public class HttpResponse {

    private static final String SERVER_NAME = "NetApps-HttpServer/1.1";
    private static final int CHUNK_SIZE = 8192; // 8 KB streaming chunks

    private StatusCode status;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] bodyBytes;
    private File file;
    private long rangeStart = 0;
    private long rangeEnd = -1; // -1 indicates full file length

    /**
     * Constructs a response with a status code.
     *
     * @param status HTTP status code
     */
    public HttpResponse(StatusCode status) {
        this.status = status;
        // Standard default headers
        setHeader("Date", DateUtil.formatHttpDate(System.currentTimeMillis()));
        setHeader("Server", SERVER_NAME);
    }

    public StatusCode getStatus() {
        return status;
    }

    public void setStatus(StatusCode status) {
        this.status = status;
    }

    public HttpResponse setHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public HttpResponse setBody(byte[] bytes) {
        this.bodyBytes = (bytes != null) ? bytes : new byte[0];
        this.file = null;
        setHeader("Content-Length", String.valueOf(this.bodyBytes.length));
        return this;
    }

    public HttpResponse setBody(String text, String contentType) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        setHeader("Content-Type", contentType);
        return setBody(bytes);
    }

    public HttpResponse setFile(File file, String contentType) {
        this.file = file;
        this.bodyBytes = null;
        this.rangeStart = 0;
        this.rangeEnd = file.length() - 1;
        setHeader("Content-Type", contentType);
        setHeader("Content-Length", String.valueOf(file.length()));
        return this;
    }

    public HttpResponse setFileRange(File file, String contentType, long start, long end, long totalLength) {
        this.file = file;
        this.bodyBytes = null;
        this.rangeStart = start;
        this.rangeEnd = end;
        long contentLength = (end - start) + 1;
        setHeader("Content-Type", contentType);
        setHeader("Content-Length", String.valueOf(contentLength));
        setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalLength);
        return this;
    }

    /**
     * Serializes headers and streams payload over the provided network stream.
     *
     * @param out      socket output stream
     * @param headOnly true if HEAD method (headers only, omit body)
     * @throws IOException on network write failure
     */
    public void writeTo(OutputStream out, boolean headOnly) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status.getCode()).append(" ").append(status.getReasonPhrase()).append("\r\n");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        sb.append("\r\n");

        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));

        if (headOnly || status == StatusCode.NO_CONTENT || status == StatusCode.NOT_MODIFIED) {
            out.flush();
            return;
        }

        if (bodyBytes != null && bodyBytes.length > 0) {
            out.write(bodyBytes);
        } else if (file != null && file.exists()) {
            streamFile(out);
        }

        out.flush();
    }

    private void streamFile(OutputStream out) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            if (rangeStart > 0) {
                long skipped = fis.skip(rangeStart);
                if (skipped != rangeStart) {
                    throw new IOException("Failed to skip to range start: " + rangeStart);
                }
            }

            long remaining = (rangeEnd >= rangeStart) ? (rangeEnd - rangeStart + 1) : file.length();
            byte[] buffer = new byte[CHUNK_SIZE];

            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = fis.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    // Static Factory Helpers

    public static HttpResponse ok() {
        return new HttpResponse(StatusCode.OK);
    }

    public static HttpResponse json(String jsonText) {
        HttpResponse res = new HttpResponse(StatusCode.OK);
        res.setBody(jsonText, "application/json; charset=UTF-8");
        return res;
    }

    public static HttpResponse html(StatusCode status, String htmlText) {
        HttpResponse res = new HttpResponse(status);
        res.setBody(htmlText, "text/html; charset=UTF-8");
        return res;
    }

    public static HttpResponse status(StatusCode status) {
        return new HttpResponse(status);
    }
}
