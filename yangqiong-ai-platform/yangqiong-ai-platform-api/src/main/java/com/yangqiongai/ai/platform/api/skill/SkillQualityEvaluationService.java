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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能内容质量评测
 * @author yangqiong
 */
@Service
public class SkillQualityEvaluationService {

    /**
     * 质量评测提示词(占位符{content}/{boundTools}运行时替换)
     */
    private static final String EVAL_PROMPT = "你是技能(Skill)内容质量评审专家。请评审以下技能的Markdown指令内容，按四个维度打分(各维度0到100分)：\n"
            + "1. 结构完整性(权重25%)：角色定义、触发条件、执行步骤、约束规则、输出格式是否齐备\n"
            + "2. 指令清晰度(权重30%)：指令无歧义、可执行、粒度恰当\n"
            + "3. 安全合规(权重25%)：无提示词注入风险、无越权指令、无危险系统命令\n"
            + "4. 工具匹配度(权重20%)：声明绑定的工具与内容中引用的工具是否一致\n"
            + "总分 = 各维度得分 × 权重之和，四舍五入保留1位小数。\n"
            + "技能内容：\n{content}\n\n"
            + "技能声明绑定的工具列表：{boundTools}\n\n"
            + "仅输出JSON，不要输出任何其他内容，格式：\n"
            + "{\"totalScore\":总分,\"dimensions\":{\"structure\":分数,\"clarity\":分数,\"security\":分数,\"toolMatch\":分数},\"suggestions\":\"改进建议(不超过300字)\"}";

    /**
     * 提示词中技能内容的最大长度，防止超长内容撑爆上下文
     */
    private static final int MAX_CONTENT_LENGTH = 16000;

    /**
     * 改进建议最大长度
     */
    private static final int MAX_SUGGESTIONS_LENGTH = 300;

    @Autowired
    private SkillConfigRepository skillConfigRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${ai.skill.eval.model-code:defaultAgent}")
    private String modelCode;

    /**
     * 对技能当前版本执行内容质量评测（单次LLM调用，结果落库至版本快照行并发布评测事件）
     * @param skillId
     * @return
     */
    public SkillEvalResultDto evaluate(String skillId) {
        SkillDefinition skill = skillConfigRepository.getBySkillId(skillId);
        if (skill == null) {
            // 数据库无记录时回退到组合仓库（classpath内置技能），评分行由saveEvaluation自动补建
            skill = skillRepository.findById(skillId).orElse(null);
        }
        if (skill == null) {
            throw new AiException(AiErrorCode.SKILL_NOT_FOUND, "技能不存在: " + skillId);
        }
        if (languageModelFactory == null) {
            throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR, "模型工厂未装配，无法执行质量评测");
        }
        int version = skill.getSkillVersion() > 0 ? skill.getSkillVersion() : 1;
        // 评测前的上一版本评分，用于事件消费方做版本环比退化判定
        SkillVersionInfo prevSnapshot = version > 1 ? skillConfigRepository.getVersion(skillId, version - 1) : null;
        BigDecimal prevScore = prevSnapshot != null ? prevSnapshot.getQualityScore() : null;

        String response;
        try {
            AgentMessage userMsg = AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(List.of(AgentTextBlock.builder().text(buildPrompt(skill)).build()))
                    .build();
            response = languageModelFactory.generateText(modelCode, List.of(userMsg));
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.MODEL_CALL_FAILED, "评测模型调用失败: " + e.getMessage());
        }

        EvalResult result = parseEvalResult(response);
        skillConfigRepository.saveEvaluation(skillId, version, skill.getSkillContent(),
                result.totalScore(), result.dimensionsJson(), modelCode);

        SkillEvalResultDto dto = new SkillEvalResultDto();
        dto.setSkillId(skillId);
        dto.setVersion(version);
        dto.setQualityScore(result.totalScore());
        dto.setDimensions(result.dimensions());
        dto.setSuggestions(result.suggestions());
        dto.setEvalModel(modelCode);
        dto.setEvaluatedTime(LocalDateTime.now());
        eventPublisher.publishEvent(new SkillEvaluatedEvent(skillId, ScopeContext.getScopeId(), version,
                result.totalScore(), prevScore));
        return dto;
    }

    /**
     * 构建评测提示词
     * @param skill
     * @return
     */
    private String buildPrompt(SkillDefinition skill) {
        String content = skill.getSkillContent() == null ? "" : skill.getSkillContent();
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        String boundTools = (skill.getBoundTools() == null || skill.getBoundTools().isEmpty())
                ? "无" : String.join(", ", skill.getBoundTools());
        return EVAL_PROMPT
                .replace("{content}", content)
                .replace("{boundTools}", boundTools);
    }

    /**
     * 解析LLM返回的评测JSON(容错提取首个JSON对象)
     * @param response
     * @return
     */
    private EvalResult parseEvalResult(String response) {
        if (response == null || response.isBlank()) {
            throw new AiException(AiErrorCode.MODEL_CALL_FAILED, "评测模型返回为空");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiException(AiErrorCode.MODEL_CALL_FAILED, "评测结果解析失败，模型未返回JSON");
        }
        String json = response.substring(start, end + 1);
        try {
            JsonNode root = objectMapper.readTree(json);
            BigDecimal total = new BigDecimal(root.path("totalScore").asText("0"))
                    .min(BigDecimal.valueOf(100)).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            JsonNode dims = root.path("dimensions");
            Map<String, BigDecimal> dimensionMap = new LinkedHashMap<>();
            dimensionMap.put("structure", new BigDecimal(dims.path("structure").asText("0")));
            dimensionMap.put("clarity", new BigDecimal(dims.path("clarity").asText("0")));
            dimensionMap.put("security", new BigDecimal(dims.path("security").asText("0")));
            dimensionMap.put("toolMatch", new BigDecimal(dims.path("toolMatch").asText("0")));
            String suggestions = root.path("suggestions").asText("");
            if (suggestions.length() > MAX_SUGGESTIONS_LENGTH) {
                suggestions = suggestions.substring(0, MAX_SUGGESTIONS_LENGTH);
            }
            return new EvalResult(total, dimensionMap, json, suggestions);
        } catch (NumberFormatException e) {
            throw new AiException(AiErrorCode.MODEL_CALL_FAILED, "评测结果解析失败，评分不是有效数字");
        } catch (Exception e) {
            throw new AiException(AiErrorCode.MODEL_CALL_FAILED, "评测结果解析失败，模型未按要求返回JSON");
        }
    }

    /**
     * 评测解析结果
     * @param totalScore
     * @param dimensions
     * @param dimensionsJson
     * @param suggestions
     */
    private record EvalResult(BigDecimal totalScore, Map<String, BigDecimal> dimensions,
                              String dimensionsJson, String suggestions) {
    }
}
