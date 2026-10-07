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
package com.yangqiongai.ai.agent.registry.gray;

import com.yangqiongai.ai.agent.data.registry.entity.AgentGrayRule;

/**
 * 灰度路由决策
 * @author yangqiong
 */
public class GrayDecision {

    /**
     * 是否命中灰度
     */
    private final boolean hit;

    /**
     * 命中的灰度规则（未命中为null）
     */
    private final AgentGrayRule rule;

    private GrayDecision(boolean hit, AgentGrayRule rule) {
        this.hit = hit;
        this.rule = rule;
    }

    /**
     * 命中决策
     * @param rule
     * @return
     */
    public static GrayDecision hit(AgentGrayRule rule) {
        return new GrayDecision(true, rule);
    }

    /**
     * 未命中决策
     * @return
     */
    public static GrayDecision miss() {
        return new GrayDecision(false, null);
    }

    public boolean isHit() {
        return hit;
    }

    public AgentGrayRule getRule() {
        return rule;
    }

    public Long getTargetVersionId() {
        return rule != null ? rule.getTargetVersionId() : null;
    }
}
