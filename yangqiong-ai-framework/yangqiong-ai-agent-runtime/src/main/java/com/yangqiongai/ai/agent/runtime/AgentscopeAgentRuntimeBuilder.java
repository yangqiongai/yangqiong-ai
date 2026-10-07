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

/**
 * Agentscope引擎专属扩展构建器
 * <p>
 * 承载agentscope运行时独有的工作区/环境/本地工具配置，自研引擎无对应能力。
 * 调用方需使用agentscope本地特性时，将构建器转型为本接口后配置。
 * </p>
 * @author yangqiong
 */
public interface AgentscopeAgentRuntimeBuilder extends HarnessAgentRuntimeBuilder {

    /**
     * 设置路径展开开关（@path语法）
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder atPathExpansionEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置工作区上下文开关
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder workspaceContextEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置动态技能开关
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder dynamicSkillsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置默认工作区技能开关
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder defaultWorkspaceSkillsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置文件系统工具开关（list_files/write_file/read_file/glob/grep/edit）
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder filesystemToolsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置Shell执行工具开关
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder shellToolEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置会话持久化开关
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder sessionPersistenceEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置workspace/tools.json自动加载开关（MCP与过滤器）
     * @param enabled 默认启用
     * @return
     */
    default AgentscopeAgentRuntimeBuilder toolsConfigEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置工作区目录路径
     * @param path null时使用默认 ${cwd}/.agentscope/workspace
     * @return
     */
    default AgentscopeAgentRuntimeBuilder workspace(String path) {
        return this;
    }

    /**
     * 设置部署环境标签（dev/staging/prod，用于EnvironmentFilter）
     * @param env
     * @return
     */
    default AgentscopeAgentRuntimeBuilder environment(String env) {
        return this;
    }

    /**
     * 设置Agent追踪日志开关
     * @param enabled
     * @return
     */
    default AgentscopeAgentRuntimeBuilder agentTracingLogEnabled(boolean enabled) {
        return this;
    }
}
