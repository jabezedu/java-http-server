package com.netapps.httpserver.server;

import com.netapps.httpserver.config.ServerConfig;
import com.netapps.httpserver.handler.RouterHandler;
import com.netapps.httpserver.util.AccessLogger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Main HTTP server entry point creating a raw ServerSocket, initializing
 * worker thread pools, and dispatching incoming connections.
 */
public class HttpServer {

    private final ServerConfig config;
    private final ThreadPoolManager threadPool;
    private final RouterHandler router;

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    /**
     * Constructs an HttpServer with a given configuration.
     *
     * @param config active server configuration
     */
    public HttpServer(ServerConfig config) {
        this.config = config;
        this.threadPool = new ThreadPoolManager(config.getThreads());
        this.router = new RouterHandler();
    }

    /**
     * Starts listening on the configured host and port.
     */
    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "HttpServer-ShutdownHook"));

        try {
            InetAddress bindAddr = InetAddress.getByName(config.getHost());
            serverSocket = new ServerSocket(config.getPort(), 128, bindAddr);

            System.out.println("=================================================");
            System.out.println(" NetApps HTTP/1.1 Web Server");
            System.out.println(" Listening on: http://" + config.getHost() + ":" + config.getPort() + "/");
            System.out.println(" Web Root:     " + new File(config.getWebroot()).getAbsolutePath());
            System.out.println(" Worker Pool:  " + config.getThreads() + " threads");
            System.out.println("=================================================");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ConnectionHandler handler = new ConnectionHandler(clientSocket, config, router);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    if (running) {
                        AccessLogger.logError("Error accepting incoming socket connection", e);
                    }
                }
            }
        } catch (IOException e) {
            AccessLogger.logError("Failed to bind ServerSocket on " + config.getHost() + ":" + config.getPort(), e);
        } finally {
            stop();
        }
    }

    /**
     * Gracefully stops the server, closing ServerSocket and terminating worker threads.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        System.out.println("\nStopping NetApps HTTP Server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        threadPool.shutdown();
        AccessLogger.close();
        System.out.println("Server stopped cleanly.");
    }

    /**
     * Entry point for running the server from the command line.
     *
     * @param args optional arguments: [port] or [configFilePath]
     */
    public static void main(String[] args) {
        File configFile = new File("server.properties");
        ServerConfig config = ServerConfig.load(configFile);

        // Allow CLI port override: java -cp out com.netapps.httpserver.server.HttpServer 9000
        if (args.length > 0) {
            try {
                int cliPort = Integer.parseInt(args[0].trim());
                config = new ServerConfig(cliPort, config.getHost(), config.getThreads(),
                        config.getWebroot(), config.isDirectoryListingEnabled(), config.getReadTimeoutMs(),
                        config.getKeepAliveTimeoutMs(), config.getMaxRequestsPerConnection(),
                        config.getMaxBodySizeBytes(), config.getUploadDir());
            } catch (NumberFormatException e) {
                File customConfig = new File(args[0].trim());
                if (customConfig.exists()) {
                    config = ServerConfig.load(customConfig);
                }
            }
        }

        HttpServer server = new HttpServer(config);
        server.start();
    }
}
