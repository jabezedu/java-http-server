package com.netapps.httpserver.util;

import com.netapps.httpserver.http.HttpException;
import com.netapps.httpserver.http.StatusCode;

import java.io.File;
import java.io.IOException;

/**
 * Utilities for filesystem path resolution, path traversal defense, and filename sanitization.
 */
public final class PathUtil {

    private PathUtil() {
    }

    /**
     * Resolves a relative URI path against a root directory ensuring the canonical result
     * remains strictly within the root directory boundary.
     *
     * @param rootDir      allowed base directory
     * @param relativePath requested relative path from URI
     * @return validated File reference
     * @throws HttpException with FORBIDDEN (403) on path traversal attempts
     * @throws IOException   on canonical path resolution errors
     */
    public static File resolveSafeFile(File rootDir, String relativePath) throws HttpException, IOException {
        String cleanPath = relativePath;
        if (cleanPath == null) {
            cleanPath = "";
        }
        // Normalize leading slashes
        while (cleanPath.startsWith("/") || cleanPath.startsWith("\\")) {
            cleanPath = cleanPath.substring(1);
        }

        File target = new File(rootDir, cleanPath).getCanonicalFile();
        File base = rootDir.getCanonicalFile();

        // Enforce path traversal containment
        if (!target.getPath().startsWith(base.getPath())) {
            throw new HttpException(StatusCode.FORBIDDEN, "Forbidden: Access denied to path outside web root");
        }

        return target;
    }

    /**
     * Sanitizes a filename submitted via X-Filename headers to prevent directory traversal.
     *
     * @param rawFilename raw header string
     * @return safe base filename
     */
    public static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            return "upload_" + System.currentTimeMillis() + ".dat";
        }
        String clean = rawFilename.trim();
        // Extract base name only
        int lastSlash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1).trim();
        }

        // Remove illegal traversal elements
        clean = clean.replace("..", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.isEmpty() || clean.equals(".") || clean.equals("..")) {
            return "upload_" + System.currentTimeMillis() + ".dat";
        }
        return clean;
    }
}
