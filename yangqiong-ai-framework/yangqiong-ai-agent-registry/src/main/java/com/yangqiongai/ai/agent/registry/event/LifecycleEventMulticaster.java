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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 生命周期事件多播器
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class LifecycleEventMulticaster {

    @Autowired(required = false)
    private List<LifecycleListener> listeners;

    /**
     * 转发发布事件
     * @param event
     */
    @EventListener
    public void onPublished(AgentPublishedEvent event) {
        if (listeners == null) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.onPublished(event);
        }
    }

    /**
     * 转发回滚事件
     * @param event
     */
    @EventListener
    public void onRollback(AgentRollbackEvent event) {
        if (listeners == null) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.onRollback(event);
        }
    }

    /**
     * 转发禁用事件
     * @param event
     */
    @EventListener
    public void onDisabled(AgentDisabledEvent event) {
        if (listeners == null) {
            return;
        }
        for (LifecycleListener listener : listeners) {
            listener.onDisabled(event);
        }
    }
}
