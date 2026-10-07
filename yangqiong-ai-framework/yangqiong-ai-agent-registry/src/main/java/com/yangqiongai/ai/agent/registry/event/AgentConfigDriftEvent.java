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
package com.yangqiongai.ai.agent.registry.event;

/**
 * Agent配置漂移事件
 * <p>
 * 漂移检测发现实际配置与PUBLISHED快照不一致时发布，
 * 平台侧(如企业版告警通道)可监听处理。
 * </p>
 * @author yangqiong
 */
public class AgentConfigDriftEvent {

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 环境档编码
     */
    private final String profileCode;

    /**
     * 字段级差异JSON
     */
    private final String diffJson;

    public AgentConfigDriftEvent(String agentCode, String profileCode, String diffJson) {
        this.agentCode = agentCode;
        this.profileCode = profileCode;
        this.diffJson = diffJson;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getProfileCode() {
        return profileCode;
    }

    public String getDiffJson() {
        return diffJson;
    }
}
