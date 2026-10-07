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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agent权限上下文状态
 * @author yangqiong
 */
public final class AgentPermissionContextState {

    /**
     * 权限模式
     */
    private final AgentPermissionMode mode;

    /**
     * 权限规则列表
     */
    private final List<AgentPermissionRule> rules;

    private AgentPermissionContextState(AgentPermissionMode mode, List<AgentPermissionRule> rules) {
        this.mode = mode;
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取权限模式
     * @return
     */
    public AgentPermissionMode getMode() {
        return mode;
    }

    /**
     * 获取权限规则列表
     * @return
     */
    public List<AgentPermissionRule> getRules() {
        return rules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentPermissionContextState that = (AgentPermissionContextState) o;
        return mode == that.mode && Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, rules);
    }

    @Override
    public String toString() {
        return "AgentPermissionContextState{mode=" + mode + ", ruleCount=" + (rules != null ? rules.size() : 0) + "}";
    }

    /**
     * 权限上下文状态构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 权限模式
         */
        private AgentPermissionMode mode;

        /**
         * 权限规则列表
         */
        private List<AgentPermissionRule> rules = new ArrayList<>();

        public Builder mode(AgentPermissionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder rules(List<AgentPermissionRule> rules) {
            this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
            return this;
        }

        public AgentPermissionContextState build() {
            return new AgentPermissionContextState(mode, rules);
        }
    }
}
