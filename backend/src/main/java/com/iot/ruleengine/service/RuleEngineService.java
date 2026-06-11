package com.iot.ruleengine.service;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RuleEngineService {

    private final KieContainer kieContainer;

    @Autowired
    public RuleEngineService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public void executeRules(Object fact) {
        KieSession kieSession = null;
        try {
            kieSession = kieContainer.newKieSession();
            kieSession.insert(fact);
            int firedRules = kieSession.fireAllRules();
            log.debug("执行规则完成，触发规则数量: {}, Fact: {}", firedRules, fact);
        } catch (Exception e) {
            log.error("执行规则引擎异常", e);
        } finally {
            if (kieSession != null) {
                kieSession.dispose();
            }
        }
    }
}
