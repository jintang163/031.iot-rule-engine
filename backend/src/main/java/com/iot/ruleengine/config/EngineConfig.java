package com.iot.ruleengine.config;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Feature;
import com.googlecode.aviator.Options;
import com.iot.ruleengine.drools.DroolsRuleEngine;
import com.iot.ruleengine.drools.RuleExecutionListener;
import com.iot.ruleengine.drools.RuleParser;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.engine.aviator.AviatorRuleEngine;
import com.iot.ruleengine.engine.cache.RuleCompilerCache;
import com.iot.ruleengine.engine.cache.RuleJsonParseCache;
import com.iot.ruleengine.engine.RuleExpressionParser;
import com.iot.ruleengine.repository.RuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.math.MathContext;

@Slf4j
@Configuration
public class EngineConfig {

    @Bean
    public KieServices kieServices() {
        return KieServices.Factory.get();
    }

    @Bean
    public AviatorEvaluatorInstance aviatorEvaluator() {
        log.info("初始化AviatorEvaluatorInstance(独立实例,线程安全)");

        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();

        instance.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EAGER);
        log.info("设置优化级别: EAGER(立即优化编译)");

        instance.setOption(Options.FEATURE_SET, Feature.CompatibleFeature);
        log.info("设置特性集: CompatibleFeature(兼容特性集)");

        instance.setOption(Options.MATH_CONTEXT, MathContext.DECIMAL64);
        log.info("设置数学精度: DECIMAL64(IEEE 754R Decimal64格式, 16位有效数字)");

        log.info("AviatorEvaluatorInstance初始化完成, 实例hashCode: {}", instance.hashCode());
        return instance;
    }

    @Bean
    public RuleCompilerCache ruleCompilerCache(@Lazy AviatorEvaluatorInstance aviatorEvaluator) {
        log.info("创建RuleCompilerCache - Caffeine编译缓存");
        return new RuleCompilerCache(aviatorEvaluator);
    }

    @Bean
    public RuleJsonParseCache ruleJsonParseCache(@Lazy RuleRepository ruleRepository,
                                                  @Lazy RuleExpressionParser ruleExpressionParser) {
        log.info("创建RuleJsonParseCache - Caffeine JSON解析缓存");
        return new RuleJsonParseCache(ruleRepository, ruleExpressionParser);
    }

    @Bean("aviatorRuleEngine")
    @ConditionalOnProperty(name = "rule.engine", havingValue = "aviator", matchIfMissing = true)
    public AviatorRuleEngine aviatorRuleEngine(@Lazy AviatorEvaluatorInstance aviatorEvaluator,
                                           @Lazy RuleCompilerCache ruleCompilerCache,
                                           @Lazy RuleJsonParseCache ruleJsonParseCache,
                                           @Lazy RuleRepository ruleRepository,
                                           @Lazy MeterRegistry meterRegistry) {
        log.info("创建AviatorRuleEngine - Aviator规则引擎实现");
        return new AviatorRuleEngine(
                aviatorEvaluator,
                ruleCompilerCache,
                ruleJsonParseCache,
                ruleRepository,
                meterRegistry
        );
    }

    @Bean("droolsRuleEngine")
    @ConditionalOnProperty(name = "rule.engine", havingValue = "drools")
    public DroolsRuleEngine droolsRuleEngine(KieServices kieServices,
                                            KieContainer kieContainer,
                                            RuleExecutionListener ruleExecutionListener,
                                            RuleParser ruleParser) {
        log.info("创建DroolsRuleEngine - Drools规则引擎实现");
        return new DroolsRuleEngine(
                kieServices,
                kieContainer,
                ruleExecutionListener,
                ruleParser
        );
    }

    @Primary
    @Bean
    public RuleEngine ruleEngine(@Value("${rule.engine:aviator}") String engine,
                                 @Autowired(required = false) @Qualifier("aviatorRuleEngine") Object aviator,
                                 @Autowired(required = false) @Qualifier("droolsRuleEngine") Object drools) {
        log.info("根据配置选择规则引擎实现, rule.engine={}", engine);
        if ("drools".equalsIgnoreCase(engine)) {
            if (drools == null) {
                throw new IllegalStateException("配置为drools引擎，但DroolsRuleEngine未初始化，请检查配置");
            }
            log.info("使用Drools规则引擎");
            return (RuleEngine) drools;
        } else {
            if (aviator == null) {
                throw new IllegalStateException("配置为aviator引擎，但AviatorRuleEngine未初始化，请检查配置");
            }
            log.info("使用Aviator规则引擎");
            return (RuleEngine) aviator;
        }
    }
}
