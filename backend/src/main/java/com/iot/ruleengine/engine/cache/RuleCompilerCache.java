package com.iot.ruleengine.engine.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RuleCompilerCache {

    private final LoadingCache<CacheKey, Object> compileCache;

    private final AviatorEvaluatorInstance aviatorEvaluator;

    public RuleCompilerCache(AviatorEvaluatorInstance aviatorEvaluator) {
        this.aviatorEvaluator = aviatorEvaluator;
        this.compileCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(24, TimeUnit.HOURS)
                .expireAfterWrite(7, TimeUnit.DAYS)
                .recordStats()
                .build(new CacheLoader<CacheKey, Object>() {
                    @Override
                    public Object load(CacheKey key) throws Exception {
                        log.debug("编译表达式, ruleId: {}, expression: {}", key.ruleId, key.expression);
                        return aviatorEvaluator.compile(key.expression, true);
                    }
                });
    }

    public Expression getCompiled(Long ruleId, String expression) {
        CacheKey key = new CacheKey(ruleId, expression);
        return (Expression) compileCache.get(key);
    }

    public void invalidate(Long ruleId) {
        log.info("失效规则编译缓存, ruleId: {}", ruleId);
        compileCache.asMap().keySet().removeIf(key -> Objects.equals(key.ruleId, ruleId));
    }

    public void invalidateAll() {
        log.info("失效所有规则编译缓存");
        compileCache.invalidateAll();
    }

    public CacheStats stats() {
        return compileCache.stats();
    }

    public static class CacheKey {
        private final Long ruleId;
        private final String expression;

        public CacheKey(Long ruleId, String expression) {
            this.ruleId = ruleId;
            this.expression = expression;
        }

        public Long getRuleId() {
            return ruleId;
        }

        public String getExpression() {
            return expression;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(ruleId, cacheKey.ruleId) &&
                    Objects.equals(expression, cacheKey.expression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleId, expression);
        }
    }
}
