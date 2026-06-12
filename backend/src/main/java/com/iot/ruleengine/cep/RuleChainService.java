package com.iot.ruleengine.cep;

import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.service.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleChainService {

    private final RuleRepository ruleRepository;
    private final RuleService ruleService;

    @Autowired
    public RuleChainService(RuleRepository ruleRepository, RuleService ruleService) {
        this.ruleRepository = ruleRepository;
        this.ruleService = ruleService;
    }

    public void processRuleChain(Rule triggeredRule) {
        if (triggeredRule.getChainTriggerEnabled() == null || triggeredRule.getChainTriggerEnabled() != 1) {
            return;
        }

        String nextRuleIdsStr = triggeredRule.getChainNextRuleIds();
        if (nextRuleIdsStr == null || nextRuleIdsStr.trim().isEmpty()) {
            return;
        }

        List<Long> nextRuleIds = parseRuleIds(nextRuleIdsStr);
        if (nextRuleIds.isEmpty()) {
            return;
        }

        log.info("处理规则链: 触发规则={}({}), 启用下一规则IDs={}",
                triggeredRule.getId(), triggeredRule.getName(), nextRuleIds);

        for (Long nextRuleId : nextRuleIds) {
            try {
                ruleService.enableRule(nextRuleId);
                log.info("规则链: 已启用规则 ID={}", nextRuleId);
            } catch (Exception e) {
                log.error("规则链: 启用规则失败, ruleId={}", nextRuleId, e);
            }
        }

        if (triggeredRule.getChainDisableSelf() != null && triggeredRule.getChainDisableSelf() == 1) {
            try {
                ruleService.disableRule(triggeredRule.getId());
                log.info("规则链: 已禁用自身规则 ID={}", triggeredRule.getId());
            } catch (Exception e) {
                log.error("规则链: 禁用自身规则失败, ruleId={}", triggeredRule.getId(), e);
            }
        }
    }

    public List<Long> getNextRuleIds(Rule rule) {
        if (rule.getChainTriggerEnabled() == null || rule.getChainTriggerEnabled() != 1) {
            return Collections.emptyList();
        }
        return parseRuleIds(rule.getChainNextRuleIds());
    }

    public boolean isPartOfChain(Long ruleId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Rule> queryWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        queryWrapper.eq("chain_trigger_enabled", 1)
                .like("chain_next_rule_ids", String.valueOf(ruleId));

        List<Rule> rules = ruleRepository.selectList(queryWrapper);
        return !rules.isEmpty();
    }

    private List<Long> parseRuleIds(String idsStr) {
        if (idsStr == null || idsStr.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(idsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        log.warn("无效的规则ID: {}", s);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }
}
