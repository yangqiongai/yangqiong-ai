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
package com.yangqiongai.ai.agent.skill.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.skill.generation.SkillGenerationEngine.GenerationResult;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillDraftInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillGenDraftRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 技能生成
 * @author yangqiong
 */
@Service
public class SkillGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SkillGenerationService.class);

    @Autowired
    private SkillGenerationEngine engine;

    @Autowired
    private SkillGenDraftRepository draftRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillContentValidator contentValidator;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 同步生成技能（LLM提取需求 → 渲染Markdown → 校验 → 保存草稿）
     * @param skillName
     * @param description
     * @return
     */
    public SkillDraft generate(String skillName, String description) {
        GenerationResult specResult = engine.generateSkillSpec(skillName, description);
        if (!specResult.isSuccess()) {
            throw new AiException(AiErrorCode.SKILL_GENERATE_FAILED, "需求提取失败: " + specResult.getErrorMessage());
        }

        GenerationResult renderResult = engine.renderSkillMarkdown(specResult.getSpec());
        if (!renderResult.isSuccess()) {
            throw new AiException(AiErrorCode.SKILL_GENERATE_FAILED, "Markdown渲染失败: " + renderResult.getErrorMessage());
        }

        String draftId = UUID.randomUUID().toString();
        SkillDraftInfo draftInfo = new SkillDraftInfo();
        draftInfo.setDraftId(draftId);
        draftInfo.setSkillName(skillName);
        draftInfo.setSkillDescription(specResult.getSpec());
        draftInfo.setSkillContent(renderResult.getMarkdown());
        draftInfo.setGenerateStatus(DraftStatus.DRAFT_READY.name());
        draftRepository.save(draftInfo);

        log.info("技能生成完成, draftId={}", draftId);
        return toDraft(draftInfo);
    }

    /**
     * 获取草稿
     * @param draftId
     * @return
     */
    public Optional<SkillDraft> getDraft(String draftId) {
        return draftRepository.getByDraftId(draftId).map(this::toDraft);
    }

    /**
     * 确认草稿并保存为正式技能
     * @param draftId
     * @return
     */
    public SkillDefinition confirmDraft(String draftId) {
        SkillDraftInfo draftInfo = draftRepository.getByDraftId(draftId).orElse(null);
        if (draftInfo == null) {
            throw new AiException(AiErrorCode.SKILL_NOT_FOUND, "草稿不存在: " + draftId);
        }
        if (!DraftStatus.DRAFT_READY.name().equals(draftInfo.getGenerateStatus())) {
            throw new AiException(AiErrorCode.SKILL_VALIDATION_FAILED, "草稿状态不是待确认: " + draftInfo.getGenerateStatus());
        }

        SkillContentValidator.ValidationResult validation = contentValidator.validate(draftInfo.getSkillContent());
        if (!validation.isValid()) {
            throw new AiException(AiErrorCode.SKILL_VALIDATION_FAILED, "草稿校验未通过: " + String.join(", ", validation.getErrors()));
        }

        // 保存为正式技能
        SkillDefinition definition = buildSkillDefinition(draftInfo);
        skillRepository.save(definition);

        draftRepository.updateStatus(draftId, DraftStatus.CONFIRMED.name());
        log.info("确认草稿并保存为正式技能, draftId={}, skillId={}", draftId, definition.getSkillId());
        return definition;
    }

    /**
     * 拒绝草稿
     * @param draftId
     */
    public void rejectDraft(String draftId) {
        SkillDraftInfo draftInfo = draftRepository.getByDraftId(draftId).orElse(null);
        if (draftInfo == null) {
            throw new AiException(AiErrorCode.SKILL_NOT_FOUND, "草稿不存在: " + draftId);
        }
        if (!DraftStatus.DRAFT_READY.name().equals(draftInfo.getGenerateStatus())) {
            throw new AiException(AiErrorCode.SKILL_VALIDATION_FAILED, "草稿状态不是待确认: " + draftInfo.getGenerateStatus());
        }
        draftRepository.updateStatus(draftId, DraftStatus.DISCARDED.name());
        log.info("拒绝草稿, draftId={}", draftId);
    }

    /**
     * 草稿状态
     */
    public enum DraftStatus {

        /**
         * 草稿就绪
         */
        DRAFT_READY,

        /**
         * 已确认
         */
        CONFIRMED,

        /**
         * 已丢弃
         */
        DISCARDED
    }

    /**
     * 技能草稿
     * @author yangqiong
     */
    public static class SkillDraft {

        /**
         * 草稿ID
         */
        private String draftId;

        /**
         * 技能名称
         */
        private String skillName;

        /**
         * 技能规范
         */
        private String spec;

        /**
         * Markdown内容
         */
        private String markdown;

        /**
         * 草稿状态
         */
        private DraftStatus status;

        /**
         * 创建时间
         */
        private LocalDateTime createTime;

        public String getDraftId() {
            return draftId;
        }

        public void setDraftId(String draftId) {
            this.draftId = draftId;
        }

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public String getSpec() {
            return spec;
        }

        public void setSpec(String spec) {
            this.spec = spec;
        }

        public String getMarkdown() {
            return markdown;
        }

        public void setMarkdown(String markdown) {
            this.markdown = markdown;
        }

        public DraftStatus getStatus() {
            return status;
        }

        public void setStatus(DraftStatus status) {
            this.status = status;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }

    /**
     * 安全解析草稿状态
     * @param statusStr
     * @return
     */
    private DraftStatus parseDraftStatus(String statusStr) {
        try {
            return DraftStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            log.warn("未知的草稿状态: {}, 默认使用DRAFT_READY", statusStr);
            return DraftStatus.DRAFT_READY;
        }
    }

    /**
     * DraftInfo转Draft
     * @param draftInfo
     * @return
     */
    private SkillDraft toDraft(SkillDraftInfo draftInfo) {
        SkillDraft draft = new SkillDraft();
        draft.setDraftId(draftInfo.getDraftId());
        draft.setSkillName(draftInfo.getSkillName());
        draft.setSpec(draftInfo.getSkillDescription());
        draft.setMarkdown(draftInfo.getSkillContent());
        draft.setStatus(parseDraftStatus(draftInfo.getGenerateStatus()));
        return draft;
    }

    /**
     * 构建SkillDefinition
     * @param draftInfo
     * @return
     */
    private SkillDefinition buildSkillDefinition(SkillDraftInfo draftInfo) {
        SkillDefinition definition = new SkillDefinition();
        definition.setSkillId(draftInfo.getDraftId());
        definition.setSkillName(draftInfo.getSkillName() != null ? draftInfo.getSkillName() : "custom-skill");
        definition.setSkillDescription(draftInfo.getSkillDescription());
        definition.setSkillContent(draftInfo.getSkillContent());
        definition.setSkillVersion(1);
        definition.setSkillType("GENERATED");
        if (draftInfo.getBoundTools() != null) {
            try {
                definition.setBoundTools(objectMapper.readValue(draftInfo.getBoundTools(), new TypeReference<>() {}));
            } catch (JsonProcessingException e) {
                log.warn("解析草稿boundTools失败, draftId={}", draftInfo.getDraftId());
            }
        }
        return definition;
    }
}
