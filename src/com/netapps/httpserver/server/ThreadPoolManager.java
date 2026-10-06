package com.netapps.httpserver.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of the fixed-capacity worker thread pool servicing HTTP client connections.
 */
public class ThreadPoolManager {

    private final ExecutorService executor;

    /**
     * Initializes a ThreadPoolManager with a fixed number of threads.
     *
     * @param poolSize number of concurrent worker threads
     */
    public ThreadPoolManager(int poolSize) {
        int threads = (poolSize > 0) ? poolSize : Runtime.getRuntime().availableProcessors();
        this.executor = Executors.newFixedThreadPool(threads);
    }

    /**
     * Submits a connection task for asynchronous execution.
     *
     * @param task client connection task
     */
    public void submit(Runnable task) {
        if (!executor.isShutdown() && task != null) {
            executor.execute(task);
        }
    }

    /**
     * Initiates orderly shutdown of the thread pool.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
