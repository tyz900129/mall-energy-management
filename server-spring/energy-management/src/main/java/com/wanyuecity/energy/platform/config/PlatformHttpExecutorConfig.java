package com.wanyuecity.energy.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded thread pool for the source-platform fan-out calls. A fixed pool
 * (8 threads by default) gives ~N× speedup over serial calls while keeping
 * the load on the source platform predictable. Sized to fit a single-process
 * instance; for multi-node deployments each node gets its own pool.
 */
@Slf4j(topic = "platform-sync")
@Configuration
public class PlatformHttpExecutorConfig {

    @Bean(name = "platformHttpExecutor", destroyMethod = "shutdownNow")
    @Lazy
    public ExecutorService platformHttpExecutor(
            @Value("${energy.platform.relation-concurrency:8}") int concurrency) {
        log.info("platform-http executor starting with concurrency={}", concurrency);
        AtomicInteger seq = new AtomicInteger(0);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "platform-http-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        ExecutorService pool = Executors.newFixedThreadPool(concurrency, tf);
        // Register a separate bean for the graceful shutdown so we can wait up to 15s.
        // (Spring's default destroyMethod runs shutdownNow which doesn't wait.)
        ShutdownHook.register(pool);
        return pool;
    }

    /** Tracked shutdown so the pool can drain in-flight HTTP calls on app stop. */
    static class ShutdownHook {
        static void register(ExecutorService pool) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                pool.shutdown();
                try {
                    if (!pool.awaitTermination(15, TimeUnit.SECONDS)) {
                        log.warn("platform-http executor did not drain in 15s; forced shutdown");
                        pool.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }, "platform-http-shutdown"));
        }
    }
}
