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
package com.yangqiongai.ai.agent.registry.assemble;

import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;

/**
 * Agent装配器
 * <p>
 * 唯一装配点：AgentDefinition+AgentVersion→Agent（agentCode对齐、agentName、
 * agentConfig=version.configJson、category），物化器与灰度resolver共用。
 * </p>
 * @author yangqiong
 */
public final class AgentAssembler {

    private AgentAssembler() {
    }

    /**
     * 装配Agent（仅填充治理来源字段，调用方自行处理存量字段与状态）
     * @param definition
     * @param version
     * @return
     */
    public static Agent assemble(AgentDefinition definition, AgentVersion version) {
        Agent agent = new Agent();
        agent.setAgentCode(definition.getAgentCode());
        agent.setAgentName(definition.getAgentName());
        agent.setDescription(definition.getDescription());
        agent.setCategory(definition.getCategory());
        agent.setAgentConfig(version.getConfigJson());
        return agent;
    }
}
