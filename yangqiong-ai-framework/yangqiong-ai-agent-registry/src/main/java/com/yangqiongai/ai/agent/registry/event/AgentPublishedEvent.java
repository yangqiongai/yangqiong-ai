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
 * Agent发布事件
 * @author yangqiong
 */
public class AgentPublishedEvent {

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 版本号
     */
    private final String versionNo;

    /**
     * 版本ID
     */
    private final Long versionId;

    /**
     * 发布类型(PUBLISH/GRAY)
     */
    private final String releaseType;

    public AgentPublishedEvent(String agentCode, String versionNo, Long versionId, String releaseType) {
        this.agentCode = agentCode;
        this.versionNo = versionNo;
        this.versionId = versionId;
        this.releaseType = releaseType;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public Long getVersionId() {
        return versionId;
    }

    public String getReleaseType() {
        return releaseType;
    }
}
