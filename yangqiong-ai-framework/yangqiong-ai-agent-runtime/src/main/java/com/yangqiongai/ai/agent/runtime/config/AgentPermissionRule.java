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
package com.yangqiongai.ai.agent.runtime.config;

import java.util.Objects;

/**
 * Agent权限规则
 * @author yangqiong
 */
public final class AgentPermissionRule {

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 权限模式
     */
    private final AgentPermissionMode mode;

    private AgentPermissionRule(String toolName, AgentPermissionMode mode) {
        this.toolName = toolName;
        this.mode = mode;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取权限模式
     * @return
     */
    public AgentPermissionMode getMode() {
        return mode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentPermissionRule that = (AgentPermissionRule) o;
        return Objects.equals(toolName, that.toolName) && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, mode);
    }

    @Override
    public String toString() {
        return "AgentPermissionRule{toolName='" + toolName + "', mode=" + mode + "}";
    }

    /**
     * 权限规则构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 工具名称
         */
        private String toolName;

        /**
         * 权限模式
         */
        private AgentPermissionMode mode;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder mode(AgentPermissionMode mode) {
            this.mode = mode;
            return this;
        }

        public AgentPermissionRule build() {
            return new AgentPermissionRule(toolName, mode);
        }
    }
}
