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
package com.yangqiongai.ai.agent.data;

import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.core.repository.AgentTaskStepRepository;
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.agent.data.core.repository.DefaultAgentTaskRepository;
import com.yangqiongai.ai.agent.data.core.repository.DefaultAgentTaskStepRepository;
import com.yangqiongai.ai.agent.data.core.repository.DefaultAgentRepository;
import com.yangqiongai.ai.agent.data.core.repository.DefaultScheduleLogRepository;
import com.yangqiongai.ai.agent.data.core.repository.DefaultSchedulerRepository;
import com.yangqiongai.ai.agent.data.core.repository.DefaultUserWorkspaceRepository;
import com.yangqiongai.ai.agent.data.eval.repository.DefaultEvalDatasetRepository;
import com.yangqiongai.ai.agent.data.eval.repository.DefaultEvalRunRepository;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import com.yangqiongai.ai.agent.data.mcp.repository.DefaultMcpServerCategoryRepository;
import com.yangqiongai.ai.agent.data.mcp.repository.DefaultMcpServerConfigRepository;
import com.yangqiongai.ai.agent.data.registry.repository.AgentDirectoryRepository;
import com.yangqiongai.ai.agent.data.registry.repository.DefaultAgentDirectoryRepository;
import com.yangqiongai.ai.agent.data.tool.repository.DefaultToolConfigCategoryRepository;
import com.yangqiongai.ai.agent.data.tool.repository.DefaultToolConfigRepository;
import com.yangqiongai.ai.agent.data.tool.repository.DefaultToolUsageRepository;
import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.agent.data.trace.repository.DefaultContextSnapshotRepository;
import com.yangqiongai.ai.agent.data.trace.repository.DefaultTraceSpanRepository;
import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import com.yangqiongai.ai.agent.local.repository.UserWorkspaceRepository;
import com.yangqiongai.ai.agent.mcp.repository.McpServerCategoryRepository;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigRepository;
import com.yangqiongai.ai.agent.scheduler.repository.ScheduleLogRepository;
import com.yangqiongai.ai.agent.scheduler.repository.SchedulerRepository;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigCategoryRepository;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigRepository;
import com.yangqiongai.ai.agent.tool.repository.ToolUsageRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent数据层自动配置
 * @author yangqiong
 */
@Configuration
public class AiAgentDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentTaskRepository.class)
    public AgentTaskRepository agentTaskRepository() {
        return new DefaultAgentTaskRepository();
    }

    @Bean
    @ConditionalOnMissingBean(AgentTaskStepRepository.class)
    public AgentTaskStepRepository agentTaskStepRepository() {
        return new DefaultAgentTaskStepRepository();
    }

    @Bean
    @ConditionalOnMissingBean(AgentRepository.class)
    public AgentRepository agentRepository() {
        return new DefaultAgentRepository();
    }

    @Bean
    @ConditionalOnMissingBean(McpServerConfigRepository.class)
    public McpServerConfigRepository mcpServerConfigRepository() {
        return new DefaultMcpServerConfigRepository();
    }

    @Bean
    @ConditionalOnMissingBean(McpServerCategoryRepository.class)
    public McpServerCategoryRepository mcpServerCategoryRepository() {
        return new DefaultMcpServerCategoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(SchedulerRepository.class)
    public SchedulerRepository schedulerRepository() {
        return new DefaultSchedulerRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ScheduleLogRepository.class)
    public ScheduleLogRepository scheduleLogRepository() {
        return new DefaultScheduleLogRepository();
    }

    @Bean
    @ConditionalOnMissingBean(UserWorkspaceRepository.class)
    public UserWorkspaceRepository userWorkspaceRepository() {
        return new DefaultUserWorkspaceRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ToolConfigRepository.class)
    public ToolConfigRepository toolConfigRepository() {
        return new DefaultToolConfigRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ToolConfigCategoryRepository.class)
    public ToolConfigCategoryRepository toolConfigCategoryRepository() {
        return new DefaultToolConfigCategoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(AgentDirectoryRepository.class)
    public AgentDirectoryRepository agentDirectoryRepository() {
        return new DefaultAgentDirectoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ToolUsageRepository.class)
    public ToolUsageRepository toolUsageRepository() {
        return new DefaultToolUsageRepository();
    }

    @Bean
    @ConditionalOnMissingBean(TraceSpanRepository.class)
    public TraceSpanRepository traceSpanRepository() {
        return new DefaultTraceSpanRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ContextSnapshotRepository.class)
    public ContextSnapshotRepository contextSnapshotRepository() {
        return new DefaultContextSnapshotRepository();
    }

    @Bean
    @ConditionalOnMissingBean(EvalDatasetRepository.class)
    public EvalDatasetRepository evalDatasetRepository() {
        return new DefaultEvalDatasetRepository();
    }

    @Bean
    @ConditionalOnMissingBean(EvalRunRepository.class)
    public EvalRunRepository evalRunRepository() {
        return new DefaultEvalRunRepository();
    }
}