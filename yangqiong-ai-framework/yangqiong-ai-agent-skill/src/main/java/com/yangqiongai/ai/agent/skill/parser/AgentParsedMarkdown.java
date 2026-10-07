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
package com.yangqiongai.ai.agent.skill.parser;

import java.util.Collections;
import java.util.Map;

/**
 * Agent Markdown解析结果
 * @author yangqiong
 */
public final class AgentParsedMarkdown {

    /**
     * 技能名称
     */
    private final String name;

    /**
     * 技能描述
     */
    private final String description;

    /**
     * 技能正文内容
     */
    private final String content;

    /**
     * 技能资源
     */
    private final Map<String, String> resources;

    public AgentParsedMarkdown(String name, String description, String content, Map<String, String> resources) {
        this.name = name;
        this.description = description;
        this.content = content;
        this.resources = resources != null ? Map.copyOf(resources) : Collections.emptyMap();
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
     * 获取技能正文内容
     * @return
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取技能资源
     * @return
     */
    public Map<String, String> getResources() {
        return resources;
    }
}
