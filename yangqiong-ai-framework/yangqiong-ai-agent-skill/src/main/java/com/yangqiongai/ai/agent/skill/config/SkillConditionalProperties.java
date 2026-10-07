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
package com.yangqiongai.ai.agent.skill.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 技能条件映射配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.skill")
public class SkillConditionalProperties {

    /**
     * 条件技能映射，key为任务编码或通配符，value为逗号分隔的技能ID
     */
    private Map<String, String> conditionalMapping;

    public Map<String, String> getConditionalMapping() {
        return conditionalMapping;
    }

    public void setConditionalMapping(Map<String, String> conditionalMapping) {
        this.conditionalMapping = conditionalMapping;
    }
}
