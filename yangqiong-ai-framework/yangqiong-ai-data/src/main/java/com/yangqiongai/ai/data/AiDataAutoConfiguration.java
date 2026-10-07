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
package com.yangqiongai.ai.data;

import com.yangqiongai.ai.approval.repository.PendingRequestRepository;
import com.yangqiongai.ai.data.approval.repository.DefaultPendingRequestRepository;
import com.yangqiongai.ai.data.capability.repository.DefaultCapabilityCallRepository;
import com.yangqiongai.ai.data.capability.repository.DefaultCapabilityCategoryRepository;
import com.yangqiongai.ai.data.capability.repository.DefaultCapabilityDefinitionHistoryRepository;
import com.yangqiongai.ai.data.capability.repository.DefaultCapabilityDefinitionRepository;
import com.yangqiongai.ai.data.capability.repository.DefaultDataContextRepository;
import com.yangqiongai.ai.data.capability.repository.DefaultRequestDedupRepository;
import com.yangqiongai.ai.data.llm.repository.DefaultModelInfoRepository;
import com.yangqiongai.ai.data.memory.agentmemory.repository.DefaultAgentMemoryEntryRepository;
import com.yangqiongai.ai.data.memory.agentmemory.repository.DefaultOrgContextRepository;
import com.yangqiongai.ai.data.memory.repository.DefaultChatMemoryRepository;
import com.yangqiongai.ai.data.memory.repository.DefaultConversationSessionRepository;
import com.yangqiongai.ai.data.memory.repository.DefaultUserLongTermMemoryRepository;
import com.yangqiongai.ai.data.rag.repository.DefaultSliceRecordRepository;
import com.yangqiongai.ai.data.workflow.repository.DefaultWorkflowDefinitionRepository;
import com.yangqiongai.ai.data.workflow.repository.DefaultWorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.data.workflow.repository.DefaultWorkflowNodeTraceRepository;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.repository.OrgContextRepository;
import com.yangqiongai.ai.memory.repository.ChatMemoryRepository;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionRepository;
import com.yangqiongai.ai.open.capability.context.DataContextRepository;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRepository;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowDefinitionRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowNodeTraceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 框架数据层自动配置
 * @author yangqiong
 */
@Configuration
public class AiDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PendingRequestRepository.class)
    public PendingRequestRepository pendingRequestRepository() {
        return new DefaultPendingRequestRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ModelInfoRepository.class)
    public ModelInfoRepository modelInfoRepository() {
        return new DefaultModelInfoRepository();
    }

    @Bean
    @ConditionalOnMissingBean(SliceRecordRepository.class)
    public SliceRecordRepository sliceRecordRepository() {
        return new DefaultSliceRecordRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository chatMemoryRepository() {
        return new DefaultChatMemoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ConversationSessionRepository.class)
    public ConversationSessionRepository conversationSessionRepository() {
        return new DefaultConversationSessionRepository();
    }

    @Bean
    @ConditionalOnMissingBean(UserLongTermMemoryRepository.class)
    public UserLongTermMemoryRepository userLongTermMemoryRepository() {
        return new DefaultUserLongTermMemoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(AgentMemoryEntryRepository.class)
    public AgentMemoryEntryRepository agentMemoryEntryRepository() {
        return new DefaultAgentMemoryEntryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(OrgContextRepository.class)
    public OrgContextRepository orgContextRepository() {
        return new DefaultOrgContextRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityCallRepository.class)
    public CapabilityCallRepository capabilityCallRepository() {
        return new DefaultCapabilityCallRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityDefinitionRepository.class)
    public CapabilityDefinitionRepository capabilityDefinitionRepository() {
        return new DefaultCapabilityDefinitionRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityDefinitionHistoryRepository.class)
    public CapabilityDefinitionHistoryRepository capabilityDefinitionHistoryRepository() {
        return new DefaultCapabilityDefinitionHistoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CapabilityCategoryRepository.class)
    public CapabilityCategoryRepository capabilityCategoryRepository() {
        return new DefaultCapabilityCategoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(DataContextRepository.class)
    public DataContextRepository dataContextRepository() {
        return new DefaultDataContextRepository();
    }

    @Bean
    @ConditionalOnMissingBean(DefaultRequestDedupRepository.class)
    public DefaultRequestDedupRepository requestDedupRepository() {
        return new DefaultRequestDedupRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowDefinitionRepository.class)
    public WorkflowDefinitionRepository workflowDefinitionRepository() {
        return new DefaultWorkflowDefinitionRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WorkflowExecutionHistoryRepository.class)
    public WorkflowExecutionHistoryRepository workflowExecutionHistoryRepository() {
        return new DefaultWorkflowExecutionHistoryRepository();
    }

    /**
     * 节点轨迹持久化实现，标记Primary以确定覆盖Noop兜底实现
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(DefaultWorkflowNodeTraceRepository.class)
    public WorkflowNodeTraceRepository workflowNodeTraceRepository() {
        return new DefaultWorkflowNodeTraceRepository();
    }
}
