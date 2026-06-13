package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.RuleDTO;
import com.iot.ruleengine.dto.RuleTestDTO;
import com.iot.ruleengine.dto.SandboxTestRequest;
import com.iot.ruleengine.entity.Rule;

import java.util.List;
import java.util.Map;

public interface RuleService {

    Rule saveRule(RuleDTO ruleDTO);

    Rule updateRule(RuleDTO ruleDTO);

    void deleteRule(Long id);

    Rule getRuleById(Long id);

    Page<Rule> listRules(Page<Rule> page, Map<String, Object> params);

    void enableRule(Long id);

    void disableRule(Long id);

    List<Map<String, Object>> testRule(RuleTestDTO ruleTestDTO);

    Map<String, Object> sandboxTest(SandboxTestRequest request);

    boolean checkMutex(String mutexGroup);
}
