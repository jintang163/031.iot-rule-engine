package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.VersionDiffResult;
import com.iot.ruleengine.dto.VersionRollbackDTO;
import com.iot.ruleengine.entity.Rule;
import com.iot.ruleengine.entity.RuleVersion;

import java.util.List;

public interface RuleVersionService {

    RuleVersion createVersion(Rule rule, String comment, String changeSummary);

    Page<RuleVersion> listVersions(Long ruleId, Page<RuleVersion> page);

    RuleVersion getVersionById(Long id);

    RuleVersion getVersionByRuleIdAndVersion(Long ruleId, Integer version);

    VersionDiffResult compareVersions(Long ruleId, Integer fromVersion, Integer toVersion);

    VersionDiffResult compareWithCurrent(Long ruleId, Integer version);

    Rule rollback(VersionRollbackDTO dto);

    RuleVersion updateComment(Long versionId, String comment);

    List<RuleVersion> getLatestVersions(Long ruleId, int limit);
}
