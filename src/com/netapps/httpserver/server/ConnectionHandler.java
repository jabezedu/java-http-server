package com.netapps.httpserver.server;

import com.netapps.httpserver.config.ServerConfig;
import com.netapps.httpserver.handler.ApiHandlers;
import com.netapps.httpserver.handler.RouterHandler;
import com.netapps.httpserver.http.HttpException;
import com.netapps.httpserver.http.HttpMethod;
import com.netapps.httpserver.http.HttpParser;
import com.netapps.httpserver.http.HttpRequest;
import com.netapps.httpserver.http.HttpResponse;
import com.netapps.httpserver.http.StatusCode;
import com.netapps.httpserver.util.AccessLogger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Handles an accepted TCP client socket connection, executing persistent keep-alive loops,
 * enforcing socket timeouts, invoking request parsers and routers, and emitting access logs.
 */
public class ConnectionHandler implements Runnable {

    private final Socket socket;
    private final ServerConfig config;
    private final RouterHandler router;

    /**
     * Constructs a ConnectionHandler for an accepted client socket.
     *
     * @param socket accepted client socket
     * @param config active server configuration
     * @param router application request router
     */
    public ConnectionHandler(Socket socket, ServerConfig config, RouterHandler router) {
        this.socket = socket;
        this.config = config;
        this.router = router;
    }

    @Override
    public void run() {
        ApiHandlers.incrementActiveConnections();
        String remoteAddr = socket.getInetAddress() != null
                ? socket.getInetAddress().getHostAddress()
                : "127.0.0.1";

        InputStream in = null;
        OutputStream out = null;

        try {
            socket.setSoTimeout(config.getKeepAliveTimeoutMs());
            in = new BufferedInputStream(socket.getInputStream(), 8192);
            out = new BufferedOutputStream(socket.getOutputStream(), 8192);

            int requestCount = 0;

            while (!socket.isClosed() && requestCount < config.getMaxRequestsPerConnection()) {
                long startTime = System.currentTimeMillis();
                HttpRequest request = null;

                try {
                    request = HttpParser.parse(in, remoteAddr, config.getMaxBodySizeBytes());
                    if (request == null) {
                        break; // Orderly client close or empty line
                    }

                    requestCount++;
                    boolean isKeepAlive = request.isKeepAlive() && (requestCount < config.getMaxRequestsPerConnection());

                    HttpResponse response = router.handle(request, config);

                    if (isKeepAlive) {
                        response.setHeader("Connection", "keep-alive");
                        int remaining = config.getMaxRequestsPerConnection() - requestCount;
                        response.setHeader("Keep-Alive", "timeout=" + (config.getKeepAliveTimeoutMs() / 1000) + ", max=" + remaining);
                    } else {
                        response.setHeader("Connection", "close");
                    }

                    boolean headOnly = (request.getMethod() == HttpMethod.HEAD);
                    response.writeTo(out, headOnly);

                    long duration = System.currentTimeMillis() - startTime;
                    AccessLogger.logRequest(request, response, duration);

                    if (!isKeepAlive) {
                        break;
                    }

                } catch (HttpException e) {
                    long duration = System.currentTimeMillis() - startTime;
                    HttpResponse errResponse = new HttpResponse(e.getStatusCode());
                    errResponse.setHeader("Connection", "close");
                    errResponse.setBody(e.getStatusCode().getCode() + " " + e.getMessage(), "text/plain");
                    try {
                        errResponse.writeTo(out, false);
                    } catch (IOException ignored) {
                    }
                    AccessLogger.logRequest(request, errResponse, duration);
                    break;

                } catch (SocketTimeoutException e) {
                    // If timeout occurred after servicing at least one request, close quietly (keep-alive idle timeout)
                    if (requestCount == 0) {
                        long duration = System.currentTimeMillis() - startTime;
                        HttpResponse timeoutRes = new HttpResponse(StatusCode.REQUEST_TIMEOUT);
                        timeoutRes.setHeader("Connection", "close");
                        timeoutRes.setBody("408 Request Timeout", "text/plain");
                        try {
                            timeoutRes.writeTo(out, false);
                        } catch (IOException ignored) {
                        }
                        AccessLogger.logRequest(null, timeoutRes, duration);
                    }
                    break;
                }
            }

        } catch (IOException e) {
            // Socket reset or network error
        } finally {
            ApiHandlers.decrementActiveConnections();
            closeQuietly(in);
            closeQuietly(out);
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception ignored) {
            }
        }
    }
}
