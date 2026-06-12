package com.iot.ruleengine.controller;

import com.alibaba.fastjson.JSON;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.dto.RuleDTO;
import com.iot.ruleengine.dto.RuleRecommendationDTO;
import com.iot.ruleengine.dto.RuleDetailVO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.recommendation.RuleRecommendationService;
import com.iot.ruleengine.service.RuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/recommendation")
@CrossOrigin(origins = "*", maxAge = 3600)
public class RecommendationController {

    private final RuleRecommendationService recommendationService;
    private final RuleService ruleService;

    @Autowired
    public RecommendationController(RuleRecommendationService recommendationService,
                                     RuleService ruleService) {
        this.recommendationService = recommendationService;
        this.ruleService = ruleService;
    }

    @GetMapping("/list")
    public Result<List<RuleRecommendationDTO>> getRecommendations() {
        log.info("获取规则推荐列表");
        try {
            List<RuleRecommendationDTO> recommendations = recommendationService.getRecommendations();
            return Result.success(recommendations);
        } catch (Exception e) {
            log.error("获取规则推荐失败", e);
            return Result.fail("获取推荐失败: " + e.getMessage());
        }
    }

    @PostMapping("/apply")
    public Result<RuleDetailVO> applyRecommendation(@RequestBody RuleRecommendationDTO recommendation) {
        log.info("应用规则推荐: {}", recommendation.getTitle());
        try {
            RuleDTO ruleDTO = new RuleDTO();
            ruleDTO.setName(recommendation.getTemplateRuleName());
            ruleDTO.setDescription(recommendation.getTemplateDescription());
            ruleDTO.setStatus(0);
            ruleDTO.setPriority(5);

            if (recommendation.getRuleJson() != null) {
                String ruleJson = JSON.toJSONString(recommendation.getRuleJson());
                ruleDTO.setRuleJson(ruleJson);
            }

            Rule savedRule = ruleService.saveRule(ruleDTO);
            log.info("推荐规则应用成功，规则ID: {}", savedRule.getId());

            return Result.success(convertToDetailVO(savedRule));
        } catch (Exception e) {
            log.error("应用规则推荐失败", e);
            return Result.fail("应用推荐失败: " + e.getMessage());
        }
    }

    private RuleDetailVO convertToDetailVO(Rule rule) {
        RuleDetailVO vo = new RuleDetailVO();
        vo.setId(rule.getId());
        vo.setName(rule.getName());
        vo.setDescription(rule.getDescription());
        vo.setStatus(rule.getStatus());
        vo.setPriority(rule.getPriority());
        vo.setMutexGroup(rule.getMutexGroup());
        vo.setRuleJson(rule.getRuleJson());
        vo.setDrlContent(rule.getDrlContent());
        vo.setAviatorExpression(rule.getAviatorExpression() != null ? rule.getAviatorExpression() : "");
        vo.setAviatorActions(rule.getAviatorActions() != null ? rule.getAviatorActions() : "");
        vo.setWindowEnabled(rule.getWindowEnabled());
        vo.setWindowType(rule.getWindowType());
        vo.setWindowDuration(rule.getWindowDuration());
        vo.setWindowAggregation(rule.getWindowAggregation());
        vo.setWindowField(rule.getWindowField());
        vo.setWindowOperator(rule.getWindowOperator());
        vo.setWindowThreshold(rule.getWindowThreshold());
        vo.setCooldownSeconds(rule.getCooldownSeconds());
        vo.setChainTriggerEnabled(rule.getChainTriggerEnabled());
        vo.setChainNextRuleIds(rule.getChainNextRuleIds());
        vo.setChainDisableSelf(rule.getChainDisableSelf());
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());

        Map<String, Object> ruleInfo = new java.util.HashMap<>();
        ruleInfo.put("id", rule.getId());
        ruleInfo.put("name", rule.getName());
        ruleInfo.put("description", rule.getDescription());
        ruleInfo.put("status", rule.getStatus());
        ruleInfo.put("priority", rule.getPriority());
        ruleInfo.put("mutexGroup", rule.getMutexGroup());
        vo.setRuleInfo(ruleInfo);

        vo.setNodes(new java.util.ArrayList<>());
        vo.setEdges(new java.util.ArrayList<>());

        return vo;
    }
}
