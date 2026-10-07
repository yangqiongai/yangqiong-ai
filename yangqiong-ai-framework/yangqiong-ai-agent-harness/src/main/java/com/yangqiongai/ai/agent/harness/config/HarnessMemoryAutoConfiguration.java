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
package com.yangqiongai.ai.agent.harness.config;

import com.yangqiongai.ai.agent.harness.memory.HarnessLongTermMemoryBridge;
import com.yangqiongai.ai.agent.harness.RuntimeSpiBridge;
import com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Harness记忆自动配置
 * <p>
 * 仅当 classpath 存在 ai-memory 的 LongTermMemoryManager 时激活，
 * 注册框架 L3 长期记忆 SPI，供 HarnessAutoConfiguration 通过 ObjectProvider 消费。
 * ai-memory 缺席时本配置类不生效，harness 运行时行为与现状一致。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnClass({LongTermMemoryManager.class, AgentLongTermMemory.class})
@ConditionalOnProperty(prefix = "ai.agent.harness.memory", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(HarnessAutoConfiguration.class)
public class HarnessMemoryAutoConfiguration {

    /**
     * 注册L3长期记忆桥接器，委托 LongTermMemoryManager，以框架类型暴露
     * @param longTermMemoryManager
     * @param retrieveMaxTokens
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AgentLongTermMemory.class)
    public AgentLongTermMemory harnessLongTermMemoryBridge(
            LongTermMemoryManager longTermMemoryManager,
            @Value("${ai.memory.long-term.retrieve-max-tokens:${ai.conversation.long-term.retrieve-max-tokens:500}}") int retrieveMaxTokens) {
        return RuntimeSpiBridge.toRuntime(
                new HarnessLongTermMemoryBridge(longTermMemoryManager, retrieveMaxTokens));
    }
}
