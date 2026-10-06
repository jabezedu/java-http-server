package com.netapps.httpserver.http;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Encapsulates a parsed HTTP request, including method, path, headers, query parameters,
 * and body payload.
 */
public class HttpRequest {

    private final HttpMethod method;
    private final String rawMethod;
    private final String rawUri;
    private final String path;
    private final String query;
    private final String httpVersion;
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> queryParameters = new HashMap<>();
    private final byte[] body;
    private final String remoteAddress;

    /**
     * Constructs a fully initialized HttpRequest.
     *
     * @param method         resolved HttpMethod (or null if unsupported)
     * @param rawMethod      raw method string from request line
     * @param rawUri         raw URI string from request line
     * @param path           URL path component
     * @param query          raw query string
     * @param httpVersion    HTTP version (e.g. "HTTP/1.1")
     * @param headers        map of request headers
     * @param body           body bytes (empty array if no body)
     * @param remoteAddress  client remote socket IP/host
     */
    public HttpRequest(HttpMethod method, String rawMethod, String rawUri, String path, String query,
                       String httpVersion, Map<String, String> headers, byte[] body, String remoteAddress) {
        this.method = method;
        this.rawMethod = rawMethod;
        this.rawUri = rawUri;
        this.path = path;
        this.query = query;
        this.httpVersion = httpVersion;
        if (headers != null) {
            this.headers.putAll(headers);
        }
        this.body = (body != null) ? body : new byte[0];
        this.remoteAddress = (remoteAddress != null) ? remoteAddress : "127.0.0.1";
        parseQueryParameters();
    }

    private void parseQueryParameters() {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx).trim();
                String value = (idx < pair.length() - 1) ? pair.substring(idx + 1).trim() : "";
                queryParameters.put(key, value);
            } else if (!pair.trim().isEmpty()) {
                queryParameters.put(pair.trim(), "");
            }
        }
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getRawMethod() {
        return rawMethod;
    }

    public String getRawUri() {
        return rawUri;
    }

    public String getPath() {
        return path;
    }

    public String getQuery() {
        return query;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }

    public Map<String, String> getQueryParameters() {
        return Collections.unmodifiableMap(queryParameters);
    }

    public String getQueryParameter(String name) {
        return queryParameters.get(name);
    }

    public byte[] getBody() {
        return body;
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public boolean isKeepAlive() {
        String conn = getHeader("Connection");
        if ("close".equalsIgnoreCase(conn)) {
            return false;
        }
        if ("keep-alive".equalsIgnoreCase(conn)) {
            return true;
        }
        // HTTP/1.1 defaults to keep-alive; HTTP/1.0 defaults to close
        return "HTTP/1.1".equalsIgnoreCase(httpVersion);
    }
}
