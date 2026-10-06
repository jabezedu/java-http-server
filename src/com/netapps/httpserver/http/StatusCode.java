package com.netapps.httpserver.http;

/**
 * Standard HTTP/1.1 status codes supported by the server.
 */
public enum StatusCode {
    OK(200, "OK"),
    CREATED(201, "Created"),
    NO_CONTENT(204, "No Content"),
    PARTIAL_CONTENT(206, "Partial Content"),
    NOT_MODIFIED(304, "Not Modified"),
    BAD_REQUEST(400, "Bad Request"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
    RANGE_NOT_SATISFIABLE(416, "Range Not Satisfiable"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported");

    private final int code;
    private final String reasonPhrase;

    StatusCode(int code, String reasonPhrase) {
        this.code = code;
        this.reasonPhrase = reasonPhrase;
    }

    /**
     * Gets the numeric status code.
     *
     * @return 3-digit HTTP status code
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the standard RFC reason phrase.
     *
     * @return reason phrase
     */
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    /**
     * Resolves a StatusCode by its integer code.
     *
     * @param code integer code
     * @return matching StatusCode or INTERNAL_SERVER_ERROR if unknown
     */
    public static StatusCode fromCode(int code) {
        for (StatusCode sc : values()) {
            if (sc.code == code) {
                return sc;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }
}
