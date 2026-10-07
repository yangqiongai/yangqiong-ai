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
package com.yangqiongai.ai.security.guardrails;

/**
 * 护栏检查结果
 *
 * @author yangqiong
 */
public class GuardrailResult {

    /**
     * 是否通过
     */
    private boolean passed;

    /**
     * 阻断原因
     */
    private String reason;

    /**
     * 触发该结果的护栏名称
     */
    private String guardrailName;

    /**
     * 触发该结果的挂载点
     */
    private HookPoint hookPoint;

    /**
     * 护栏动作
     */
    private GuardrailAction action;

    /**
     * 脱敏后的内容（action=MASK时使用）
     */
    private String maskedContent;

    /**
     * 通过
     * @return
     */
    public static GuardrailResult passed() {
        return new GuardrailResult(true, null, null, null, GuardrailAction.ALLOW, null);
    }

    /**
     * 阻断
     * @param reason
     * @return
     */
    public static GuardrailResult blocked(String reason) {
        return new GuardrailResult(false, reason, null, null, GuardrailAction.BLOCK, null);
    }

    /**
     * 阻断（带护栏名称和挂载点）
     * @param reason
     * @param guardrailName
     * @param hookPoint
     * @return
     */
    public static GuardrailResult blocked(String reason, String guardrailName, HookPoint hookPoint) {
        return new GuardrailResult(false, reason, guardrailName, hookPoint, GuardrailAction.BLOCK, null);
    }

    /**
     * 脱敏处理
     * @param maskedContent
     * @param guardrailName
     * @param hookPoint
     * @return
     */
    public static GuardrailResult masked(String maskedContent, String guardrailName, HookPoint hookPoint) {
        return new GuardrailResult(true, null, guardrailName, hookPoint, GuardrailAction.MASK, maskedContent);
    }

    public GuardrailResult(boolean passed, String reason) {
        this(passed, reason, null, null, passed ? GuardrailAction.ALLOW : GuardrailAction.BLOCK, null);
    }

    public GuardrailResult(boolean passed, String reason, String guardrailName,
                           HookPoint hookPoint, GuardrailAction action, String maskedContent) {
        this.passed = passed;
        this.reason = reason;
        this.guardrailName = guardrailName;
        this.hookPoint = hookPoint;
        this.action = action;
        this.maskedContent = maskedContent;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getReason() {
        return reason;
    }

    public String getGuardrailName() {
        return guardrailName;
    }

    public HookPoint getHookPoint() {
        return hookPoint;
    }

    public GuardrailAction getAction() {
        return action;
    }

    public String getMaskedContent() {
        return maskedContent;
    }
}
