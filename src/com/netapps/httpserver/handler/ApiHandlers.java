package com.netapps.httpserver.handler;

import com.netapps.httpserver.config.ServerConfig;
import com.netapps.httpserver.http.HttpException;
import com.netapps.httpserver.http.HttpMethod;
import com.netapps.httpserver.http.HttpRequest;
import com.netapps.httpserver.http.HttpResponse;
import com.netapps.httpserver.http.StatusCode;
import com.netapps.httpserver.util.PathUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handlers servicing dynamic REST API endpoints, file uploads, health probes,
 * and custom styled error pages.
 */
public final class ApiHandlers {

    private static final long START_TIME_MILLIS = System.currentTimeMillis();
    private static final AtomicLong TOTAL_REQUESTS = new AtomicLong(0);
    private static final AtomicLong ACTIVE_CONNECTIONS = new AtomicLong(0);

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter LOCAL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ApiHandlers() {
    }

    public static void incrementRequestCount() {
        TOTAL_REQUESTS.incrementAndGet();
    }

    public static void incrementActiveConnections() {
        ACTIVE_CONNECTIONS.incrementAndGet();
    }

    public static void decrementActiveConnections() {
        ACTIVE_CONNECTIONS.decrementAndGet();
    }

    /**
     * GET /api/time: Returns current server time in UTC and local zone formatted as JSON.
     */
    public static HttpResponse handleTime(HttpRequest request) {
        long now = System.currentTimeMillis();
        Instant instant = Instant.ofEpochMilli(now);
        String utc = ISO_FORMATTER.format(instant);
        String local = LOCAL_FORMATTER.format(instant);

        String json = "{\n"
                + "  \"timestamp\": " + now + ",\n"
                + "  \"utc\": \"" + utc + "\",\n"
                + "  \"local\": \"" + local + "\"\n"
                + "}";
        return HttpResponse.json(json);
    }

    /**
     * GET /api/info: Returns server uptime, total processed requests, and active connections.
     */
    public static HttpResponse handleInfo(HttpRequest request) {
        long uptimeSec = (System.currentTimeMillis() - START_TIME_MILLIS) / 1000;
        String json = "{\n"
                + "  \"serverName\": \"NetApps-HttpServer/1.1\",\n"
                + "  \"uptimeSeconds\": " + uptimeSec + ",\n"
                + "  \"totalRequests\": " + TOTAL_REQUESTS.get() + ",\n"
                + "  \"activeConnections\": " + Math.max(0, ACTIVE_CONNECTIONS.get()) + "\n"
                + "}";
        return HttpResponse.json(json);
    }

    /**
     * POST /api/echo: Echoes received request headers and body payload as JSON.
     */
    public static HttpResponse handleEcho(HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"method\": \"").append(request.getRawMethod()).append("\",\n");
        sb.append("  \"path\": \"").append(escapeJson(request.getPath())).append("\",\n");
        sb.append("  \"query\": \"").append(escapeJson(request.getQuery())).append("\",\n");
        sb.append("  \"headers\": {\n");

        boolean first = true;
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            sb.append("    \"").append(escapeJson(header.getKey())).append("\": \"")
              .append(escapeJson(header.getValue())).append("\"");
            first = false;
        }
        sb.append("\n  },\n");

        sb.append("  \"bodyLength\": ").append(request.getBody().length).append(",\n");
        sb.append("  \"body\": \"").append(escapeJson(request.getBodyAsString())).append("\"\n");
        sb.append("}");

        return HttpResponse.json(sb.toString());
    }

    /**
     * POST /upload: Saves raw body bytes into ./www/uploads/<X-Filename> and returns 201 Created.
     */
    public static HttpResponse handleUpload(HttpRequest request, ServerConfig config) throws IOException {
        String rawFilename = request.getHeader("X-Filename");
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            rawFilename = "upload_" + System.currentTimeMillis() + ".bin";
        }
        String safeName = PathUtil.sanitizeFilename(rawFilename);

        File uploadDir = new File(config.getUploadDir());
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        File targetFile = new File(uploadDir, safeName);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(request.getBody());
        }

        String location = "/uploads/" + safeName;
        HttpResponse res = new HttpResponse(StatusCode.CREATED);
        res.setHeader("Location", location);
        String json = "{\n  \"status\": \"created\",\n  \"location\": \"" + location + "\",\n  \"size\": "
                + targetFile.length() + "\n}";
        res.setBody(json, "application/json; charset=UTF-8");
        return res;
    }

    /**
     * DELETE /uploads/<name>: Removes target uploaded file.
     */
    public static HttpResponse handleDeleteUpload(HttpRequest request, ServerConfig config) throws HttpException, IOException {
        String path = request.getPath();
        String prefix = "/uploads/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            throw new HttpException(StatusCode.BAD_REQUEST, "Filename not specified in path");
        }

        String filename = path.substring(prefix.length());
        String safeName = PathUtil.sanitizeFilename(filename);

        File uploadDir = new File(config.getUploadDir());
        File targetFile = PathUtil.resolveSafeFile(uploadDir, safeName);

        if (!targetFile.exists() || !targetFile.isFile()) {
            throw new HttpException(StatusCode.NOT_FOUND, "Uploaded file not found: " + safeName);
        }

        boolean deleted = targetFile.delete();
        if (deleted) {
            HttpResponse res = new HttpResponse(StatusCode.OK);
            res.setBody("{\n  \"status\": \"deleted\",\n  \"file\": \"" + safeName + "\"\n}", "application/json");
            return res;
        } else {
            throw new HttpException(StatusCode.INTERNAL_SERVER_ERROR, "Failed to delete file: " + safeName);
        }
    }

    /**
     * GET /health: Health probe endpoint.
     */
    public static HttpResponse handleHealth(HttpRequest request) {
        return HttpResponse.json("{\"status\":\"ok\"}");
    }

    /**
     * Renders a custom styled 404 Not Found HTML page.
     */
    public static HttpResponse build404Page(String resource) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>404 Not Found</title>"
                + "<style>body{font-family:sans-serif;text-align:center;padding:4rem;background:#f8fafc;color:#0f172a;}"
                + "h1{font-size:3rem;color:#dc2626;margin-bottom:0.5rem;}p{font-size:1.1rem;color:#475569;}"
                + "a{color:#2563eb;text-decoration:none;font-weight:600;}</style></head><body>"
                + "<h1>404 Not Found</h1>"
                + "<p>The requested resource <code>" + escapeJson(resource) + "</code> does not exist on this server.</p>"
                + "<p><a href=\"/\">&larr; Return to Home</a></p>"
                + "<hr style=\"margin-top:2rem;border:0;border-top:1px solid #e2e8f0;\"/>"
                + "<p><small>NetApps HTTP/1.1 Engine</small></p>"
                + "</body></html>";
        return HttpResponse.html(StatusCode.NOT_FOUND, html);
    }

    /**
     * Renders a custom styled 500 Internal Server Error HTML page.
     */
    public static HttpResponse build500Page(String message) {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>500 Internal Server Error</title>"
                + "<style>body{font-family:sans-serif;text-align:center;padding:4rem;background:#f8fafc;color:#0f172a;}"
                + "h1{font-size:3rem;color:#dc2626;margin-bottom:0.5rem;}p{font-size:1.1rem;color:#475569;}"
                + "pre{background:#0f172a;color:#f87171;padding:1rem;border-radius:6px;display:inline-block;max-width:80%;text-align:left;}"
                + "a{color:#2563eb;text-decoration:none;font-weight:600;}</style></head><body>"
                + "<h1>500 Internal Server Error</h1>"
                + "<p>The server encountered an unexpected condition while processing your request.</p>"
                + "<pre>" + escapeJson(message) + "</pre><br/><br/>"
                + "<p><a href=\"/\">&larr; Return to Home</a></p>"
                + "<hr style=\"margin-top:2rem;border:0;border-top:1px solid #e2e8f0;\"/>"
                + "<p><small>NetApps HTTP/1.1 Engine</small></p>"
                + "</body></html>";
        return HttpResponse.html(StatusCode.INTERNAL_SERVER_ERROR, html);
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
