package com.iot.ruleengine.engine.cache;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.iot.ruleengine.engine.RuleExpressionParser;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.repository.RuleRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RuleJsonParseCache {

    private final LoadingCache<Long, ParsedRule> parseCache;

    private final RuleRepository ruleRepository;

    private final RuleExpressionParser ruleExpressionParser;

    public RuleJsonParseCache(RuleRepository ruleRepository, RuleExpressionParser ruleExpressionParser) {
        this.ruleRepository = ruleRepository;
        this.ruleExpressionParser = ruleExpressionParser;
        this.parseCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(24, TimeUnit.HOURS)
                .expireAfterWrite(7, TimeUnit.DAYS)
                .recordStats()
                .build(new CacheLoader<Long, ParsedRule>() {
                    @Override
                    public ParsedRule load(Long ruleId) throws Exception {
                        log.debug("从DB加载并解析规则, ruleId: {}", ruleId);
                        Rule rule = ruleRepository.selectById(ruleId);
                        if (rule == null) {
                            log.warn("规则不存在, ruleId: {}", ruleId);
                            return null;
                        }
                        return parseRule(rule);
                    }
                });
    }

    public ParsedRule getParsed(Long ruleId) {
        return parseCache.get(ruleId);
    }

    public ParsedRule getParsedWithUpdateCheck(Long ruleId, LocalDateTime expectedUpdateTime) {
        ParsedRule cached = parseCache.getIfPresent(ruleId);
        if (cached != null) {
            if (expectedUpdateTime != null && !expectedUpdateTime.equals(cached.getUpdateTime())) {
                log.debug("规则updateTime变更，失效缓存并重新解析, ruleId: {}", ruleId);
                parseCache.invalidate(ruleId);
                return parseCache.get(ruleId);
            }
            return cached;
        }
        return parseCache.get(ruleId);
    }

    public ParsedRule parseAndCache(Rule rule) {
        ParsedRule parsedRule = parseRule(rule);
        parseCache.put(rule.getId(), parsedRule);
        return parsedRule;
    }

    private ParsedRule parseRule(Rule rule) {
        if (rule.getRuleJson() == null || rule.getRuleJson().isEmpty()) {
            log.warn("规则JSON为空, ruleId: {}", rule.getId());
            ParsedRule empty = new ParsedRule();
            empty.setExpression("false");
            empty.setActionsMeta(new JSONObject());
            empty.setUpdateTime(rule.getUpdateTime());
            return empty;
        }

        try {
            RuleExpressionParser.ParseResult parseResult = ruleExpressionParser.parseToExpression(
                    String.valueOf(rule.getId()),
                    rule.getName(),
                    rule.getRuleJson()
            );

            JSONObject actionsMeta = new JSONObject();
            actionsMeta.put("actionType", parseResult.getActionType());
            actionsMeta.put("actionParams", parseResult.getActionParams());
            actionsMeta.put("targetDeviceId", parseResult.getTargetDeviceId());
            actionsMeta.put("actionNodes", parseResult.getActionNodes() != null
                    ? JSON.toJSONString(parseResult.getActionNodes())
                    : null);

            ParsedRule parsedRule = new ParsedRule();
            parsedRule.setExpression(parseResult.getExpression());
            parsedRule.setActionsMeta(actionsMeta);
            parsedRule.setUpdateTime(rule.getUpdateTime());
            return parsedRule;
        } catch (Exception e) {
            log.error("解析规则JSON失败, ruleId: {}, ruleName: {}", rule.getId(), rule.getName(), e);
            ParsedRule error = new ParsedRule();
            error.setExpression("false");
            error.setActionsMeta(new JSONObject());
            error.setUpdateTime(rule.getUpdateTime());
            return error;
        }
    }

    public void invalidate(Long ruleId) {
        log.info("失效规则解析缓存, ruleId: {}", ruleId);
        parseCache.invalidate(ruleId);
    }

    public void invalidateAll() {
        log.info("失效所有规则解析缓存");
        parseCache.invalidateAll();
    }

    public CacheStats stats() {
        return parseCache.stats();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedRule {
        private String expression;
        private JSONObject actionsMeta;
        private LocalDateTime updateTime;
    }
}
