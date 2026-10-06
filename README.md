# Java HTTP/1.1 Server
An RFC-Compliant Web Engine Built on Raw Sockets in Pure Java 17

## Overview

Java HTTP Server is an implementation of the HTTP/1.1 protocol constructed using plain Java 17 socket primitives without third-party libraries, servlet containers, or `com.sun.net.httpserver`. It features a custom stateful protocol parser, persistent Keep-Alive connections, single-range byte slicing, conditional caching (ETag / Last-Modified), REST API endpoints, directory listing generation, path traversal sandboxing, and Common Log Format (CLF) access logging.

## Features

- Raw ServerSocket Engine: Thread-pooled concurrency handling persistent client connections.
- Hand-Crafted Protocol Parser: Strict RFC 7230 parser validating request lines, headers, and Content-Length bodies.
- Robust Error Handling: Returns 400 for malformed frames, 408 for idle read timeouts, 413 for oversized payloads, 501 for chunked transfer, and 505 for unsupported HTTP versions.
- HTTP Methods: Supports GET, HEAD, POST, PUT, DELETE, and OPTIONS; emits 405 Method Not Allowed with Allow headers on unsupported routes.
- Static Web Delivery: Serves files from configurable `./www` root with index.html resolution, automated HTML directory indexer, and MIME mapping for 25+ file extensions.
- Path Traversal Containment: Restricts all file lookups within the web root boundary, rejecting traversal escapes with 403 Forbidden.
- Conditional Caching: Evaluates If-None-Match and If-Modified-Since headers to return 304 Not Modified.
- Byte-Range Slicing: Single-range requests return 206 Partial Content with Content-Range headers in 8 KB chunked streams.
- REST API & Uploads: Built-in endpoints for `/api/time`, `/api/info`, `/api/echo`, `/upload`, `/uploads/<name>` deletion, and `/health`.
- Comprehensive Logging: Common Log Format (CLF) to `logs/access.log`, exception traces to `logs/error.log`, and real-time console summaries.

## Architecture

```
+-------------------------------------------------------------+
|                        Client Layer                         |
|             (Web Browser, cURL, HTTP Utilities)             |
+------------------------------|------------------------------+
                               | TCP Socket Stream
+------------------------------v------------------------------+
|                         Server Layer                        |
|  [HttpServer] -> ServerSocket.accept()                      |
|         |                                                   |
|  [ThreadPoolManager] -> 16 Worker Threads                   |
|         |                                                   |
|  [ConnectionHandler] -> Keep-Alive Session Loop             |
+---------|---------------------------------------------------+
          |
          +--> [HttpParser] -> Headers (Case-Insensitive) & Body
          |
          +--> [RouterHandler]
                 |
                 +--> [ApiHandlers]        (/api/*, /upload, /health)
                 |
                 +--> [StaticFileHandler]  (ETag, Range, Index, 403)
          |
          +--> [HttpResponse]  -> 8 KB Chunked Stream
          |
          +--> [AccessLogger]  -> logs/access.log (CLF) & logs/error.log
```

### Package Breakdown

- `com.netapps.httpserver.server`: ServerSocket listener, worker thread pool manager, and per-connection keep-alive handler.
- `com.netapps.httpserver.http`: HTTP request/response models, method and status enums, RFC stream parser, and protocol exceptions.
- `com.netapps.httpserver.handler`: Router, static file handler with caching and range slicing, and dynamic REST API endpoints.
- `com.netapps.httpserver.config`: Server configuration loader reading `server.properties`.
- `com.netapps.httpserver.util`: MIME type registry, RFC 1123 date formatting, path traversal sanitizers, and thread-safe access loggers.

## Folder Structure

```
java-http-server/
├── .gitignore
├── README.md
├── server.properties
├── docs/
│   ├── ARCHITECTURE.md
│   └── TESTING.md
├── scripts/
│   ├── build.bat
│   ├── build.sh
│   ├── run.bat
│   └── run.sh
├── src/
│   └── com/
│       └── netapps/
│           └── httpserver/
│               ├── config/
│               │   └── ServerConfig.java
│               ├── handler/
│               │   ├── ApiHandlers.java
│               │   ├── RequestHandler.java
│               │   ├── RouterHandler.java
│               │   └── StaticFileHandler.java
│               ├── http/
│               │   ├── HttpException.java
│               │   ├── HttpMethod.java
│               │   ├── HttpParser.java
│               │   ├── HttpRequest.java
│               │   ├── HttpResponse.java
│               │   └── StatusCode.java
│               ├── server/
│               │   ├── ConnectionHandler.java
│               │   ├── HttpServer.java
│               │   └── ThreadPoolManager.java
│               └── util/
│                   ├── AccessLogger.java
│                   ├── DateUtil.java
│                   ├── MimeTypes.java
│                   └── PathUtil.java
└── www/
    ├── about.html
    ├── index.html
    ├── css/
    │   └── style.css
    ├── images/
    │   └── logo.svg
    ├── js/
    │   └── app.js
    └── uploads/
        └── .gitkeep
```

## Requirements

- Java Development Kit (JDK) 17 or higher.
- No Maven, Gradle, or external libraries required.

## Build and Run

### Windows

1. Compile source code:
   ```cmd
   scripts\build.bat
   ```

2. Start the HTTP server:
   ```cmd
   scripts\run.bat
   ```
   To specify a custom port:
   ```cmd
   scripts\run.bat 9000
   ```

### Linux and macOS

1. Make scripts executable and compile:
   ```bash
   chmod +x scripts/*.sh
   ./scripts/build.sh
   ```

2. Start the HTTP server:
   ```bash
   ./scripts/run.sh
   ```
   Or on custom port:
   ```bash
   ./scripts/run.sh 9000
   ```

## Usage Walkthrough

1. Start the server and navigate to `http://localhost:8080/` in a browser to inspect the included sample website.
2. Query live server metrics:
   ```bash
   curl http://localhost:8080/api/info
   ```
3. Test echo payload:
   ```bash
   curl -X POST -H "Content-Type: text/plain" -d "Hello Java Server" http://localhost:8080/api/echo
   ```
4. Upload a file:
   ```bash
   curl -X POST -H "X-Filename: notes.txt" -d "Server notes content" http://localhost:8080/upload
   ```
5. Fetch uploaded file:
   ```bash
   curl http://localhost:8080/uploads/notes.txt
   ```
6. Delete uploaded file:
   ```bash
   curl -X DELETE http://localhost:8080/uploads/notes.txt
   ```

## Configuration Options

Settings configured via `server.properties`:

- `server.port`: TCP listening port (default `8080`).
- `server.host`: Network interface to bind (`0.0.0.0` for all).
- `server.threads`: Fixed pool worker thread count (default `16`).
- `server.webroot`: Document root folder (default `www`).
- `server.directoryListing`: Enable/disable HTML directory browsing (default `true`).
- `server.readTimeoutMs`: Socket read timeout triggering 408 (default `10000`).
- `server.keepAliveTimeoutMs`: Idle timeout for persistent connections (default `15000`).
- `server.maxRequestsPerConnection`: Max requests per keep-alive socket (default `100`).
- `server.maxBodySizeBytes`: Maximum request body limit (default `10485760` = 10 MB).
- `server.uploadDir`: Directory for uploaded files (default `www/uploads`).

## Limitations and Ideas for Improvement

- TLS/HTTPS Support: Implementation communicates over plaintext TCP; `SSLServerSocket` integration would enable HTTPS encryption.
- Chunked Transfer-Encoding: Currently rejected with 501; streaming dechunking could be layered into `HttpParser`.
- Compression: Automatic `gzip` content encoding for HTML, CSS, and JS files would reduce bandwidth consumption.
