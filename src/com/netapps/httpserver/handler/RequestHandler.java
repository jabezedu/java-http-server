package com.netapps.httpserver.handler;

import com.netapps.httpserver.config.ServerConfig;
import com.netapps.httpserver.http.HttpException;
import com.netapps.httpserver.http.HttpRequest;
import com.netapps.httpserver.http.HttpResponse;

/**
 * Functional contract for processing HTTP requests and generating HTTP responses.
 */
public interface RequestHandler {

    /**
     * Handles an HTTP request.
     *
     * @param request parsed HTTP request
     * @param config  active server configuration
     * @return generated HttpResponse
     * @throws HttpException on semantic or protocol error
     * @throws Exception     on unexpected runtime failures
     */
    HttpResponse handle(HttpRequest request, ServerConfig config) throws HttpException, Exception;
}
