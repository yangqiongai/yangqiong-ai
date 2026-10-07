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
package com.yangqiongai.ai.memory.agentmemory.contributor;

import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryCandidate;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryInjection;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunOutcome;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunQuery;
import com.yangqiongai.ai.memory.agentmemory.service.AgentMemoryRetrieveService;
import com.yangqiongai.ai.memory.agentmemory.service.AgentMemoryWriteService;
import com.yangqiongai.ai.memory.agentmemory.service.OrgContextService;
import com.yangqiongai.ai.memory.spi.MemoryContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Agent运行记忆缺省贡献者
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.memory.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DefaultAgentMemoryContributor implements MemoryContributor {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentMemoryContributor.class);

    @Autowired
    private AgentMemoryRetrieveService retrieveService;

    @Autowired
    private AgentMemoryWriteService writeService;

    @Autowired
    private OrgContextService orgContextService;

    @Value("${ai.memory.agent.contributor.inject-token-budget:512}")
    private int injectTokenBudget;

    @Value("${ai.memory.agent.contributor.episodic-max-length:500}")
    private int episodicMaxLength;

    /**
     * 运行前收集注入记忆（组织上下文+运行记忆，合并受Token预算约束）
     * @param query
     * @return
     */
    @Override
    public AgentMemoryInjection beforeRun(MemoryRunQuery query) {
        AgentMemoryInjection injection = retrieveService.retrieve(query, injectTokenBudget);
        String orgBlock = orgContextService.buildInjectionBlock();
        if (orgBlock != null && !orgBlock.isBlank()) {
            String combined = orgBlock + "\n\n" + injection.getText();
            injection.setText(combined);
            injection.setTokenEstimate(injection.getTokenEstimate() + estimateTokens(orgBlock));
        }
        return injection;
    }

    /**
     * 运行后产出情景记忆候选（任务级，写入侧含安全网关与去重）
     * @param outcome
     */
    @Override
    public void afterRun(MemoryRunOutcome outcome) {
        if (outcome == null || outcome.getOutputText() == null || outcome.getOutputText().isBlank()) {
            return;
        }
        try {
            AgentMemoryCandidate candidate = new AgentMemoryCandidate();
            candidate.setAgentCode(outcome.getAgentCode());
            candidate.setUserAnchor(outcome.getUserAnchor());
            candidate.setSessionId(outcome.getSessionId());
            candidate.setScopeId(outcome.getScopeId());
            candidate.setSourceTaskId(outcome.getTaskId());
            candidate.setSourceUser(outcome.getUserAnchor());
            candidate.setMemoryType(AgentMemoryEntryInfo.TYPE_EPISODIC);
            candidate.setContent(buildEpisodicContent(outcome));
            writeService.store(candidate);
        } catch (Exception e) {
            // 记忆产出失败不影响主流程
            log.warn("Agent运行记忆产出失败, agentCode={}", outcome.getAgentCode(), e);
        }
    }

    /**
     * 构建情景记忆内容（输入+输出摘要，控制长度）
     * @param outcome
     * @return
     */
    private String buildEpisodicContent(MemoryRunOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        if (outcome.getInputText() != null && !outcome.getInputText().isBlank()) {
            sb.append("任务输入：").append(abbreviate(outcome.getInputText()));
        }
        sb.append("；任务输出：").append(abbreviate(outcome.getOutputText()));
        return sb.toString();
    }

    private String abbreviate(String text) {
        String trimmed = text.strip();
        return trimmed.length() > episodicMaxLength ? trimmed.substring(0, episodicMaxLength) + "..." : trimmed;
    }

    /**
     * 估算文本Token数（与检索服务同口径）
     * @param text
     * @return
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 2.0);
    }
}
