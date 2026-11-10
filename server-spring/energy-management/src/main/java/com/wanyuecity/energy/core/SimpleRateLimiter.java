package com.wanyuecity.energy.core;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内滑动窗口限流，用于免登录 H5 入口。
 * 单实例部署下足够；多实例部署需替换为 Redis 等共享存储。
 */
@Component
public class SimpleRateLimiter {

    private static final int CLEANUP_THRESHOLD = 5000;

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** 超出限制时抛出 400，消息直接展示给用户。 */
    public void check(String key, int limit, Duration window, String message) {
        Instant now = Instant.now();
        cleanupIfNeeded(now);
        Deque<Instant> queue = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(now.minus(window))) {
                queue.removeFirst();
            }
            if (queue.size() >= limit) throw BizException.badRequest(message);
            queue.addLast(now);
        }
    }

    private void cleanupIfNeeded(Instant now) {
        if (hits.size() < CLEANUP_THRESHOLD) return;
        Iterator<Map.Entry<String, Deque<Instant>>> it = hits.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Instant> queue = it.next().getValue();
            synchronized (queue) {
                if (queue.isEmpty() || queue.peekLast().isBefore(now.minus(Duration.ofHours(1)))) it.remove();
            }
        }
    }
}
