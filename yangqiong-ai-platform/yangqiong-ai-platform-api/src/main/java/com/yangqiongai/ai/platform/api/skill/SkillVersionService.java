/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.platform.api.skill;

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 技能版本管理
 * @author yangqiong
 */
@Service
public class SkillVersionService {

    @Autowired
    private SkillConfigRepository skillConfigRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 分页查询版本历史（版本号倒序）
     * @param skillId
     * @param pageNum
     * @param pageSize
     * @return
     */
    public VersionPage pageVersions(String skillId, int pageNum, int pageSize) {
        List<SkillVersionInfo> infos = skillConfigRepository.listVersions(skillId, pageNum, pageSize);
        long total = skillConfigRepository.countVersions(skillId);
        List<SkillVersionDto> records = infos.stream().map(this::toDto).toList();
        return new VersionPage(records, total);
    }

    /**
     * 查询单版本快照详情（含内容全文）
     * @param skillId
     * @param version
     * @return
     */
    public SkillVersionDetailDto getVersionDetail(String skillId, int version) {
        SkillVersionInfo info = skillConfigRepository.getVersion(skillId, version);
        if (info == null) {
            throw new AiException(AiErrorCode.SKILL_NOT_FOUND, "版本快照不存在: " + skillId + " v" + version);
        }
        SkillVersionDetailDto detail = new SkillVersionDetailDto();
        fillDto(detail, info);
        detail.setSkillContent(info.getSkillContent());
        return detail;
    }

    /**
     * 回滚技能内容至指定版本（保留当前配置仅回退skillContent，走saveSkill产生新版本号）
     * @param skillId
     * @param targetVersion
     * @param remark
     * @param operator
     * @return
     */
    public SkillDefinition rollback(String skillId, Integer targetVersion, String remark, String operator) {
        if (targetVersion == null || targetVersion < 1) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "targetVersion必须为正整数");
        }
        SkillDefinition current = skillConfigRepository.getBySkillId(skillId);
        if (current == null) {
            throw new AiException(AiErrorCode.SKILL_NOT_FOUND, "技能不存在: " + skillId);
        }
        if (targetVersion == current.getSkillVersion()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目标版本与当前版本相同: " + targetVersion);
        }
        SkillVersionInfo snapshot = skillConfigRepository.getVersion(skillId, targetVersion);
        if (snapshot == null) {
            throw new AiException(AiErrorCode.SKILL_NOT_FOUND, "目标版本快照不存在: v" + targetVersion);
        }
        // 仅回退内容，绑定工具/资源/信任等级/启用状态等保留当前配置
        SkillDefinition rollbackDef = buildRollbackDefinition(current, snapshot.getSkillContent());
        String changeLog = (remark != null && !remark.isBlank()) ? remark : "回滚至版本" + targetVersion;
        skillConfigRepository.saveSkill(rollbackDef, operator, changeLog);
        publishChanged(SkillChangedEvent.ACTION_ROLLBACK, skillId, operator, rollbackDef.getSkillVersion());
        return rollbackDef;
    }

    /**
     * 发布技能变更事件（saveSkill成功落库后调用，版本号取当前最新版本）
     * @param action
     * @param skillId
     * @param operator
     */
    public void publishChanged(String action, String skillId, String operator) {
        publishChanged(action, skillId, operator, null);
    }

    /**
     * 发布技能变更事件（版本号明确时直接传入，避免重复查询）
     * @param action
     * @param skillId
     * @param operator
     * @param newVersion
     */
    public void publishChanged(String action, String skillId, String operator, Integer newVersion) {
        int version;
        if (newVersion != null) {
            version = newVersion;
        } else {
            SkillDefinition latest = skillConfigRepository.getBySkillId(skillId);
            version = latest != null ? latest.getSkillVersion() : 0;
        }
        eventPublisher.publishEvent(new SkillChangedEvent(action, skillId, ScopeContext.getScopeId(), operator, version));
    }

    /**
     * 构建回滚用的技能定义（当前配置+目标版本内容）
     * @param current
     * @param rollbackContent
     * @return
     */
    private SkillDefinition buildRollbackDefinition(SkillDefinition current, String rollbackContent) {
        SkillDefinition def = new SkillDefinition();
        def.setSkillId(current.getSkillId());
        def.setSkillName(current.getSkillName());
        def.setSkillDescription(current.getSkillDescription());
        def.setSkillType(current.getSkillType());
        def.setSkillContent(rollbackContent);
        def.setBoundTools(current.getBoundTools());
        def.setResources(current.getResources());
        def.setExecution(current.getExecution());
        def.setDependencies(current.getDependencies());
        def.setPresetParameters(current.getPresetParameters());
        def.setConditions(current.getConditions());
        def.setTrustLevel(current.getTrustLevel());
        return def;
    }

    /**
     * 版本快照转列表DTO（指纹截前8位）
     * @param info
     * @return
     */
    private SkillVersionDto toDto(SkillVersionInfo info) {
        SkillVersionDto dto = new SkillVersionDto();
        fillDto(dto, info);
        return dto;
    }

    /**
     * 填充公共字段
     * @param dto
     * @param info
     */
    private void fillDto(SkillVersionDto dto, SkillVersionInfo info) {
        dto.setVersion(info.getVersion());
        dto.setChangeLog(info.getChangeLog());
        String fingerprint = info.getFingerprint();
        if (fingerprint != null && fingerprint.length() > 8) {
            fingerprint = fingerprint.substring(0, 8);
        }
        dto.setFingerprint(fingerprint);
        dto.setCreateUser(info.getCreateUser());
        dto.setCreateTime(info.getCreateTime());
        dto.setQualityScore(info.getQualityScore());
        dto.setEvalDimensions(info.getEvalDimensions());
        dto.setEvalModel(info.getEvalModel());
        dto.setEvaluatedTime(info.getEvaluatedTime());
    }

    /**
     * 版本分页结果
     * @author yangqiong
     */
    public static class VersionPage {

        /**
         * 当前页版本列表
         */
        private final List<SkillVersionDto> records;

        /**
         * 版本总数
         */
        private final long total;

        /**
         * @param records
         * @param total
         */
        public VersionPage(List<SkillVersionDto> records, long total) {
            this.records = records;
            this.total = total;
        }

        public List<SkillVersionDto> getRecords() {
            return records;
        }

        public long getTotal() {
            return total;
        }
    }
}
