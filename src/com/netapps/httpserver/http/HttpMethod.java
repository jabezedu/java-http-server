package com.netapps.httpserver.http;

/**
 * Enumeration of HTTP methods supported by the server.
 */
public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    OPTIONS;

    /**
     * Parses a raw method string case-sensitively according to RFC 7230.
     *
     * @param methodStr raw method token
     * @return HttpMethod or null if unsupported
     */
    public static HttpMethod fromString(String methodStr) {
        if (methodStr == null) {
            return null;
        }
        try {
            return HttpMethod.valueOf(methodStr.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
