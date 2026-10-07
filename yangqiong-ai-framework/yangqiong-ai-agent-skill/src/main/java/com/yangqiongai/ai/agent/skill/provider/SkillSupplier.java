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
package com.yangqiongai.ai.agent.skill.provider;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.resolver.ConditionalSkillMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 技能供给
 * @author yangqiong
 */
@Service
public class SkillSupplier {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private static final String RUNTIME_CONTEXT_HEADER = "[运行时上下文]";

    @Autowired
    private ConditionalSkillMatcher conditionalSkillMatcher;

    /**
     * 判断是否支持该请求
     * @param request
     * @param skillId
     * @return
     */
    public boolean canHandle(AgentRequest request, String skillId) {
        return conditionalSkillMatcher.matches(request);
    }

    /**
     * 根据任务上下文定制技能内容
     * @param request
     * @param skillId
     * @param baseSkill
     * @return
     */
    public SkillDefinition customize(AgentRequest request, String skillId, SkillDefinition baseSkill) {
        if (baseSkill == null || request == null) {
            return baseSkill;
        }
        if (!conditionalSkillMatcher.isSkillApplicable(baseSkill, request)) {
            return baseSkill;
        }
        String originalContent = baseSkill.getSkillContent();
        String resolvedContent = resolvePlaceholders(originalContent, request);
        String contextBlock = buildRuntimeContextBlock(request);
        String enrichedContent = appendRuntimeContext(resolvedContent, contextBlock);
        SkillDefinition tailored = copyDefinition(baseSkill);
        tailored.setSkillContent(enrichedContent);
        return tailored;
    }

    /**
     * 替换技能内容中的占位符
     * @param template
     * @param request
     * @return
     */
    private String resolvePlaceholders(String template, AgentRequest request) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, Object> contextMap = assembleContextMap(request);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = contextMap.get(key);
            String replacement = val != null ? Matcher.quoteReplacement(val.toString()) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 组装占位符上下文映射
     * @param request
     * @return
     */
    private Map<String, Object> assembleContextMap(AgentRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        // 占位符键agentCode为技能模板契约，保留历史兼容
        ctx.put("agentCode", request.getAgentCode());
        ctx.put("sessionId", request.getSessionId());
        ctx.put("userId", request.getUserId());
        if (request.getBody() != null) {
            ctx.putAll(request.getBody());
        }
        return ctx;
    }

    /**
     * 构建运行时上下文文本块
     * @param request
     * @return
     */
    private String buildRuntimeContextBlock(AgentRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(RUNTIME_CONTEXT_HEADER).append("\n");
        builder.append("当前任务：").append(safeStr(request.getAgentCode())).append("\n");
        Map<String, Object> meta = request.getBody();
        if (meta != null && !meta.isEmpty()) {
            for (Map.Entry<String, Object> entry : meta.entrySet()) {
                builder.append(entry.getKey()).append("：").append(safeStr(entry.getValue())).append("\n");
            }
        }
        return builder.toString().trim();
    }

    /**
     * 将运行时上下文追加到技能内容末尾
     * @param baseContent
     * @param runtimeBlock
     * @return
     */
    private String appendRuntimeContext(String baseContent, String runtimeBlock) {
        String trimmedBase = baseContent == null ? "" : baseContent.trim();
        String trimmedRuntime = runtimeBlock == null ? "" : runtimeBlock.trim();
        if (trimmedBase.isEmpty()) {
            return trimmedRuntime;
        }
        if (trimmedRuntime.isEmpty()) {
            return trimmedBase;
        }
        return trimmedBase + "\n\n" + trimmedRuntime;
    }

    /**
     * 复制技能定义
     * @param source
     * @return
     */
    private SkillDefinition copyDefinition(SkillDefinition source) {
        SkillDefinition target = new SkillDefinition();
        target.setSkillId(source.getSkillId());
        target.setSkillName(source.getSkillName());
        target.setSkillDescription(source.getSkillDescription());
        target.setSkillType(source.getSkillType());
        target.setSkillContent(source.getSkillContent());
        target.setBoundTools(source.getBoundTools());
        target.setExecution(source.getExecution());
        target.setDependencies(source.getDependencies());
        target.setPresetParameters(source.getPresetParameters());
        target.setSkillVersion(source.getSkillVersion());
        target.setConditions(source.getConditions());
        target.setTrustLevel(source.getTrustLevel());
        target.setResources(source.getResources());
        return target;
    }

    /**
     * 空值安全转字符串
     * @param value
     * @return
     */
    private String safeStr(Object value) {
        return value == null ? "" : value.toString();
    }
}
