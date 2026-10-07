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

import java.math.BigDecimal;

/**
 * 技能质量评测事件（评测落库后发布，社区版只发布不消费）
 * @author yangqiong
 */
public class SkillEvaluatedEvent extends ApplicationEvent {

    /**
     * 技能ID
     */
    private final String skillId;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 评测版本号
     */
    private final int version;

    /**
     * 质量总分(0-100)
     */
    private final BigDecimal qualityScore;

    /**
     * 上一版本质量总分（上一版本未评测时为null）
     */
    private final BigDecimal prevScore;

    /**
     * @param skillId
     * @param scopeId
     * @param version
     * @param qualityScore
     * @param prevScore
     */
    public SkillEvaluatedEvent(String skillId, String scopeId, int version,
                               BigDecimal qualityScore, BigDecimal prevScore) {
        super(skillId);
        this.skillId = skillId;
        this.scopeId = scopeId;
        this.version = version;
        this.qualityScore = qualityScore;
        this.prevScore = prevScore;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public int getVersion() {
        return version;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public BigDecimal getPrevScore() {
        return prevScore;
    }
}
