package com.iot.ruleengine.history;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rule.history")
public class RuleHistoryProperties {

    private boolean enabled = false;

    private String indexPrefix = "rule_trigger_history_";

    private int retentionDays = 90;

    private int asyncThreads = 4;

    private int asyncQueueCapacity = 10000;
}
