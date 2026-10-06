package com.netapps.httpserver.http;

/**
 * Exception representing protocol errors, parsing failures, or abnormal HTTP conditions.
 */
public class HttpException extends Exception {

    private final StatusCode statusCode;

    /**
     * Constructs an HttpException with a specific status code and message.
     *
     * @param statusCode the HTTP status code to return to the client
     * @param message    description of the failure
     */
    public HttpException(StatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Constructs an HttpException with a status code, message, and cause.
     *
     * @param statusCode the HTTP status code to return to the client
     * @param message    description of the failure
     * @param cause      underlying exception
     */
    public HttpException(StatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Retrieves the associated HTTP status code.
     *
     * @return StatusCode enum constant
     */
    public StatusCode getStatusCode() {
        return statusCode;
    }
}
