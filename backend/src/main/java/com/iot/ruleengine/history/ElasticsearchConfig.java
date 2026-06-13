package com.iot.ruleengine.history;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(RuleHistoryProperties.class)
@ConditionalOnProperty(name = "rule.history.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchConfig {

    private final RuleHistoryProperties properties;

    public ElasticsearchConfig(RuleHistoryProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "ruleHistoryExecutor")
    public ThreadPoolTaskExecutor ruleHistoryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getAsyncThreads());
        executor.setMaxPoolSize(properties.getAsyncThreads());
        executor.setQueueCapacity(properties.getAsyncQueueCapacity());
        executor.setThreadNamePrefix("rule-history-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
