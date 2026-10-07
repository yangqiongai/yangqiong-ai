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
package com.yangqiongai.ai.agent.core.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.processor.AgentProcessor;
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.llm.LlmModelService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent配置管理
 * @author yangqiong
 */
@Service
public class AgentManager {

    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private List<AgentProcessor> processors;

    @Autowired
    private LlmModelService llmModelService;

    public List<Agent> list() {
        List<Agent> agents = agentRepository.list();
        agents.forEach(this::fillSupportImage);
        return agents;
    }

    public Agent getByCode(String agentCode) {
        Agent agent = agentRepository.getByCode(agentCode);
        if (agent != null) {
            fillSupportImage(agent);
        }
        return agent;
    }

    public List<Agent> listEnabled() {
        List<Agent> agents = agentRepository.listEnabled();
        agents.forEach(this::fillSupportImage);
        return agents;
    }

    /**
     * 按Agent绑定模型填充图片支持能力
     * @param agent
     */
    private void fillSupportImage(Agent agent) {
        try {
            String modelCode = null;
            if (agent.getAgentConfig() != null && !agent.getAgentConfig().isBlank()) {
                String model = OBJECT_MAPPER.readTree(agent.getAgentConfig()).path("model").asText(null);
                if (model != null && !model.isBlank()) {
                    modelCode = model;
                }
            }
            if (modelCode == null || modelCode.isBlank()) {
                modelCode = llmModelService.resolveModel(agent.getAgentCode(), null);
            }
            agent.setSupportImage(llmModelService.findByModelCode(modelCode)
                    .map(m -> m.getSupportImage() != null ? m.getSupportImage() : 0)
                    .orElse(0));
        } catch (Exception e) {
            log.debug("填充Agent图片支持能力失败: agentCode={}", agent.getAgentCode(), e);
            agent.setSupportImage(0);
        }
    }

    public void save(Agent agent) {
        agentRepository.save(agent);
    }

    public void updateById(Agent agent) {
        agentRepository.updateById(agent);
    }

    public boolean toggleStatus(String agentCode) {
        return agentRepository.toggleStatus(agentCode);
    }

    @PostConstruct
    public void syncProcessorsFromSpring() {
        if (processors == null || processors.isEmpty()) {
            log.warn("未发现任何AgentProcessor实现");
            return;
        }
        int registered = 0;
        int updated = 0;
        for (AgentProcessor processor : processors) {
            String agentCode = processor.getAgentCode();
            Agent existing = getByCode(agentCode);
            if (existing == null) {
                Agent agent = new Agent();
                agent.setAgentCode(agentCode);
                agent.setAgentName(inferTypeName(processor));
                agent.setDescription(inferDescription(processor));
                agent.setCategory(inferCategory(agentCode));
                agent.setSessionType(inferSessionType(agentCode));
                agent.setStatus(1);
                agent.setSortOrder(99);
                agentRepository.save(agent);
                registered++;
                log.info("自动注册Agent: agentCode={}, processor={}", agentCode, processor.getClass().getSimpleName());
            }
        }
        log.info("Agent同步完成: registered={}, updated={}, total={}", registered, updated, processors.size());
    }

    /**
     * 已注册处理器清单（内存注册来源，不查库）
     * @return
     */
    public List<Map<String, Object>> listProcessorOptions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentProcessor processor : processors) {
            Map<String, Object> item = new HashMap<>();
            item.put("code", processor.getAgentCode());
            item.put("name", processor.getClass().getSimpleName());
            result.add(item);
        }
        return result;
    }

    private String inferTypeName(AgentProcessor processor) {
        String simpleName = processor.getClass().getSimpleName();
        if (simpleName.endsWith("AgentProcessor")) {
            String base = simpleName.substring(0, simpleName.length() - "AgentProcessor".length());
            return camelToChinese(base);
        }
        return simpleName;
    }

    private String inferDescription(AgentProcessor processor) {
        return processor.getClass().getSimpleName() + " 处理器";
    }

    private String inferCategory(String agentCode) {
        if (agentCode.contains("qa")) return "QA";
        if (agentCode.equals("extraction")) return "EXTRACTION";
        if (agentCode.equals("review")) return "REVIEW";
        if (agentCode.contains("report")) return "REPORT";
        if (agentCode.equals("orchestration")) return "ORCHESTRATION";
        return "CHAT";
    }

    private String inferSessionType(String agentCode) {
        if (agentCode.equals("doc_qa")) return "DOC_QA";
        if (agentCode.equals("kb_qa")) return "KB_QA";
        if (agentCode.equals("extraction")) return "EXTRACTION";
        if (agentCode.equals("review")) return "REVIEW";
        if (agentCode.contains("report")) return "REPORT";
        if (agentCode.equals("orchestration")) return "ORCHESTRATION";
        return "CHAT";
    }

    private String camelToChinese(String camel) {
        return switch (camel.toLowerCase()) {
            case "default" -> "默认对话";
            case "chat" -> "通用对话";
            case "docqa" -> "文档问答";
            case "kbqa" -> "知识库问答";
            case "kbqarag" -> "知识库RAG问答";
            case "kbqawiki" -> "知识库Wiki问答";
            case "extraction" -> "信息提取";
            case "review" -> "内容审核";
            case "reportwriting" -> "报告撰写";
            case "orchestration" -> "多代理编排";
            default -> camel;
        };
    }
}
