package com.iot.ruleengine.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class RuleRecommendationDTO {

    private String recommendationId;

    private String title;

    private String description;

    private String reason;

    private BigDecimal confidence;

    private String category;

    private String icon;

    private String templateRuleName;

    private String templateDescription;

    private List<ConditionTemplate> conditions;

    private List<ActionTemplate> actions;

    private Map<String, Object> ruleJson;

    private List<String> relatedDeviceIds;

    private Integer manualActionCount;

    private Integer suggestionPriority;

    private LocalDateTime generatedAt;

    @Data
    public static class ConditionTemplate {
        private String deviceId;
        private String deviceName;
        private String field;
        private String fieldLabel;
        private String operator;
        private Object value;
        private String label;
    }

    @Data
    public static class ActionTemplate {
        private String deviceId;
        private String deviceName;
        private String action;
        private String actionLabel;
        private Map<String, Object> params;
        private String label;
    }
}
