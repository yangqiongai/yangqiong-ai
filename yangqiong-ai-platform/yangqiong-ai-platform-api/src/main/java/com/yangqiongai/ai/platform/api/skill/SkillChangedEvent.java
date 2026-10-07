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
package com.yangqiongai.ai.platform.api.skill;

import org.springframework.context.ApplicationEvent;

/**
 * 技能变更事件（saveSkill落库成功后发布，社区版只发布不消费）
 * @author yangqiong
 */
public class SkillChangedEvent extends ApplicationEvent {

    /**
     * 变更动作：新建
     */
    public static final String ACTION_CREATE = "CREATE";

    /**
     * 变更动作：更新
     */
    public static final String ACTION_UPDATE = "UPDATE";

    /**
     * 变更动作：回滚
     */
    public static final String ACTION_ROLLBACK = "ROLLBACK";

    /**
     * 变更动作
     */
    private final String action;

    /**
     * 技能ID
     */
    private final String skillId;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 操作人
     */
    private final String operator;

    /**
     * 新版本号
     */
    private final int newVersion;

    /**
     * @param action
     * @param skillId
     * @param scopeId
     * @param operator
     * @param newVersion
     */
    public SkillChangedEvent(String action, String skillId, String scopeId, String operator, int newVersion) {
        super(skillId);
        this.action = action;
        this.skillId = skillId;
        this.scopeId = scopeId;
        this.operator = operator;
        this.newVersion = newVersion;
    }

    public String getAction() {
        return action;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getOperator() {
        return operator;
    }

    public int getNewVersion() {
        return newVersion;
    }
}
