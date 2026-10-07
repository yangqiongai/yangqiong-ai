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

import com.yangqiongai.ai.agent.registry.resolve.RegistryAgentDefinitionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 灰度缓存失效监听器
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class GrayCacheEvictListener {

    @Autowired
    private RegistryAgentDefinitionResolver resolver;

    /**
     * 发布后失效解析缓存
     * @param event
     */
    @EventListener
    public void onPublished(AgentPublishedEvent event) {
        resolver.evict(event.getAgentCode());
    }

    /**
     * 回滚后失效解析缓存
     * @param event
     */
    @EventListener
    public void onRollback(AgentRollbackEvent event) {
        resolver.evict(event.getAgentCode());
    }

    /**
     * 禁用后失效解析缓存
     * @param event
     */
    @EventListener
    public void onDisabled(AgentDisabledEvent event) {
        resolver.evict(event.getAgentCode());
    }
}
