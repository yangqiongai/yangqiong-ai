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
package com.yangqiongai.ai.agent.runtime;

import com.yangqiongai.ai.agent.runtime.config.AgentCompactionConfig;
import com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig;
import com.yangqiongai.ai.agent.runtime.config.AgentPermissionContextState;
import com.yangqiongai.ai.agent.runtime.config.AgentPermissionMode;
import com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule;
import com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat;
import com.yangqiongai.ai.agent.runtime.config.AgentToolChoice;
import com.yangqiongai.ai.agent.runtime.config.AgentToolResultEvictionConfig;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillFilter;

/**
 * Agent运行时构建器（高级）
 * <p>
 * 扩展基础构建器，提供技能箱、响应格式、权限、记忆、压缩等高级配置。
 * 大多数运行时实现应支持这些配置项。
 * </p>
 * @author yangqiong
 */
public interface AdvancedAgentRuntimeBuilder extends AgentRuntimeBuilder {

    /**
     * 设置技能箱
     * @param skillBox
     * @return
     */
    AdvancedAgentRuntimeBuilder skillBox(AgentSkillBox skillBox);

    /**
     * 设置技能过滤规则（ONLY/EXCEPT/ENABLE/DISABLE）
     * @param filter
     * @return
     */
    default AdvancedAgentRuntimeBuilder skillFilter(AgentSkillFilter filter) {
        return this;
    }

    /**
     * 设置响应格式
     * @param format
     * @return
     */
    AdvancedAgentRuntimeBuilder responseFormat(AgentResponseFormat format);

    /**
     * 设置结构化输出类型
     * @param type
     * @return
     */
    AdvancedAgentRuntimeBuilder structuredOutputType(Class<?> type);

    /**
     * 设置结构化输出校验失败自动纠错重试
     * @param maxRetries 最大重试次数
     * @param errorTemplate 校验错误注入模板
     * @return
     */
    default AdvancedAgentRuntimeBuilder structuredOutputRetry(int maxRetries, String errorTemplate) {
        return this;
    }

    /**
     * 设置权限模式与规则
     * @param mode
     * @param rule
     * @return
     */
    AdvancedAgentRuntimeBuilder permission(AgentPermissionMode mode, AgentPermissionRule rule);

    /**
     * 设置权限上下文状态
     * @param state
     * @return
     */
    AdvancedAgentRuntimeBuilder permissionContextState(AgentPermissionContextState state);

    /**
     * 设置记忆配置
     * @param config
     * @return
     */
    AdvancedAgentRuntimeBuilder memoryConfig(AgentMemoryConfig config);

    /**
     * 设置压缩配置
     * @param config
     * @return
     */
    AdvancedAgentRuntimeBuilder compactionConfig(AgentCompactionConfig config);

    /**
     * 设置工具结果驱逐配置
     * @param config
     * @return
     */
    AdvancedAgentRuntimeBuilder toolResultEvictionConfig(AgentToolResultEvictionConfig config);

    /**
     * 设置工具选择策略
     * @param choice
     * @return
     */
    AdvancedAgentRuntimeBuilder toolChoice(AgentToolChoice choice);
}
