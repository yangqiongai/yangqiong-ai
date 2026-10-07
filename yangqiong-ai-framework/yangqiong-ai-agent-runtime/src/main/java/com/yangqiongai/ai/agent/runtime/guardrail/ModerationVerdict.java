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
package com.yangqiongai.ai.agent.runtime.guardrail;

/**
 * 内容审查裁决
 * <p>
 * 描述内容审查的裁决动作与原因。
 * </p>
 * @author yangqiong
 */
public final class ModerationVerdict {

    /**
     * 裁决动作
     */
    public enum Action {

        /**
         * 放行
         */
        PASS,

        /**
         * 中止执行
         */
        BLOCK,

        /**
         * 脱敏后继续
         */
        MASK
    }

    /**
     * 裁决动作
     */
    private final Action action;

    /**
     * 裁决原因
     */
    private final String reason;

    private ModerationVerdict(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    /**
     * 创建放行裁决
     * @return
     */
    public static ModerationVerdict pass() {
        return new ModerationVerdict(Action.PASS, null);
    }

    /**
     * 创建中止裁决
     * @param reason 中止原因
     * @return
     */
    public static ModerationVerdict block(String reason) {
        return new ModerationVerdict(Action.BLOCK, reason);
    }

    /**
     * 创建脱敏裁决
     * @param reason 脱敏原因
     * @return
     */
    public static ModerationVerdict mask(String reason) {
        return new ModerationVerdict(Action.MASK, reason);
    }

    /**
     * 获取裁决动作
     * @return
     */
    public Action getAction() {
        return action;
    }

    /**
     * 获取裁决原因
     * @return
     */
    public String getReason() {
        return reason;
    }

    /**
     * 是否放行
     * @return
     */
    public boolean isPass() {
        return action == Action.PASS;
    }

    /**
     * 是否中止
     * @return
     */
    public boolean isBlock() {
        return action == Action.BLOCK;
    }

    /**
     * 是否脱敏
     * @return
     */
    public boolean isMask() {
        return action == Action.MASK;
    }

    @Override
    public String toString() {
        return "ModerationVerdict{action=" + action + ", reason=" + reason + "}";
    }
}
