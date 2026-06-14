package com.iot.ruleengine.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VersionDiffResult {

    private Long ruleId;

    private Integer fromVersion;

    private Integer toVersion;

    private List<DiffItem> diffs;

    private int totalChanges;

    @Data
    public static class DiffItem {
        private String field;
        private String fieldName;
        private String oldValue;
        private String newValue;
        private String changeType;
        private List<Map<String, Object>> nodeChanges;
    }
}
