package com.iot.ruleengine.history;

import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
@ConditionalOnProperty(name = "rule.history.enabled", havingValue = "true")
public class HistoryIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(HistoryIndexInitializer.class);

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
        log.info("建议: 请在Elasticsearch中配置ILM策略，设置索引生命周期为{}天", properties.getRetentionDays());

        try {
            createIndexTemplate();
        } catch (Exception e) {
            log.warn("创建索引模板失败，不影响正常运行（请手动配置ILM策略）", e);
        }
    }

    private void createIndexTemplate() throws IOException {
        String templateName = "rule_trigger_history_template";
        String indexPattern = properties.getIndexPrefix() + "*";

        PutIndexTemplateRequest request = new PutIndexTemplateRequest(templateName);
        request.patterns(indexPattern);

        request.settings(Settings.builder()
                .put("index.number_of_shards", 3)
                .put("index.number_of_replicas", 1)
                .put("index.refresh_interval", "30s")
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
                "    \"deviceSnapshot\": { \"type\": \"object\", \"enabled\": false },\n" +
                "    \"actions\": {\n" +
                "      \"type\": \"nested\",\n" +
                "      \"properties\": {\n" +
                "        \"actionType\": { \"type\": \"keyword\" },\n" +
                "        \"targetDeviceId\": { \"type\": \"keyword\" },\n" +
                "        \"success\": { \"type\": \"boolean\" },\n" +
                "        \"resultCode\": { \"type\": \"integer\" },\n" +
                "        \"errorMsg\": { \"type\": \"text\" },\n" +
                "        \"params\": { \"type\": \"object\", \"enabled\": false }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        request.mapping(mappings, XContentType.JSON);

        restHighLevelClient.indices().putTemplate(request, RequestOptions.DEFAULT);

        log.info("索引模板创建成功: templateName={}, indexPattern={}, retentionDays={}",
                templateName, indexPattern, properties.getRetentionDays());
        log.warn("注意: ILM生命周期策略需要手动配置，模板中未自动绑定ILM策略。");
    }
}
