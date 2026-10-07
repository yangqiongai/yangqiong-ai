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
package com.yangqiongai.ai.agent.core.stream;

import com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl;
import com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中断控制注册表，按会话ID管理InterruptControl实例
 * <p>
 * 流式执行开始时注册InterruptControl，外部API通过sessionId触发中断。
 * 执行结束后自动清理注册。
 * </p>
 *
 * @author yangqiong
 */
@Component
public class InterruptControlRegistry {

    private static final Logger log = LoggerFactory.getLogger(InterruptControlRegistry.class);

    private final Map<String, AgentInterruptControl> registry = new ConcurrentHashMap<>();

    /**
     * 注册会话的InterruptControl
     * @param sessionId
     * @param control
     */
    public void register(String sessionId, AgentInterruptControl control) {
        registry.put(sessionId, control);
        log.debug("注册中断控制: sessionId={}", sessionId);
    }

    /**
     * 注销会话的InterruptControl
     * @param sessionId
     */
    public void unregister(String sessionId) {
        registry.remove(sessionId);
        log.debug("注销中断控制: sessionId={}", sessionId);
    }

    /**
     * 触发会话中断
     * @param sessionId
     * @param reason
     * @return 是否成功触发（会话存在且未中断时返回true）
     */
    public boolean interrupt(String sessionId, String reason) {
        AgentInterruptControl control = registry.get(sessionId);
        if (control == null) {
            log.warn("未找到会话的中断控制: sessionId={}", sessionId);
            return false;
        }
        control.trigger(AgentInterruptSource.USER, AgentMessage.builder()
                .name("system")
                .role(AgentMessageRole.SYSTEM)
                .content(List.of(AgentTextBlock.builder().text(reason != null ? reason : "用户取消").build()))
                .build());
        log.info("已触发会话中断: sessionId={}, reason={}", sessionId, reason);
        return true;
    }

    /**
     * 检查会话是否已注册
     * @param sessionId
     * @return
     */
    public boolean isRegistered(String sessionId) {
        return registry.containsKey(sessionId);
    }
}
