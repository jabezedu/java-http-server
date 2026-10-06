package com.netapps.httpserver.handler;

import com.netapps.httpserver.config.ServerConfig;
import com.netapps.httpserver.http.HttpException;
import com.netapps.httpserver.http.HttpMethod;
import com.netapps.httpserver.http.HttpRequest;
import com.netapps.httpserver.http.HttpResponse;
import com.netapps.httpserver.http.StatusCode;
import com.netapps.httpserver.util.DateUtil;
import com.netapps.httpserver.util.MimeTypes;
import com.netapps.httpserver.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Handles static file resolution, conditional caching (If-None-Match, If-Modified-Since),
 * single-range byte slicing (Range / 206), directory index fallback, and HTML directory listing.
 */
public class StaticFileHandler implements RequestHandler {

    @Override
    public HttpResponse handle(HttpRequest request, ServerConfig config) throws HttpException, Exception {
        HttpMethod method = request.getMethod();
        if (method != HttpMethod.GET && method != HttpMethod.HEAD && method != HttpMethod.OPTIONS) {
            HttpResponse res = new HttpResponse(StatusCode.METHOD_NOT_ALLOWED);
            res.setHeader("Allow", "GET, HEAD, OPTIONS");
            res.setBody("Method Not Allowed", "text/plain");
            return res;
        }

        if (method == HttpMethod.OPTIONS) {
            HttpResponse res = new HttpResponse(StatusCode.NO_CONTENT);
            res.setHeader("Allow", "GET, HEAD, OPTIONS");
            return res;
        }

        File webRoot = new File(config.getWebroot());
        String decodedPath = URLDecoder.decode(request.getPath(), StandardCharsets.UTF_8);

        File target = PathUtil.resolveSafeFile(webRoot, decodedPath);

        // If target is a directory, search for index.html
        if (target.isDirectory()) {
            File indexFile = new File(target, "index.html");
            if (indexFile.exists() && indexFile.isFile()) {
                target = indexFile;
            } else if (config.isDirectoryListingEnabled()) {
                return generateDirectoryListing(request.getPath(), target);
            } else {
                throw new HttpException(StatusCode.FORBIDDEN, "Directory listing forbidden");
            }
        }

        if (!target.exists() || !target.isFile()) {
            throw new HttpException(StatusCode.NOT_FOUND, "Resource not found: " + request.getPath());
        }

        long fileLength = target.length();
        long lastModified = target.lastModified();
        String etag = "\"" + fileLength + "-" + lastModified + "\"";
        String contentType = MimeTypes.getMimeType(target.getName());

        // Check conditional If-None-Match
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (ifNoneMatch != null && ifNoneMatch.trim().equals(etag)) {
            HttpResponse res = new HttpResponse(StatusCode.NOT_MODIFIED);
            res.setHeader("ETag", etag);
            res.setHeader("Last-Modified", DateUtil.formatHttpDate(lastModified));
            return res;
        }

        // Check conditional If-Modified-Since
        String ifModifiedSince = request.getHeader("If-Modified-Since");
        if (ifModifiedSince != null) {
            long clientTime = DateUtil.parseHttpDate(ifModifiedSince);
            if (clientTime >= 0 && (lastModified / 1000) <= (clientTime / 1000)) {
                HttpResponse res = new HttpResponse(StatusCode.NOT_MODIFIED);
                res.setHeader("ETag", etag);
                res.setHeader("Last-Modified", DateUtil.formatHttpDate(lastModified));
                return res;
            }
        }

        // Check Range request header
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && method == HttpMethod.GET) {
            return handleRangeRequest(rangeHeader, target, contentType, fileLength, etag, lastModified);
        }

        // Standard full file response
        HttpResponse response = new HttpResponse(StatusCode.OK);
        response.setHeader("ETag", etag);
        response.setHeader("Last-Modified", DateUtil.formatHttpDate(lastModified));
        response.setHeader("Accept-Ranges", "bytes");
        response.setFile(target, contentType);
        return response;
    }

    /**
     * Slices file for single-range Range request returning 206 Partial Content or 416.
     */
    private HttpResponse handleRangeRequest(String rangeHeader, File file, String contentType,
                                            long fileLength, String etag, long lastModified) {
        String spec = rangeHeader.substring("bytes=".length()).trim();
        // Support single range only
        if (spec.contains(",")) {
            // Multi-range unsupported, return full file
            HttpResponse res = new HttpResponse(StatusCode.OK);
            res.setHeader("ETag", etag);
            res.setHeader("Last-Modified", DateUtil.formatHttpDate(lastModified));
            res.setHeader("Accept-Ranges", "bytes");
            res.setFile(file, contentType);
            return res;
        }

        long start = 0;
        long end = fileLength - 1;

        try {
            int dashIdx = spec.indexOf('-');
            if (dashIdx == -1) {
                return buildRangeNotSatisfiable(fileLength);
            }
            String startPart = spec.substring(0, dashIdx).trim();
            String endPart = (dashIdx < spec.length() - 1) ? spec.substring(dashIdx + 1).trim() : "";

            if (startPart.isEmpty() && endPart.isEmpty()) {
                return buildRangeNotSatisfiable(fileLength);
            } else if (startPart.isEmpty()) {
                // Suffix byte range: bytes=-500 (last 500 bytes)
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return buildRangeNotSatisfiable(fileLength);
                }
                start = Math.max(0, fileLength - suffix);
                end = fileLength - 1;
            } else {
                start = Long.parseLong(startPart);
                if (!endPart.isEmpty()) {
                    end = Long.parseLong(endPart);
                }
            }

            if (start < 0 || start > end || start >= fileLength) {
                return buildRangeNotSatisfiable(fileLength);
            }
            if (end >= fileLength) {
                end = fileLength - 1;
            }

            HttpResponse res = new HttpResponse(StatusCode.PARTIAL_CONTENT);
            res.setHeader("ETag", etag);
            res.setHeader("Last-Modified", DateUtil.formatHttpDate(lastModified));
            res.setHeader("Accept-Ranges", "bytes");
            res.setFileRange(file, contentType, start, end, fileLength);
            return res;

        } catch (NumberFormatException e) {
            return buildRangeNotSatisfiable(fileLength);
        }
    }

    private HttpResponse buildRangeNotSatisfiable(long fileLength) {
        HttpResponse res = new HttpResponse(StatusCode.RANGE_NOT_SATISFIABLE);
        res.setHeader("Content-Range", "bytes */" + fileLength);
        res.setBody("Requested range not satisfiable", "text/plain");
        return res;
    }

    /**
     * Generates a clean HTML directory listing when index.html is absent.
     */
    private HttpResponse generateDirectoryListing(String requestPath, File dir) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        sb.append("<title>Index of ").append(escapeHtml(requestPath)).append("</title>");
        sb.append("<style>body{font-family:monospace;padding:2rem;}a{display:block;margin:0.25rem 0;text-decoration:none;color:#0366d6;}a:hover{text-decoration:underline;}</style>");
        sb.append("</head><body>");
        sb.append("<h2>Index of ").append(escapeHtml(requestPath)).append("</h2><hr/>");

        if (!"/".equals(requestPath) && !requestPath.isEmpty()) {
            sb.append("<a href=\"..\">&larr; Parent Directory</a>");
        }

        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : files) {
                String name = f.getName();
                String displayName = f.isDirectory() ? (name + "/") : name;
                String href = requestPath.endsWith("/") ? (requestPath + name) : (requestPath + "/" + name);
                sb.append("<a href=\"").append(escapeHtml(href)).append("\">").append(escapeHtml(displayName)).append("</a>");
            }
        }

        sb.append("<hr/><p><em>NetApps HTTP/1.1 Engine</em></p></body></html>");
        return HttpResponse.html(StatusCode.OK, sb.toString());
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
