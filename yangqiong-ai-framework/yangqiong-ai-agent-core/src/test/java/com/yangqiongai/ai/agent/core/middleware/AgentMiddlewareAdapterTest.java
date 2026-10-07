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
package com.yangqiongai.ai.agent.core.middleware;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.provider.ToolConventions;
import com.yangqiongai.ai.agent.core.provider.ToolUsageTracker;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer;
import com.yangqiongai.ai.agent.core.trace.TraceCollector;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Agent中间件适配器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentMiddlewareAdapterTest {

    @Mock
    private TraceCollector traceCollector;

    @Mock
    private ErrorCategorizer errorCategorizer;

    @Mock
    private AgentRuntimeContext context;

    @Mock
    private AgentSkillBox skillBox;

    private ToolConventions conventions;

    @BeforeEach
    void setUp() {
        conventions = new StubToolConventions();
    }

    private AgentMiddlewareAdapter newAdapter(ToolConventions toolConventions) {
        return new AgentMiddlewareAdapter(traceCollector, errorCategorizer, null, toolConventions,
                (ToolUsageTracker) null);
    }

    private String skillInjectedPrompt() {
        return "你是一个智能AI助手。\n启动指引。\n\n# 可用技能\n\n- task-planning: 任务规划专家\n";
    }

    @Test
    @DisplayName("动态重建模式下使用指引紧随技能列表之后")
    void onSystemPrompt_dynamicRebuild_insertsGuideRightAfterSkillList() {
        when(context.get(anyString())).thenReturn(null);

        String result = newAdapter(conventions).onSystemPrompt(skillInjectedPrompt(), context);

        int skillIdx = result.indexOf("# 可用技能");
        int guideIdx = result.indexOf("## 技能使用指引");
        int envIdx = result.indexOf("# 环境信息");
        int ruleIdx = result.indexOf("# 工具使用规则");
        assertThat(guideIdx).isGreaterThan(skillIdx);
        assertThat(guideIdx).isLessThan(envIdx);
        assertThat(envIdx).isLessThan(ruleIdx);
        assertThat(result.substring(skillIdx, guideIdx)).doesNotContain("# 工具使用规则");
    }

    @Test
    @DisplayName("动态重建模式下技能摘要缺少使用指引时补齐")
    void onSystemPrompt_dynamicRebuild_appendsGuideWhenMissing() {
        when(context.get(anyString())).thenReturn(null);
        String base = "系统提示词\n\n# 可用技能\n\n- task-planning: 任务规划专家\n";

        String result = newAdapter(conventions).onSystemPrompt(base, context);

        assertThat(result.indexOf("## 技能使用指引")).isLessThan(result.indexOf("# 环境信息"));
        assertThat(result).contains("用户请求与上述技能匹配时，先调用 load_skill 加载对应技能");
    }

    @Test
    @DisplayName("技能摘要已携带使用指引时不重复追加")
    void onSystemPrompt_dynamicRebuild_skipsGuideWhenAlreadyPresent() {
        when(context.get(anyString())).thenReturn(null);
        String base = skillInjectedPrompt()
                + "\n## 技能使用指引\n\n当用户请求与上述某个技能匹配时优先加载。\n";

        String result = newAdapter(conventions).onSystemPrompt(base, context);

        assertThat(result).containsOnlyOnce("## 技能使用指引");
    }

    @Test
    @DisplayName("Hook注入模式下按技能分块加指引加运行时上下文顺序追加")
    void onSystemPrompt_hookMode_appendsSkillSectionWithGuide() {
        when(context.get(AgentContext.CTX_SKILL_BOX)).thenReturn(skillBox);
        when(skillBox.buildSystemPrompt()).thenReturn("# 可用技能\n\n- task-planning: 任务规划专家\n");

        String result = newAdapter(conventions).onSystemPrompt("你是一个智能AI助手。\n", context);

        int skillIdx = result.indexOf("# 可用技能");
        int guideIdx = result.indexOf("## 技能使用指引");
        int envIdx = result.indexOf("# 环境信息");
        assertThat(skillIdx).isGreaterThan(0);
        assertThat(guideIdx).isGreaterThan(skillIdx);
        assertThat(envIdx).isGreaterThan(guideIdx);
        assertThat(result).contains("# 可用技能\n\n- task-planning: 任务规划专家\n\n## 技能使用指引");
    }

    @Test
    @DisplayName("无技能时仅追加运行时上下文")
    void onSystemPrompt_withoutSkills_appendsRuntimeContextOnly() {
        when(context.get(anyString())).thenReturn(null);

        String result = newAdapter(conventions).onSystemPrompt("你是一个智能AI助手。", context);

        assertThat(result).doesNotContain("# 可用技能");
        assertThat(result).doesNotContain("## 技能使用指引");
        assertThat(result).contains("# 环境信息");
        assertThat(result).contains("# 工具使用规则");
    }

    /**
     * 测试用工具名约定
     */
    private static class StubToolConventions implements ToolConventions {

        @Override
        public String subagentToolPrefix() {
            return "call_";
        }

        @Override
        public Set<String> skillViewTools() {
            return Set.of("load_skill", "read_skill_resource");
        }

        @Override
        public String skillUseTool() {
            return "use_skill";
        }

        @Override
        public String skillLoadTool() {
            return "load_skill";
        }
    }
}
