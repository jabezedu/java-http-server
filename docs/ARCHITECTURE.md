# Architecture: Java HTTP/1.1 Server

## Overview

The Java HTTP Server is an implementation of the HTTP/1.1 protocol (RFC 7230, RFC 7231, RFC 7232, RFC 7233) built purely on Java standard library primitives (`java.net.ServerSocket`, `java.io.InputStream`, `java.io.OutputStream`) with zero third-party framework dependencies.

---

## Component Architecture

```
+-----------------------------------------------------------------------------------+
|                                  Client Browser / CLI                             |
+-----------------------------------------------------------------------------------+
                                          |
                                   TCP Connections
                                          v
+-----------------------------------------------------------------------------------+
|                        HttpServer (ServerSocket Accept Loop)                      |
|                                         |                                         |
|                           [ThreadPoolManager (16 Workers)]                        |
+-----------------------------------------------------------------------------------+
                                          | Hand-off
                                          v
+-----------------------------------------------------------------------------------+
|                        ConnectionHandler (Per-Connection Loop)                    |
|                                                                                   |
|  1. Apply Keep-Alive Timeout (socket.setSoTimeout)                                |
|  2. HttpParser: Parse Request-Line, Case-Insensitive Headers, Content-Length Body  |
|  3. Validate Protocol (Reject: 400 Malformed, 408 Timeout, 413 Too Large,         |
|                         501 Chunked, 505 Bad Version)                             |
|  4. RouterHandler: Route Path                                                      |
|     +--> Dynamic Endpoints:                                                       |
|     |    - /api/time, /api/info, /api/echo, /upload, /uploads/<file>, /health     |
|     +--> StaticFileHandler:                                                       |
|          - Path traversal verification (canonical path confinement)               |
|          - Directory resolution & index.html / HTML listing generation             |
|          - Conditional caching: ETag / If-None-Match, Last-Modified / If-Modified   |
|          - Byte-range slicing (Range: bytes=start-end -> 206 Partial Content)     |
|  5. HttpResponse Serialization:                                                   |
|     - Stream headers + in-memory payload OR 8 KB chunked file streaming           |
|  6. AccessLogger: Emit Common Log Format & Error Audit Logs                       |
+-----------------------------------------------------------------------------------+
```

---

## Request & Packet Processing Flow

1. **Acceptance**: `HttpServer` accepts incoming TCP socket connections on the configured port (default `8080`) and immediately offloads them to a fixed worker thread pool via `ThreadPoolManager`.
2. **Persistent Connection Session**:
   - The worker sets the socket timeout to `keepAliveTimeoutMs` (e.g. 15,000 ms).
   - Enters a loop bounded by `maxRequestsPerConnection` (e.g. 100 requests).
3. **Parsing**:
   - `HttpParser` reads byte-by-byte through CRLF delimiters without overreading stream buffers.
   - Extracts the request line (`METHOD URI VERSION`).
   - Normalizes headers into a case-insensitive `TreeMap`.
   - Reads exact `Content-Length` bytes for methods containing request bodies.
4. **Validation**:
   - Versions other than `HTTP/1.0` and `HTTP/1.1` trigger an immediate `505 HTTP Version Not Supported`.
   - `Transfer-Encoding: chunked` triggers `501 Not Implemented`.
   - Payloads exceeding `maxBodySizeBytes` trigger `413 Payload Too Large`.
   - Socket read expiration triggers `408 Request Timeout`.
5. **Dispatch & Routing**:
   - `/api/*`, `/health`, `/upload` execute within `ApiHandlers`.
   - Filesystem requests are verified by `PathUtil.resolveSafeFile` ensuring `target.getCanonicalPath().startsWith(root.getCanonicalPath())`. Traversal attempts abort with `403 Forbidden`.
   - Caching headers (`If-None-Match`, `If-Modified-Since`) are evaluated; if unexpired, headers are returned with `304 Not Modified` and zero body.
   - Range headers are parsed for start and end offsets; sliced files are streamed with `206 Partial Content` in 8 KB chunk loops.
6. **Delivery & Logging**:
   - Headers and data are flushed out.
   - Request exchange is recorded to `logs/access.log` using Common Log Format (CLF).
