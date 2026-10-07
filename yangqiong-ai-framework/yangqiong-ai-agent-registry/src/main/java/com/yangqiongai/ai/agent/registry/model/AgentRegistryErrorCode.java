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
package com.yangqiongai.ai.agent.registry.model;

/**
 * 注册中心错误码
 * @author yangqiong
 */
public enum AgentRegistryErrorCode {

    /**
     * 非法的版本状态迁移
     */
    VERSION_STATE_TRANSITION_INVALID(72001, "非法的版本状态迁移"),

    /**
     * 发布门禁未通过
     */
    PUBLISH_GATE_REJECTED(72002, "发布门禁未通过"),

    /**
     * Agent版本不存在
     */
    VERSION_NOT_FOUND(72003, "Agent版本不存在"),

    /**
     * 重复发布相同配置
     */
    DUPLICATE_PUBLISH(72004, "重复发布相同配置"),

    /**
     * Agent定义不存在
     */
    DEFINITION_NOT_FOUND(72005, "Agent定义不存在"),

    /**
     * Agent定义已存在版本，无法删除
     */
    DEFINITION_HAS_VERSIONS(72006, "Agent定义已存在版本，无法删除"),

    /**
     * 并发发布冲突
     */
    CONCURRENT_CONFLICT(72007, "并发发布冲突，请重试");

    private final int code;

    private final String message;

    AgentRegistryErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
