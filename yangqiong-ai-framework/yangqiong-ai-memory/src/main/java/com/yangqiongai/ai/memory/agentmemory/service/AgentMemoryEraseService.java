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
package com.yangqiongai.ai.memory.agentmemory.service;

import com.yangqiongai.ai.memory.agentmemory.event.AgentMemoryErasedEvent;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.vector.AgentMemoryVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent运行记忆合规擦除
 * @author yangqiong
 */
@Service
public class AgentMemoryEraseService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryEraseService.class);

    @Autowired
    private AgentMemoryEntryRepository entryRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private AgentMemoryVectorStore agentMemoryVectorStore;

    /**
     * 按用户锚点双向擦除（关系库+向量库）并发擦除事件
     * @param userAnchor
     * @return
     */
    public int eraseByUser(String userAnchor) {
        return erase(AgentMemoryErasedEvent.SCOPE_USER, userAnchor, entryRepository.findByUserAnchor(userAnchor),
                () -> entryRepository.deleteByUserAnchor(userAnchor));
    }

    /**
     * 按Agent编码双向擦除（关系库+向量库）并发擦除事件
     * @param agentCode
     * @return
     */
    public int eraseByAgent(String agentCode) {
        return erase(AgentMemoryErasedEvent.SCOPE_AGENT, agentCode, entryRepository.findByAgentCode(agentCode),
                () -> entryRepository.deleteByAgentCode(agentCode));
    }

    /**
     * 执行擦除：向量库清理→关系库删除→发布审计事件
     * @param scope
     * @param target
     * @param entries
     * @param deleteAction
     * @return
     */
    private int erase(String scope, String target, List<AgentMemoryEntryInfo> entries, Runnable deleteAction) {
        int erased = 0;
        int vectorDeleted = -1;
        if (entries != null && !entries.isEmpty()) {
            if (agentMemoryVectorStore != null) {
                // 向量删除失败时返回-1，审计事件如实上报
                vectorDeleted = agentMemoryVectorStore.deleteByMemoryIds(entries.stream().map(AgentMemoryEntryInfo::getId).toList());
            }
            deleteAction.run();
            erased = entries.size();
        }
        eventPublisher.publishEvent(new AgentMemoryErasedEvent(scope, target, erased, vectorDeleted));
        log.info("Agent记忆擦除完成, scope={}, target={}, erased={}", scope, target, erased);
        return erased;
    }
}
