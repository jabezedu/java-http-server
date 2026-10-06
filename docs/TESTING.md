# Manual Test Plan: Java HTTP Server

This document contains a test suite for validating all features of the HTTP/1.1 server using `curl` or web browsers.

---

## Prerequisites

1. Compile the server: `scripts/build.bat` (or `./scripts/build.sh`).
2. Start the server: `scripts/run.bat` (or `./scripts/run.sh`).
3. Server should be listening on `http://127.0.0.1:8080/`.

---

## Test Cases

### 1. Static File Serving & Default Document
- **Command**:
  ```bash
  curl -i http://localhost:8080/
  ```
- **Expected Result**:
  - `HTTP/1.1 200 OK`
  - `Content-Type: text/html; charset=UTF-8`
  - Serves `www/index.html` content.
  - Headers include `Server`, `Date`, `ETag`, `Last-Modified`, and `Content-Length`.

---

### 2. Auto-Generated Directory Listing
- **Command**:
  ```bash
  curl -i http://localhost:8080/css/
  ```
- **Expected Result**:
  - `HTTP/1.1 200 OK`
  - `Content-Type: text/html; charset=UTF-8`
  - Returns HTML document containing link to `style.css` and parent directory.

---

### 3. MIME Type Verification (20+ Types)
- **Command**:
  ```bash
  curl -i http://localhost:8080/images/logo.svg
  curl -i http://localhost:8080/js/app.js
  ```
- **Expected Result**:
  - Logo returns `Content-Type: image/svg+xml`.
  - Script returns `Content-Type: application/javascript; charset=UTF-8`.

---

### 4. Path Traversal Protection
- **Command**:
  ```bash
  curl -i --path-as-is http://localhost:8080/../../server.properties
  curl -i --path-as-is http://localhost:8080/..%2f..%2fserver.properties
  ```
- **Expected Result**:
  - `HTTP/1.1 403 Forbidden`
  - Body contains error explaining access violation outside the web root.

---

### 5. Conditional Request: ETag & If-None-Match
- **Command**:
  ```bash
  # Step 1: Capture ETag
  ETAG=$(curl -sI http://localhost:8080/css/style.css | grep -i ETag | awk '{print $2}' | tr -d '\r')
  # Step 2: Send conditional request
  curl -i -H "If-None-Match: $ETAG" http://localhost:8080/css/style.css
  ```
- **Expected Result**:
  - `HTTP/1.1 304 Not Modified`
  - Empty response body.

---

### 6. Byte-Range Slicing (206 Partial Content & 416)
- **Command**:
  ```bash
  curl -i -H "Range: bytes=0-49" http://localhost:8080/css/style.css
  curl -i -H "Range: bytes=999999-9999999" http://localhost:8080/css/style.css
  ```
- **Expected Result**:
  - First request returns `HTTP/1.1 206 Partial Content`, `Content-Range: bytes 0-49/<total>`, and 50 bytes.
  - Second request returns `HTTP/1.1 416 Range Not Satisfiable` and `Content-Range: bytes */<total>`.

---

### 7. Method Not Allowed (405)
- **Command**:
  ```bash
  curl -i -X PUT http://localhost:8080/index.html
  ```
- **Expected Result**:
  - `HTTP/1.1 405 Method Not Allowed`
  - Header: `Allow: GET, HEAD, OPTIONS`.

---

### 8. Malformed Requests & Unsupported Versions
- **Command (Raw Socket / Telnet)**:
  ```bash
  printf "INVALID_REQUEST\r\n\r\n" | nc localhost 8080
  printf "GET / HTTP/2.0\r\nHost: localhost\r\n\r\n" | nc localhost 8080
  ```
- **Expected Result**:
  - First request returns `HTTP/1.1 400 Bad Request`.
  - Second request returns `HTTP/1.1 505 HTTP Version Not Supported`.

---

### 9. Chunked Transfer Rejection (501)
- **Command**:
  ```bash
  curl -i -X POST -H "Transfer-Encoding: chunked" -d "hello" http://localhost:8080/api/echo
  ```
- **Expected Result**:
  - `HTTP/1.1 501 Not Implemented`.

---

### 10. Payload Too Large (413)
- **Command**:
  ```bash
  curl -i -X POST -H "Content-Length: 999999999" http://localhost:8080/api/echo
  ```
- **Expected Result**:
  - `HTTP/1.1 413 Payload Too Large`.

---

### 11. REST API Dynamic Endpoints
- **Endpoints**:
  ```bash
  curl -i http://localhost:8080/health
  curl -i http://localhost:8080/api/time
  curl -i http://localhost:8080/api/info
  curl -i -X POST -H "Content-Type: text/plain" -d "Testing Echo" http://localhost:8080/api/echo
  ```
- **Expected Result**:
  - `/health` returns `200 OK` and `{"status":"ok"}`.
  - `/api/time` returns `200 OK` with JSON timestamp and UTC/local strings.
  - `/api/info` returns `200 OK` with server name, uptime, requests, and active connections.
  - `/api/echo` returns `200 OK` echoing headers, body length, and payload text.

---

### 12. File Upload & Deletion Workflow
- **Steps**:
  ```bash
  # Upload:
  curl -i -X POST -H "X-Filename: mydoc.txt" -d "Sample uploaded content" http://localhost:8080/upload
  # Download back:
  curl -i http://localhost:8080/uploads/mydoc.txt
  # Delete:
  curl -i -X DELETE http://localhost:8080/uploads/mydoc.txt
  ```
- **Expected Results**:
  - Upload returns `HTTP/1.1 201 Created` with `Location: /uploads/mydoc.txt`.
  - Download returns `HTTP/1.1 200 OK` with text content.
  - Delete returns `HTTP/1.1 200 OK` confirming deletion.
  - Subsequent download returns `404 Not Found`.

---

### 13. Access Logging Verification
- **Steps**:
  Inspect `./logs/access.log`.
- **Expected Result**:
  - Entries formatted in Common Log Format (CLF):
    `127.0.0.1 - - [06/Oct/2026:08:40:15 +0530] "GET / HTTP/1.1" 200 2415`
