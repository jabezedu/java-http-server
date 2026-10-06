package com.netapps.httpserver.handler;

import com.netapps.httpserver.config.ServerConfig;
import com.netapps.httpserver.http.HttpException;
import com.netapps.httpserver.http.HttpMethod;
import com.netapps.httpserver.http.HttpRequest;
import com.netapps.httpserver.http.HttpResponse;
import com.netapps.httpserver.http.StatusCode;
import com.netapps.httpserver.util.AccessLogger;

/**
 * Directs incoming requests to dedicated API handlers or delegates to the static
 * file delivery engine, while providing standardized error page generation.
 */
public class RouterHandler implements RequestHandler {

    private final StaticFileHandler staticFileHandler = new StaticFileHandler();

    @Override
    public HttpResponse handle(HttpRequest request, ServerConfig config) {
        String path = request.getPath();
        HttpMethod method = request.getMethod();

        ApiHandlers.incrementRequestCount();

        try {
            // Check unsupported or unrecognized HTTP methods
            if (method == null) {
                HttpResponse res = new HttpResponse(StatusCode.METHOD_NOT_ALLOWED);
                res.setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
                res.setBody("Method Not Allowed: " + request.getRawMethod(), "text/plain");
                return res;
            }

            // Global OPTIONS query
            if (method == HttpMethod.OPTIONS && "*".equals(path)) {
                HttpResponse res = new HttpResponse(StatusCode.NO_CONTENT);
                res.setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, OPTIONS");
                return res;
            }

            // 1. Dynamic API Endpoints
            if ("/api/time".equals(path)) {
                if (method == HttpMethod.OPTIONS) {
                    return optionsResponse("GET, HEAD, OPTIONS");
                }
                if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                    return ApiHandlers.handleTime(request);
                }
                return methodNotAllowedResponse("GET, HEAD, OPTIONS");
            }

            if ("/api/info".equals(path)) {
                if (method == HttpMethod.OPTIONS) {
                    return optionsResponse("GET, HEAD, OPTIONS");
                }
                if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                    return ApiHandlers.handleInfo(request);
                }
                return methodNotAllowedResponse("GET, HEAD, OPTIONS");
            }

            if ("/api/echo".equals(path)) {
                if (method == HttpMethod.OPTIONS) {
                    return optionsResponse("POST, OPTIONS");
                }
                if (method == HttpMethod.POST) {
                    return ApiHandlers.handleEcho(request);
                }
                return methodNotAllowedResponse("POST, OPTIONS");
            }

            if ("/upload".equals(path)) {
                if (method == HttpMethod.OPTIONS) {
                    return optionsResponse("POST, OPTIONS");
                }
                if (method == HttpMethod.POST) {
                    return ApiHandlers.handleUpload(request, config);
                }
                return methodNotAllowedResponse("POST, OPTIONS");
            }

            if (path.startsWith("/uploads/") && path.length() > "/uploads/".length()) {
                if (method == HttpMethod.DELETE) {
                    return ApiHandlers.handleDeleteUpload(request, config);
                }
                // Allow GET/HEAD to serve uploaded files statically from upload directory
            }

            if ("/health".equals(path)) {
                if (method == HttpMethod.OPTIONS) {
                    return optionsResponse("GET, HEAD, OPTIONS");
                }
                if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
                    return ApiHandlers.handleHealth(request);
                }
                return methodNotAllowedResponse("GET, HEAD, OPTIONS");
            }

            // 2. Static File Serving
            return staticFileHandler.handle(request, config);

        } catch (HttpException e) {
            if (e.getStatusCode() == StatusCode.NOT_FOUND) {
                return ApiHandlers.build404Page(path);
            }
            HttpResponse errRes = new HttpResponse(e.getStatusCode());
            errRes.setBody(e.getStatusCode().getCode() + " " + e.getMessage(), "text/plain");
            return errRes;
        } catch (Exception e) {
            AccessLogger.logError("Unhandled exception during request processing for " + path, e);
            return ApiHandlers.build500Page(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private HttpResponse optionsResponse(String allow) {
        HttpResponse res = new HttpResponse(StatusCode.NO_CONTENT);
        res.setHeader("Allow", allow);
        return res;
    }

    private HttpResponse methodNotAllowedResponse(String allow) {
        HttpResponse res = new HttpResponse(StatusCode.METHOD_NOT_ALLOWED);
        res.setHeader("Allow", allow);
        res.setBody("405 Method Not Allowed", "text/plain");
        return res;
    }
}
