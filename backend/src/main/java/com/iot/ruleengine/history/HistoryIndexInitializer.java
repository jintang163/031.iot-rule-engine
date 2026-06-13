package com.iot.ruleengine.history;

import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "rule.history.enabled", havingValue = "true")
public class HistoryIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(HistoryIndexInitializer.class);

    private static final String ILM_POLICY_NAME = "rule_trigger_history_ilm_policy";
    private static final String INDEX_TEMPLATE_NAME = "rule_trigger_history_template";

    private final RuleHistoryProperties properties;

    private final RestHighLevelClient restHighLevelClient;

    @Autowired
    public HistoryIndexInitializer(
            RuleHistoryProperties properties,
            RestHighLevelClient restHighLevelClient
    ) {
        this.properties = properties;
        this.restHighLevelClient = restHighLevelClient;
    }

    @PostConstruct
    public void init() {
        log.info("========== 规则历史索引初始化 ==========");
        log.info("索引前缀: {}", properties.getIndexPrefix());
        log.info("数据保留天数: {}天", properties.getRetentionDays());

        try {
            createIlmPolicy();
            createIndexTemplate();
            log.info("========== 规则历史索引初始化完成 ==========");
        } catch (Exception e) {
            log.warn("初始化ES索引/ILM策略失败（不影响正常运行，可手动在Kibana中配置）: {}", e.getMessage());
            log.debug("初始化ES失败详情:", e);
        }
    }

    private void createIlmPolicy() throws IOException {
        int retentionDays = properties.getRetentionDays();

        Map<String, Object> policy = new HashMap<>();
        Map<String, Object> phases = new HashMap<>();

        Map<String, Object> hotPhase = new HashMap<>();
        hotPhase.put("min_age", "0ms");
        Map<String, Object> hotActions = new HashMap<>();
        Map<String, Object> rollover = new HashMap<>();
        rollover.put("max_age", "1d");
        rollover.put("max_primary_shard_size", "50gb");
        hotActions.put("rollover", rollover);
        hotActions.put("set_priority", Map.of("priority", 100));
        hotPhase.put("actions", hotActions);

        Map<String, Object> warmPhase = new HashMap<>();
        warmPhase.put("min_age", "7d");
        Map<String, Object> warmActions = new HashMap<>();
        warmActions.put("set_priority", Map.of("priority", 50));
        warmActions.put("shrink", Map.of("number_of_shards", 1));
        warmActions.put("forcemerge", Map.of("max_num_segments", 1));
        warmPhase.put("actions", warmActions);

        Map<String, Object> deletePhase = new HashMap<>();
        deletePhase.put("min_age", retentionDays + "d");
        Map<String, Object> deleteActions = new HashMap<>();
        deleteActions.put("delete", Map.of("delete_searchable_snapshot", true));
        deletePhase.put("actions", deleteActions);

        phases.put("hot", hotPhase);
        phases.put("warm", warmPhase);
        phases.put("delete", deletePhase);

        policy.put("phases", phases);

        Map<String, Object> policyBody = new HashMap<>();
        policyBody.put("policy", policy);

        String policyJson = toJson(policyBody);

        org.elasticsearch.client.indices.PutIndexLifecyclePolicyRequest request =
                new org.elasticsearch.client.indices.PutIndexLifecyclePolicyRequest(ILM_POLICY_NAME);
        request.source(policyJson, org.elasticsearch.xcontent.XContentType.JSON);

        restHighLevelClient.indices().putIndexLifecyclePolicy(request, RequestOptions.DEFAULT);

        log.info("ILM生命周期策略创建成功: policyName={}, 保留天数={}天 (hot->warm(7d)->delete({}d))",
                ILM_POLICY_NAME, retentionDays, retentionDays);
    }

    private void createIndexTemplate() throws IOException {
        String indexPattern = properties.getIndexPrefix() + "*";

        PutIndexTemplateRequest request = new PutIndexTemplateRequest(INDEX_TEMPLATE_NAME);
        request.patterns(indexPattern);

        request.settings(Settings.builder()
                .put("index.number_of_shards", 3)
                .put("index.number_of_replicas", 1)
                .put("index.refresh_interval", "30s")
                .put("index.lifecycle.name", ILM_POLICY_NAME)
                .put("index.lifecycle.rollover_alias", properties.getIndexPrefix() + "alias")
        );

        String mappings = "{\n" +
                "  \"properties\": {\n" +
                "    \"tenantId\": { \"type\": \"long\" },\n" +
                "    \"ruleId\": { \"type\": \"long\" },\n" +
                "    \"ruleName\": { \"type\": \"keyword\" },\n" +
                "    \"engineType\": { \"type\": \"keyword\" },\n" +
                "    \"triggerTime\": { \"type\": \"date\", \"format\": \"strict_date_optional_time||epoch_millis\" },\n" +
                "    \"deviceId\": { \"type\": \"keyword\" },\n" +
                "    \"temperature\": { \"type\": \"double\" },\n" +
                "    \"humidity\": { \"type\": \"double\" },\n" +
                "    \"matchedExpression\": { \"type\": \"text\" },\n" +
                "    \"executionMs\": { \"type\": \"long\" },\n" +
                "    \"createTime\": { \"type\": \"date\", \"format\": \"strict_date_optional_time||epoch_millis\" },\n" +
                "    \"deviceSnapshot\": { \"type\": \"object\", \"enabled\": true },\n" +
                "    \"actions\": {\n" +
                "      \"type\": \"nested\",\n" +
                "      \"properties\": {\n" +
                "        \"actionType\": { \"type\": \"keyword\" },\n" +
                "        \"targetDeviceId\": { \"type\": \"keyword\" },\n" +
                "        \"success\": { \"type\": \"boolean\" },\n" +
                "        \"resultCode\": { \"type\": \"integer\" },\n" +
                "        \"errorMsg\": { \"type\": \"text\" },\n" +
                "        \"params\": { \"type\": \"object\", \"enabled\": true }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        request.mapping(mappings, XContentType.JSON);

        restHighLevelClient.indices().putIndexTemplate(request, RequestOptions.DEFAULT);

        log.info("索引模板创建成功: templateName={}, indexPattern={}, 已绑定ILM策略={}",
                INDEX_TEMPLATE_NAME, indexPattern, ILM_POLICY_NAME);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("序列化ILM策略失败", e);
        }
    }
}
