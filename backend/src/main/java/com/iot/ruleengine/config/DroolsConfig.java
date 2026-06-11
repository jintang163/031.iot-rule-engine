package com.iot.ruleengine.config;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;

@Slf4j
@Configuration
public class DroolsConfig {

    @Value("${drools.rules-path:classpath*:rules/**/*.drl}")
    private String rulesPath;

    @Bean
    public KieContainer kieContainer() {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(rulesPath);

            log.info("开始加载Drools规则文件, 规则路径: {}, 找到文件数量: {}", rulesPath, resources.length);

            for (Resource resource : resources) {
                String ruleFileName = resource.getFilename();
                log.info("加载规则文件: {}", ruleFileName);
                kieFileSystem.write(ResourceFactory.newClassPathResource("rules/" + ruleFileName, "UTF-8"));
            }

            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();

            Results results = kieBuilder.getResults();
            if (results.hasMessages(Message.Level.ERROR)) {
                log.error("Drools规则编译错误: {}", results.getMessages());
                throw new IllegalStateException("Drools规则编译失败: " + results.getMessages());
            }

            KieModule kieModule = kieBuilder.getKieModule();
            KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());
            log.info("Drools规则引擎初始化完成");
            return kieContainer;
        } catch (IOException e) {
            log.error("加载Drools规则文件失败", e);
            log.warn("使用空KieContainer启动，规则功能将不可用");
            return kieServices.newKieClasspathContainer();
        }
    }
}
