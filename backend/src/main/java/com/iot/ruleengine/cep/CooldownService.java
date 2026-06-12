package com.iot.ruleengine.cep;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CooldownService {

    private final Map<String, Long> lastTriggerTimeMap = new ConcurrentHashMap<>();

    public boolean canTrigger(Long ruleId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return true;
        }

        String key = buildKey(ruleId);
        Long lastTrigger = lastTriggerTimeMap.get(key);

        if (lastTrigger == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastTrigger;
        long cooldownMs = TimeUnit.SECONDS.toMillis(cooldownSeconds);

        boolean canTrigger = elapsed >= cooldownMs;

        if (!canTrigger) {
            long remaining = (cooldownMs - elapsed) / 1000;
            log.debug("规则处于冷却期: ruleId={}, 剩余冷却时间={}秒", ruleId, remaining);
        }

        return canTrigger;
    }

    public void recordTrigger(Long ruleId) {
        String key = buildKey(ruleId);
        lastTriggerTimeMap.put(key, System.currentTimeMillis());
        log.debug("记录规则触发时间: ruleId={}", ruleId);
    }

    public long getRemainingCooldown(Long ruleId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }

        String key = buildKey(ruleId);
        Long lastTrigger = lastTriggerTimeMap.get(key);

        if (lastTrigger == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastTrigger;
        long cooldownMs = TimeUnit.SECONDS.toMillis(cooldownSeconds);

        if (elapsed >= cooldownMs) {
            return 0;
        }

        return (cooldownMs - elapsed) / 1000;
    }

    public void resetCooldown(Long ruleId) {
        String key = buildKey(ruleId);
        lastTriggerTimeMap.remove(key);
        log.debug("重置规则冷却期: ruleId={}", ruleId);
    }

    public void cleanupExpired() {
        long oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        lastTriggerTimeMap.entrySet().removeIf(entry -> entry.getValue() < oneDayAgo);
    }

    private String buildKey(Long ruleId) {
        return "rule_cooldown:" + ruleId;
    }
}
