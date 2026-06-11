package com.iot.ruleengine.engine.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class RuleCacheManager<K, V> {

    private final Cache<K, V> cache;

    public RuleCacheManager() {
        this(5000, 24, TimeUnit.HOURS);
    }

    public RuleCacheManager(int maximumSize, long expireAfterWrite, TimeUnit timeUnit) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite, timeUnit)
                .softValues()
                .recordStats()
                .removalListener((K key, V value, RemovalCause cause) -> {
                    log.info("缓存项被移除, key: {}, cause: {}", key, cause);
                    if (cause.wasEvicted()) {
                        log.warn("缓存项被驱逐, key: {}, 可能原因: 大小限制/过期/引用回收", key);
                    }
                })
                .build();
    }

    public Cache<K, V> getCache() {
        return cache;
    }

    public void put(K key, V value) {
        log.debug("缓存规则, key: {}", key);
        cache.put(key, value);
    }

    public V getIfPresent(K key) {
        V value = cache.getIfPresent(key);
        if (value != null) {
            log.debug("缓存命中, key: {}", key);
        }
        return value;
    }

    public void invalidate(K key) {
        log.info("失效缓存, key: {}", key);
        cache.invalidate(key);
    }

    public void invalidateAll() {
        log.info("失效所有缓存");
        cache.invalidateAll();
    }

    public long size() {
        return cache.estimatedSize();
    }

    public CacheStats getStats() {
        return cache.stats();
    }

    public void cleanUp() {
        cache.cleanUp();
    }
}
