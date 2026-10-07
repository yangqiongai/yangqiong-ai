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
package com.yangqiongai.ai.agent.registry.materialize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.registry.assemble.AgentAssembler;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent配置物化器
 * <p>
 * 将版本快照物化同步到ai_agent（配置投影经AgentManager路径保证缓存一致）。
 * 能力挂载（tools/mcpServers/skills）由agentConfig正向单源承载，运行时装配链路直读，无需物化副本。
 * scope_id由AiMetaObjectHandler按ScopeContext自动填充保持一致。
 * </p>
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class AgentConfigMaterializer {

    private static final Logger log = LoggerFactory.getLogger(AgentConfigMaterializer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int DEFAULT_SORT_ORDER = 99;

    @Autowired
    private AgentManager agentManager;

    @Autowired
    private AgentRepository agentRepository;

    /**
     * 物化版本快照到运行时表
     * @param definition
     * @param version
     */
    public void materialize(AgentDefinition definition, AgentVersion version) {
        doMaterialize(definition, version, version.getConfigJson());
    }

    /**
     * 执行物化
     * @param definition
     * @param version
     * @param configJson
     */
    private void doMaterialize(AgentDefinition definition, AgentVersion version, String configJson) {
        String agentCode = definition.getAgentCode();
        List<String> tools = parseTools(configJson);
        Agent existing = agentManager.getByCode(agentCode);
        if (existing != null) {
            // 仅覆盖治理来源字段，保留存量图标/会话类型/排序/处理器等
            existing.setAgentConfig(configJson);
            existing.setAgentName(definition.getAgentName());
            existing.setDescription(definition.getDescription());
            existing.setCategory(definition.getCategory());
            agentManager.updateById(existing);
        } else {
            Agent agent = AgentAssembler.assemble(definition, version);
            agent.setAgentConfig(configJson);
            agent.setStatus(1);
            agent.setSortOrder(DEFAULT_SORT_ORDER);
            agentManager.save(agent);
        }
        log.info("Agent配置物化完成: agentCode={}, versionNo={}, tools={}", agentCode, version.getVersionNo(), tools);
    }

    /**
     * 禁用运行时Agent记录
     * @param agentCode
     */
    public void disable(String agentCode) {
        agentRepository.updateStatus(agentCode, 0);
        log.info("Agent运行时记录已禁用: agentCode={}", agentCode);
    }

    /**
     * 启用运行时Agent记录
     * @param agentCode
     */
    public void enable(String agentCode) {
        agentRepository.updateStatus(agentCode, 1);
        log.info("Agent运行时记录已启用: agentCode={}", agentCode);
    }

    /**
     * 从配置JSON中解析tools清单(同时校验configJson与tools结构合法性)
     * @param configJson
     * @return
     */
    private List<String> parseTools(String configJson) {
        JsonNode root;
        try {
            root = MAPPER.readTree(configJson);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "configJson不是合法JSON: " + e.getMessage());
        }
        JsonNode tools = root.get("tools");
        if (tools == null) {
            return List.of();
        }
        if (!tools.isArray()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "tools必须是字符串数组");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode node : tools) {
            if (!node.isTextual()) {
                throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "tools必须是字符串数组");
            }
            result.add(node.asText());
        }
        return result;
    }
}
