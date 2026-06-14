package com.iot.ruleengine.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.VersionDiffResult;
import com.iot.ruleengine.dto.VersionRollbackDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleVersion;
import com.iot.ruleengine.exception.BusinessException;
import com.iot.ruleengine.engine.RuleEngine;
import com.iot.ruleengine.repository.RuleRepository;
import com.iot.ruleengine.repository.RuleVersionRepository;
import com.iot.ruleengine.service.RuleVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RuleVersionServiceImpl implements RuleVersionService {

    private final RuleVersionRepository ruleVersionRepository;
    private final RuleRepository ruleRepository;
    private final RuleEngine ruleEngine;

    private static final Map<String, String> FIELD_NAME_MAP = new HashMap<>();

    static {
        FIELD_NAME_MAP.put("name", "规则名称");
        FIELD_NAME_MAP.put("description", "规则描述");
        FIELD_NAME_MAP.put("ruleJson", "规则画布");
        FIELD_NAME_MAP.put("status", "状态");
        FIELD_NAME_MAP.put("priority", "优先级");
        FIELD_NAME_MAP.put("mutexGroup", "互斥组");
        FIELD_NAME_MAP.put("windowEnabled", "时间窗口");
        FIELD_NAME_MAP.put("windowType", "窗口类型");
        FIELD_NAME_MAP.put("windowDuration", "窗口时长");
        FIELD_NAME_MAP.put("windowAggregation", "窗口聚合函数");
        FIELD_NAME_MAP.put("windowField", "窗口聚合字段");
        FIELD_NAME_MAP.put("windowOperator", "窗口比较运算符");
        FIELD_NAME_MAP.put("windowThreshold", "窗口阈值");
        FIELD_NAME_MAP.put("cooldownSeconds", "冷却时间");
        FIELD_NAME_MAP.put("chainTriggerEnabled", "规则链触发");
        FIELD_NAME_MAP.put("chainNextRuleIds", "后续规则ID");
        FIELD_NAME_MAP.put("chainDisableSelf", "禁用自身");
    }

    @Autowired
    public RuleVersionServiceImpl(RuleVersionRepository ruleVersionRepository,
                                   RuleRepository ruleRepository,
                                   RuleEngine ruleEngine) {
        this.ruleVersionRepository = ruleVersionRepository;
        this.ruleRepository = ruleRepository;
        this.ruleEngine = ruleEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleVersion createVersion(Rule rule, String comment, String changeSummary) {
        if (rule == null || rule.getId() == null) {
            throw new BusinessException("规则不存在，无法创建版本");
        }

        int nextVersion = getNextVersion(rule.getId());

        RuleVersion version = new RuleVersion();
        BeanUtils.copyProperties(rule, version);
        version.setId(null);
        version.setRuleId(rule.getId());
        version.setVersion(nextVersion);
        version.setComment(comment);
        version.setChangeSummary(changeSummary);

        if (version.getTenantId() == null) {
            version.setTenantId(rule.getTenantId());
        }

        ruleVersionRepository.insert(version);
        log.info("创建规则版本成功: ruleId={}, version={}", rule.getId(), nextVersion);

        return version;
    }

    @Override
    public Page<RuleVersion> listVersions(Long ruleId, Page<RuleVersion> page) {
        QueryWrapper<RuleVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("rule_id", ruleId);
        queryWrapper.orderByDesc("version");
        return ruleVersionRepository.selectPage(page, queryWrapper);
    }

    @Override
    public RuleVersion getVersionById(Long id) {
        RuleVersion version = ruleVersionRepository.selectById(id);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        return version;
    }

    @Override
    public RuleVersion getVersionByRuleIdAndVersion(Long ruleId, Integer version) {
        QueryWrapper<RuleVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("rule_id", ruleId)
                .eq("version", version);
        RuleVersion ruleVersion = ruleVersionRepository.selectOne(queryWrapper);
        if (ruleVersion == null) {
            throw new BusinessException("版本不存在");
        }
        return ruleVersion;
    }

    @Override
    public VersionDiffResult compareVersions(Long ruleId, Integer fromVersion, Integer toVersion) {
        RuleVersion from = getVersionByRuleIdAndVersion(ruleId, fromVersion);
        RuleVersion to = getVersionByRuleIdAndVersion(ruleId, toVersion);
        return buildDiffResult(ruleId, fromVersion, toVersion, from, to);
    }

    @Override
    public VersionDiffResult compareWithCurrent(Long ruleId, Integer version) {
        Rule rule = ruleRepository.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("规则不存在");
        }
        RuleVersion targetVersion = getVersionByRuleIdAndVersion(ruleId, version);

        RuleVersion currentVersion = new RuleVersion();
        BeanUtils.copyProperties(rule, currentVersion);

        return buildDiffResult(ruleId, version, null, targetVersion, currentVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Rule rollback(VersionRollbackDTO dto) {
        Rule rule = ruleRepository.selectById(dto.getRuleId());
        if (rule == null) {
            throw new BusinessException("规则不存在");
        }

        RuleVersion targetVersion = getVersionByRuleIdAndVersion(dto.getRuleId(), dto.getVersion());

        boolean wasEnabled = rule.getStatus() != null && rule.getStatus() == 1;

        BeanUtils.copyProperties(targetVersion, rule, "id", "createTime", "updateTime");
        rule.setId(dto.getRuleId());
        rule.setStatus(0);

        ruleRepository.updateById(rule);

        if (wasEnabled) {
            try {
                ruleEngine.unregisterRule(dto.getRuleId());
            } catch (Exception e) {
                log.warn("回滚时从引擎卸载规则失败: {}", e.getMessage());
            }
        }

        String rollbackComment = dto.getComment();
        if (!StringUtils.hasText(rollbackComment)) {
            rollbackComment = "回滚至版本 v" + dto.getVersion();
        }
        createVersion(rule, rollbackComment, "回滚操作");

        log.info("规则回滚成功: ruleId={}, targetVersion={}", dto.getRuleId(), dto.getVersion());

        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleVersion updateComment(Long versionId, String comment) {
        RuleVersion version = getVersionById(versionId);
        version.setComment(comment);
        ruleVersionRepository.updateById(version);
        return version;
    }

    @Override
    public List<RuleVersion> getLatestVersions(Long ruleId, int limit) {
        QueryWrapper<RuleVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("rule_id", ruleId)
                .orderByDesc("version")
                .last("LIMIT " + limit);
        return ruleVersionRepository.selectList(queryWrapper);
    }

    private int getNextVersion(Long ruleId) {
        QueryWrapper<RuleVersion> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("rule_id", ruleId)
                .orderByDesc("version")
                .last("LIMIT 1");
        RuleVersion latest = ruleVersionRepository.selectOne(queryWrapper);
        if (latest == null || latest.getVersion() == null) {
            return 1;
        }
        return latest.getVersion() + 1;
    }

    private VersionDiffResult buildDiffResult(Long ruleId, Integer fromVersion, Integer toVersion,
                                               RuleVersion from, RuleVersion to) {
        VersionDiffResult result = new VersionDiffResult();
        result.setRuleId(ruleId);
        result.setFromVersion(fromVersion);
        result.setToVersion(toVersion);

        List<VersionDiffResult.DiffItem> diffs = new ArrayList<>();

        compareBasicField(diffs, "name", from.getName(), to.getName());
        compareBasicField(diffs, "description", from.getDescription(), to.getDescription());
        compareBasicField(diffs, "priority",
                from.getPriority() != null ? String.valueOf(from.getPriority()) : null,
                to.getPriority() != null ? String.valueOf(to.getPriority()) : null);
        compareBasicField(diffs, "mutexGroup", from.getMutexGroup(), to.getMutexGroup());
        compareBasicField(diffs, "status",
                from.getStatus() != null ? (from.getStatus() == 1 ? "启用" : "禁用") : null,
                to.getStatus() != null ? (to.getStatus() == 1 ? "启用" : "禁用") : null);

        compareWindowFields(diffs, from, to);
        compareChainFields(diffs, from, to);
        compareCooldownField(diffs, from, to);

        compareRuleJson(diffs, from.getRuleJson(), to.getRuleJson());

        result.setDiffs(diffs);
        result.setTotalChanges(diffs.size());

        return result;
    }

    private void compareBasicField(List<VersionDiffResult.DiffItem> diffs, String field,
                                    String oldVal, String newVal) {
        boolean oldEmpty = !StringUtils.hasText(oldVal);
        boolean newEmpty = !StringUtils.hasText(newVal);

        if (oldEmpty && newEmpty) {
            return;
        }

        if (oldEmpty || newEmpty || !oldVal.equals(newVal)) {
            VersionDiffResult.DiffItem item = new VersionDiffResult.DiffItem();
            item.setField(field);
            item.setFieldName(FIELD_NAME_MAP.getOrDefault(field, field));
            item.setOldValue(oldVal);
            item.setNewValue(newVal);

            if (oldEmpty) {
                item.setChangeType("ADD");
            } else if (newEmpty) {
                item.setChangeType("REMOVE");
            } else {
                item.setChangeType("MODIFY");
            }

            diffs.add(item);
        }
    }

    private void compareWindowFields(List<VersionDiffResult.DiffItem> diffs, RuleVersion from, RuleVersion to) {
        compareBasicField(diffs, "windowEnabled",
                from.getWindowEnabled() != null ? (from.getWindowEnabled() == 1 ? "启用" : "禁用") : null,
                to.getWindowEnabled() != null ? (to.getWindowEnabled() == 1 ? "启用" : "禁用") : null);
        compareBasicField(diffs, "windowType", from.getWindowType(), to.getWindowType());
        compareBasicField(diffs, "windowDuration",
                from.getWindowDuration() != null ? String.valueOf(from.getWindowDuration()) : null,
                to.getWindowDuration() != null ? String.valueOf(to.getWindowDuration()) : null);
        compareBasicField(diffs, "windowAggregation", from.getWindowAggregation(), to.getWindowAggregation());
        compareBasicField(diffs, "windowField", from.getWindowField(), to.getWindowField());
        compareBasicField(diffs, "windowOperator", from.getWindowOperator(), to.getWindowOperator());
        compareBasicField(diffs, "windowThreshold",
                from.getWindowThreshold() != null ? from.getWindowThreshold().toPlainString() : null,
                to.getWindowThreshold() != null ? to.getWindowThreshold().toPlainString() : null);
    }

    private void compareChainFields(List<VersionDiffResult.DiffItem> diffs, RuleVersion from, RuleVersion to) {
        compareBasicField(diffs, "chainTriggerEnabled",
                from.getChainTriggerEnabled() != null ? (from.getChainTriggerEnabled() == 1 ? "启用" : "禁用") : null,
                to.getChainTriggerEnabled() != null ? (to.getChainTriggerEnabled() == 1 ? "启用" : "禁用") : null);
        compareBasicField(diffs, "chainNextRuleIds", from.getChainNextRuleIds(), to.getChainNextRuleIds());
        compareBasicField(diffs, "chainDisableSelf",
                from.getChainDisableSelf() != null ? (from.getChainDisableSelf() == 1 ? "是" : "否") : null,
                to.getChainDisableSelf() != null ? (to.getChainDisableSelf() == 1 ? "是" : "否") : null);
    }

    private void compareCooldownField(List<VersionDiffResult.DiffItem> diffs, RuleVersion from, RuleVersion to) {
        compareBasicField(diffs, "cooldownSeconds",
                from.getCooldownSeconds() != null ? String.valueOf(from.getCooldownSeconds()) : null,
                to.getCooldownSeconds() != null ? String.valueOf(to.getCooldownSeconds()) : null);
    }

    private void compareRuleJson(List<VersionDiffResult.DiffItem> diffs, String fromJson, String toJson) {
        boolean fromEmpty = !StringUtils.hasText(fromJson);
        boolean toEmpty = !StringUtils.hasText(toJson);

        if (fromEmpty && toEmpty) {
            return;
        }

        if (fromEmpty || toEmpty) {
            VersionDiffResult.DiffItem item = new VersionDiffResult.DiffItem();
            item.setField("ruleJson");
            item.setFieldName("规则画布");
            item.setOldValue(fromJson);
            item.setNewValue(toJson);
            item.setChangeType(fromEmpty ? "ADD" : "REMOVE");
            diffs.add(item);
            return;
        }

        try {
            JSONObject fromObj = JSON.parseObject(fromJson);
            JSONObject toObj = JSON.parseObject(toJson);

            JSONArray fromNodes = fromObj.getJSONArray("nodes");
            JSONArray toNodes = toObj.getJSONArray("nodes");

            List<Map<String, Object>> nodeChanges = compareNodes(fromNodes, toNodes);

            if (!nodeChanges.isEmpty()) {
                VersionDiffResult.DiffItem item = new VersionDiffResult.DiffItem();
                item.setField("ruleJson");
                item.setFieldName("规则画布");
                item.setChangeType("MODIFY");
                item.setNodeChanges(nodeChanges);
                item.setOldValue("画布变更: " + nodeChanges.size() + " 处");
                item.setNewValue("画布变更: " + nodeChanges.size() + " 处");
                diffs.add(item);
            }
        } catch (Exception e) {
            log.warn("解析规则JSON进行对比失败: {}", e.getMessage());
            if (!fromJson.equals(toJson)) {
                VersionDiffResult.DiffItem item = new VersionDiffResult.DiffItem();
                item.setField("ruleJson");
                item.setFieldName("规则画布");
                item.setOldValue("规则画布已变更");
                item.setNewValue("规则画布已变更");
                item.setChangeType("MODIFY");
                diffs.add(item);
            }
        }
    }

    private List<Map<String, Object>> compareNodes(JSONArray fromNodes, JSONArray toNodes) {
        List<Map<String, Object>> changes = new ArrayList<>();

        Map<String, JSONObject> fromNodeMap = new HashMap<>();
        Map<String, JSONObject> toNodeMap = new HashMap<>();

        if (fromNodes != null) {
            for (int i = 0; i < fromNodes.size(); i++) {
                JSONObject node = fromNodes.getJSONObject(i);
                String id = node.getString("id");
                fromNodeMap.put(id, node);
            }
        }

        if (toNodes != null) {
            for (int i = 0; i < toNodes.size(); i++) {
                JSONObject node = toNodes.getJSONObject(i);
                String id = node.getString("id");
                toNodeMap.put(id, node);
            }
        }

        for (Map.Entry<String, JSONObject> entry : toNodeMap.entrySet()) {
            String id = entry.getKey();
            JSONObject toNode = entry.getValue();

            if (!fromNodeMap.containsKey(id)) {
                Map<String, Object> change = new HashMap<>();
                change.put("nodeId", id);
                change.put("changeType", "ADD");
                change.put("label", getNodeLabel(toNode));
                change.put("type", toNode.getString("type"));
                changes.add(change);
            }
        }

        for (Map.Entry<String, JSONObject> entry : fromNodeMap.entrySet()) {
            String id = entry.getKey();
            JSONObject fromNode = entry.getValue();

            if (!toNodeMap.containsKey(id)) {
                Map<String, Object> change = new HashMap<>();
                change.put("nodeId", id);
                change.put("changeType", "REMOVE");
                change.put("label", getNodeLabel(fromNode));
                change.put("type", fromNode.getString("type"));
                changes.add(change);
            }
        }

        for (Map.Entry<String, JSONObject> entry : fromNodeMap.entrySet()) {
            String id = entry.getKey();
            JSONObject fromNode = entry.getValue();

            if (toNodeMap.containsKey(id)) {
                JSONObject toNode = toNodeMap.get(id);
                JSONObject fromData = fromNode.getJSONObject("data");
                JSONObject toData = toNode.getJSONObject("data");

                if (fromData != null && toData != null) {
                    String fromLabel = fromData.getString("label");
                    String toLabel = toData.getString("label");

                    if ((fromLabel != null && !fromLabel.equals(toLabel))
                            || (toLabel != null && !toLabel.equals(fromLabel))) {
                        Map<String, Object> change = new HashMap<>();
                        change.put("nodeId", id);
                        change.put("changeType", "MODIFY");
                        change.put("oldLabel", fromLabel);
                        change.put("newLabel", toLabel);
                        change.put("type", fromNode.getString("type"));
                        changes.add(change);
                    }
                }
            }
        }

        return changes;
    }

    private String getNodeLabel(JSONObject node) {
        if (node == null) return "";
        JSONObject data = node.getJSONObject("data");
        if (data != null && data.containsKey("label")) {
            return data.getString("label");
        }
        return node.getString("id");
    }
}
