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

import org.springframework.context.ApplicationEvent;

/**
 * 护栏触发事件
 * @author yangqiong
 */
public class GuardrailTriggeredEvent extends ApplicationEvent {

    /**
     * 触发该结果的护栏名称
     */
    private final String guardrailName;

    /**
     * 挂载点
     */
    private final HookPoint hookPoint;

    /**
     * 护栏动作
     */
    private final GuardrailAction action;

    /**
     * 触发内容
     */
    private final String content;

    /**
     * 阻断原因
     */
    private final String reason;

    /**
     * 关联Agent编码
     */
    private final String agentCode;

    public GuardrailTriggeredEvent(Object source, String guardrailName, HookPoint hookPoint,
                                   GuardrailAction action, String content, String reason,
                                   String agentCode) {
        super(source);
        this.guardrailName = guardrailName;
        this.hookPoint = hookPoint;
        this.action = action;
        this.content = content;
        this.reason = reason;
        this.agentCode = agentCode;
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

    public String getContent() {
        return content;
    }

    public String getReason() {
        return reason;
    }

    public String getAgentCode() {
        return agentCode;
    }
}
