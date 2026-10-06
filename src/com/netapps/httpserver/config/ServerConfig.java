package com.netapps.httpserver.config;

import com.netapps.httpserver.util.AccessLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Immutable configuration parameters for the HTTP server loaded from server.properties
 * with safe fallback defaults.
 */
public class ServerConfig {

    private final int port;
    private final String host;
    private final int threads;
    private final String webroot;
    private final boolean directoryListing;
    private final int readTimeoutMs;
    private final int keepAliveTimeoutMs;
    private final int maxRequestsPerConnection;
    private final long maxBodySizeBytes;
    private final String uploadDir;

    public ServerConfig(int port, String host, int threads, String webroot, boolean directoryListing,
                        int readTimeoutMs, int keepAliveTimeoutMs, int maxRequestsPerConnection,
                        long maxBodySizeBytes, String uploadDir) {
        this.port = port;
        this.host = host;
        this.threads = threads;
        this.webroot = webroot;
        this.directoryListing = directoryListing;
        this.readTimeoutMs = readTimeoutMs;
        this.keepAliveTimeoutMs = keepAliveTimeoutMs;
        this.maxRequestsPerConnection = maxRequestsPerConnection;
        this.maxBodySizeBytes = maxBodySizeBytes;
        this.uploadDir = uploadDir;
    }

    /**
     * Loads configuration from a file or falls back to standard defaults if absent.
     *
     * @param configFile configuration properties file
     * @return initialized ServerConfig
     */
    public static ServerConfig load(File configFile) {
        Properties props = new Properties();
        if (configFile != null && configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                AccessLogger.logError("Failed to load configuration file: " + configFile.getPath(), e);
            }
        }

        int port = getInt(props, "server.port", 8080);
        String host = props.getProperty("server.host", "0.0.0.0");
        int threads = getInt(props, "server.threads", 16);
        String webroot = props.getProperty("server.webroot", "www");
        boolean directoryListing = Boolean.parseBoolean(props.getProperty("server.directoryListing", "true"));
        int readTimeoutMs = getInt(props, "server.readTimeoutMs", 10000);
        int keepAliveTimeoutMs = getInt(props, "server.keepAliveTimeoutMs", 15000);
        int maxRequests = getInt(props, "server.maxRequestsPerConnection", 100);
        long maxBodySize = getLong(props, "server.maxBodySizeBytes", 10L * 1024L * 1024L);
        String uploadDir = props.getProperty("server.uploadDir", "www/uploads");

        return new ServerConfig(port, host, threads, webroot, directoryListing,
                readTimeoutMs, keepAliveTimeoutMs, maxRequests, maxBodySize, uploadDir);
    }

    private static int getInt(Properties props, String key, int defVal) {
        String val = props.getProperty(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defVal;
    }

    private static long getLong(Properties props, String key, long defVal) {
        String val = props.getProperty(key);
        if (val != null) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defVal;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public int getThreads() {
        return threads;
    }

    public String getWebroot() {
        return webroot;
    }

    public boolean isDirectoryListingEnabled() {
        return directoryListing;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getKeepAliveTimeoutMs() {
        return keepAliveTimeoutMs;
    }

    public int getMaxRequestsPerConnection() {
        return maxRequestsPerConnection;
    }

    public long getMaxBodySizeBytes() {
        return maxBodySizeBytes;
    }

    public String getUploadDir() {
        return uploadDir;
    }
}
