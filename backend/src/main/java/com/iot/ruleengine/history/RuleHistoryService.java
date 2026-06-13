package com.iot.ruleengine.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.ruleengine.dto.PageResult;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class RuleHistoryService {

    private static final Logger log = LoggerFactory.getLogger(RuleHistoryService.class);

    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RuleHistoryProperties properties;

    private final ObjectMapper objectMapper;

    @Nullable
    @Autowired(required = false)
    private RestHighLevelClient restHighLevelClient;

    @Nullable
    @Autowired(required = false)
    private ThreadPoolTaskExecutor ruleHistoryExecutor;

    public RuleHistoryService(RuleHistoryProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void recordHistory(RuleTriggerHistory history) {
        if (history == null) {
            return;
        }
        if (ruleHistoryExecutor != null) {
            try {
                ruleHistoryExecutor.execute(() -> doRecordHistory(history));
            } catch (Exception e) {
                log.warn("提交规则历史记录到线程池失败，降级为同步记录", e);
                doRecordHistory(history);
            }
        } else {
            doRecordHistory(history);
        }
    }

    private void doRecordHistory(RuleTriggerHistory history) {
        if (isEnabled()) {
            try {
                String index = properties.getIndexPrefix() + history.getTenantId();
                String docId = (history.getId() != null && !history.getId().isEmpty())
                        ? history.getId()
                        : UUID.randomUUID().toString().replace("-", "");
                history.setId(docId);

                IndexRequest indexRequest = new IndexRequest(index)
                        .id(docId)
                        .source(objectMapper.writeValueAsString(history), XContentType.JSON)
                        .setRefreshPolicy(WriteRequest.RefreshPolicy.NONE);

                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
                log.debug("规则历史记录已写入ES，index={}, docId={}", index, docId);
            } catch (JsonProcessingException e) {
                log.error("序列化规则历史记录失败", e);
            } catch (IOException e) {
                log.warn("写入规则历史记录到ES失败，降级为日志输出", e);
                logHistorySummary(history);
            } catch (Exception e) {
                log.warn("写入规则历史记录到ES发生未知异常，降级为日志输出", e);
                logHistorySummary(history);
            }
        } else {
            logHistorySummary(history);
        }
    }

    private void logHistorySummary(RuleTriggerHistory history) {
        try {
            log.info("[规则历史降级模式] ruleId={}, ruleName={}, deviceId={}, triggerTime={}, actions={}",
                    history.getRuleId(),
                    history.getRuleName(),
                    history.getDeviceId(),
                    history.getTriggerTime(),
                    history.getActions() != null ? history.getActions().size() : 0);
            if (log.isDebugEnabled()) {
                log.debug("规则历史详情: {}", objectMapper.writeValueAsString(history));
            }
        } catch (Exception e) {
            log.warn("打印规则历史摘要失败", e);
        }
    }

    private String clampStartTimeToRetentionDays(String startTime) {
        int retentionDays = properties.getRetentionDays() > 0 ? properties.getRetentionDays() : 90;
        LocalDateTime earliest = LocalDateTime.now().minusDays(retentionDays);
        String earliestStr = earliest.format(DTF);

        if (startTime == null || startTime.isEmpty()) {
            return earliestStr;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startTime.replace(" ", "T"),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (start.isBefore(earliest)) {
                log.debug("请求的起始时间 {} 早于保留天数上限 {}，已自动 clamp", startTime, earliestStr);
                return earliestStr;
            }
        } catch (Exception e) {
            // parse失败，用兜底
        }
        return startTime;
    }

    public PageResult<RuleTriggerHistory> searchByRuleId(
            Long ruleId,
            Long tenantId,
            String startTime,
            String endTime,
            int page,
            int size
    ) {
        PageResult<RuleTriggerHistory> emptyResult = new PageResult<>();
        emptyResult.setRecords(Collections.emptyList());
        emptyResult.setTotal(0L);
        emptyResult.setCurrent((long) page);
        emptyResult.setSize((long) size);
        emptyResult.setPages(0L);

        if (!isEnabled()) {
            return emptyResult;
        }

        try {
            String index = properties.getIndexPrefix() + tenantId;
            SearchRequest searchRequest = new SearchRequest(index);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must(QueryBuilders.termQuery("ruleId", ruleId));
            boolQuery.must(QueryBuilders.termQuery("tenantId", tenantId));

            String clampedStartTime = clampStartTimeToRetentionDays(startTime);
            boolQuery.must(QueryBuilders.rangeQuery("triggerTime").gte(clampedStartTime));

            if (endTime != null && !endTime.isEmpty()) {
                boolQuery.must(QueryBuilders.rangeQuery("triggerTime").lte(endTime));
            }

            sourceBuilder.query(boolQuery);
            sourceBuilder.sort("triggerTime", SortOrder.DESC);

            int from = (page - 1) * size;
            if (from < 0) {
                from = 0;
            }
            sourceBuilder.from(from);
            sourceBuilder.size(size);

            searchRequest.source(sourceBuilder);

            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            List<RuleTriggerHistory> records = new ArrayList<>();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                try {
                    RuleTriggerHistory item = objectMapper.readValue(
                            hit.getSourceAsString(),
                            RuleTriggerHistory.class
                    );
                    if (item.getId() == null || item.getId().isEmpty()) {
                        item.setId(hit.getId());
                    }
                    records.add(item);
                } catch (Exception e) {
                    log.warn("解析ES文档失败，docId={}", hit.getId(), e);
                }
            }

            long total = searchResponse.getHits().getTotalHits().value;
            long pages = (total + size - 1) / size;

            PageResult<RuleTriggerHistory> result = new PageResult<>();
            result.setRecords(records);
            result.setTotal(total);
            result.setCurrent((long) page);
            result.setSize((long) size);
            result.setPages(pages);

            return result;
        } catch (Exception e) {
            log.warn("搜索规则历史记录失败，返回空结果，ruleId={}", ruleId, e);
            return emptyResult;
        }
    }

    public RuleTriggerHistory getSnapshot(String historyId, Long tenantId) {
        if (!isEnabled() || historyId == null || historyId.isEmpty()) {
            return null;
        }

        try {
            SearchRequest searchRequest = new SearchRequest(properties.getIndexPrefix() + "*");
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            boolQuery.must(QueryBuilders.termQuery("_id", historyId));
            boolQuery.must(QueryBuilders.termQuery("tenantId", tenantId));
            int retentionDays = properties.getRetentionDays() > 0 ? properties.getRetentionDays() : 90;
            boolQuery.must(QueryBuilders.rangeQuery("triggerTime")
                    .gte(LocalDateTime.now().minusDays(retentionDays).format(DTF)));

            sourceBuilder.query(boolQuery);
            sourceBuilder.size(1);

            searchRequest.source(sourceBuilder);

            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            if (searchResponse.getHits().getTotalHits().value > 0) {
                SearchHit hit = searchResponse.getHits().getHits()[0];
                RuleTriggerHistory history = objectMapper.readValue(
                        hit.getSourceAsString(),
                        RuleTriggerHistory.class
                );
                if (history.getId() == null || history.getId().isEmpty()) {
                    history.setId(hit.getId());
                }
                return history;
            }
        } catch (Exception e) {
            log.warn("获取规则历史快照失败，historyId={}", historyId, e);
        }

        return null;
    }

    @Scheduled(cron = "0 10 3 * * ?")
    public void cleanExpiredHistory() {
        if (!isEnabled()) {
            return;
        }
        int retentionDays = properties.getRetentionDays() > 0 ? properties.getRetentionDays() : 90;
        String cutoffStr = LocalDateTime.now().minusDays(retentionDays)
                .atZone(ZoneId.systemDefault()).toInstant().toString();

        log.info("开始清理{}天前的规则历史记录，截止时间={}", retentionDays, cutoffStr);

        try {
            DeleteByQueryRequest request = new DeleteByQueryRequest(properties.getIndexPrefix() + "*");
            request.setQuery(QueryBuilders.rangeQuery("triggerTime").lt(cutoffStr));
            request.setBatchSize(1000);
            request.setScroll("5m");
            request.setConflicts("proceed");

            BulkByScrollResponse response = restHighLevelClient.deleteByQuery(request, RequestOptions.DEFAULT);
            long deleted = response.getDeleted();
            long failures = response.getBulkFailures().size();

            log.info("规则历史过期清理完成，删除文档数={}, 失败数={}, 耗时={}ms",
                    deleted, failures, response.getTook().getMillis());

            if (failures > 0) {
                log.warn("规则历史清理存在{}条失败，详情: {}", failures, response.getBulkFailures());
            }
        } catch (IOException e) {
            log.warn("清理过期规则历史记录失败(IO异常)", e);
        } catch (Exception e) {
            log.warn("清理过期规则历史记录失败", e);
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled() && restHighLevelClient != null;
    }
}
