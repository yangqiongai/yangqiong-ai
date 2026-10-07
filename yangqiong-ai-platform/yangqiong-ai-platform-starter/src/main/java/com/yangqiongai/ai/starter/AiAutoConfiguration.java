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
package com.yangqiongai.ai.starter;

import com.yangqiongai.ai.agent.harness.config.HarnessAutoConfiguration;
import com.yangqiongai.ai.agent.core.configuration.AgentCoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * AI平台自动配置入口
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@Import({
    com.yangqiongai.ai.storage.StorageAutoConfiguration.class,
    com.yangqiongai.ai.platform.knowledge.KnowledgeAutoConfiguration.class,
    com.yangqiongai.ai.memory.config.MemoryAutoConfiguration.class,
    com.yangqiongai.ai.llm.ModelAutoConfiguration.class,
    com.yangqiongai.ai.evaluation.EvaluationAutoConfiguration.class,
        com.yangqiongai.ai.agent.harness.config.HarnessAutoConfiguration.class
})
public class AiAutoConfiguration {

    /**
     * Agent核心模块条件配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ai.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import({
        AgentCoreAutoConfiguration.class,
        com.yangqiongai.ai.agent.tool.ToolAutoConfiguration.class,
        com.yangqiongai.ai.approval.ApprovalAutoConfiguration.class
    })
    static class AgentCoreConfiguration {
    }

    /**
     * RAG模块条件配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import({
        com.yangqiongai.ai.rag.RagAutoConfiguration.class,
        com.yangqiongai.ai.agent.rag.AgentRagAutoConfiguration.class
    })
    static class RagConfiguration {
    }

    /**
     * MCP模块条件配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ai.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import(com.yangqiongai.ai.agent.mcp.McpAutoConfiguration.class)
    static class McpConfiguration {
    }

    /**
     * Guardrails模块条件配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ai.guardrails", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import(com.yangqiongai.ai.security.SecurityAutoConfiguration.class)
    static class GuardrailsConfiguration {
    }

    /**
     * Skill模块条件配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ai.skill", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Import(com.yangqiongai.ai.agent.skill.SkillAutoConfiguration.class)
    static class SkillConfiguration {
    }
}
