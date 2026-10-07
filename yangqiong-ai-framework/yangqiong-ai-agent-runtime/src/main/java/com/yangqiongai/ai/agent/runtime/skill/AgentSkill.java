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
package com.yangqiongai.ai.agent.runtime.skill;

import java.util.Collections;
import java.util.Map;

/**
 * Agent技能定义
 * @author yangqiong
 */
public final class AgentSkill {

    /**
     * 技能名称
     */
    private final String name;

    /**
     * 技能描述
     */
    private final String description;

    /**
     * 技能来源
     * <p>已知值：</p>
     * <ul>
     *   <li>BUILTIN：框架内置技能</li>
     *   <li>TRUSTED：受信任来源技能</li>
     *   <li>COMMUNITY：社区贡献技能</li>
     *   <li>AGENT_CREATED：Agent 运行时动态创建的技能</li>
     * </ul>
     */
    private final String source;

    /**
     * 技能内容
     */
    private final String skillContent;

    /**
     * 技能资源
     */
    private final Map<String, String> resources;

    /**
     * 构造技能（含描述）
     * @param name
     * @param source
     * @param description
     * @param skillContent
     * @param resources
     */
    public AgentSkill(String name, String source, String description, String skillContent, Map<String, String> resources) {
        this.name = name;
        this.source = source;
        this.description = description != null ? description : "";
        this.skillContent = skillContent;
        this.resources = resources != null ? Map.copyOf(resources) : Collections.emptyMap();
    }

    /**
     * 构造技能（无描述，兼容旧调用方）
     * @param name
     * @param source
     * @param skillContent
     * @param resources
     */
    public AgentSkill(String name, String source, String skillContent, Map<String, String> resources) {
        this(name, source, "", skillContent, resources);
    }

    /**
     * 获取技能名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取技能描述
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取技能来源
     * @return
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取技能内容
     * @return
     */
    public String getSkillContent() {
        return skillContent;
    }

    /**
     * 获取技能资源
     * @return
     */
    public Map<String, String> getResources() {
        return resources;
    }
}
