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
package com.yangqiongai.ai.agent.runtime.skill;

import com.yangqiongai.ai.agent.runtime.prompt.SystemPromptSections;

import java.util.List;

/**
 * Agent技能箱
 * @author yangqiong
 */
public interface AgentSkillBox {

    /**
     * 获取技能列表
     * @return
     */
    List<AgentSkill> getSkills();

    /**
     * 技能箱是否为空
     * @return
     */
    boolean isEmpty();

    /**
     * 添加技能
     * @param skill
     */
    void addSkill(AgentSkill skill);

    /**
     * 按过滤规则返回过滤后的技能箱（请求级skillFilter装配路径应用点）
     * <p>
     * 语义与SkillMiddleware的matchesFilter一致：ALL/null不过滤，NONE清空，
     * ONLY/ENABLE仅保留名单内技能，EXCEPT/DISABLE剔除名单内技能。
     * </p>
     * @param filter 过滤规则，null或无需过滤时返回原箱
     * @return
     */
    default AgentSkillBox filteredBy(AgentSkillFilter filter) {
        if (filter == null || filter.getMode() == null || filter.getMode() == AgentSkillFilterMode.ALL) {
            return this;
        }
        AgentSkillFilterMode mode = filter.getMode();
        List<String> names = filter.getSkills();
        if (mode == AgentSkillFilterMode.NONE) {
            return new SimpleAgentSkillBox();
        }
        if (names == null || names.isEmpty()) {
            return this;
        }
        AgentSkillBox filtered = new SimpleAgentSkillBox();
        boolean only = mode == AgentSkillFilterMode.ONLY || mode == AgentSkillFilterMode.ENABLE;
        boolean changed = false;
        for (AgentSkill skill : getSkills()) {
            boolean contains = names.contains(skill.getName());
            boolean keep = only ? contains : !contains;
            changed = changed || !keep;
            if (keep) {
                filtered.addSkill(skill);
            }
        }
        return changed ? filtered : this;
    }

    /**
     * 构建系统提示词
     * <p>
     * 优先使用技能 description 字段作为摘要，无 description 时回退到 skillContent 首句，实现渐进加载。
     * </p>
     * @return
     */
    default String buildSystemPrompt() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(SystemPromptSections.SKILL_SECTION + "\n\n");
        for (AgentSkill skill : getSkills()) {
            sb.append("- ").append(skill.getName());
            String desc = skill.getDescription();
            if (desc != null && !desc.isBlank()) {
                sb.append(": ").append(desc);
            } else {
                String content = skill.getSkillContent();
                if (content != null && !content.isBlank()) {
                    String summary = extractFirstSentence(content);
                    if (!summary.isEmpty()) {
                        sb.append(": ").append(summary);
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 从内容中提取首句作为摘要
     * @param content
     * @return
     */
    private static String extractFirstSentence(String content) {
        String trimmed = content.trim();
        String body = trimmed.replaceFirst("^#+\\s+[^\\n]*\\n?", "");
        int end = -1;
        for (char c : new char[]{'。', '？', '！', '.', '?', '!', '\n'}) {
            int idx = body.indexOf(c);
            if (idx >= 0 && (end < 0 || idx < end)) {
                end = idx;
            }
        }
        String sentence = end > 0 ? body.substring(0, end).trim() : body;
        if (sentence.length() > 120) {
            return sentence.substring(0, 117) + "...";
        }
        return sentence;
    }
}
