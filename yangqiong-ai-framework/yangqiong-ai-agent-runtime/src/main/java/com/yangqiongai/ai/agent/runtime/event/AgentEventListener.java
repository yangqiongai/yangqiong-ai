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
package com.yangqiongai.ai.agent.runtime.event;

/**
 * Agent事件监听器
 * <p>
 * 注册后运行时事件自动广播至监听器，用于审计、指标采集与外部联动。
 * </p>
 * @author yangqiong
 */
public interface AgentEventListener {

    /**
     * 判断是否关注指定类型事件
     * @param type
     * @return true时接收该类型事件
     */
    default boolean isInterestedIn(AgentEventType type) {
        return true;
    }

    /**
     * 处理事件
     * @param event
     */
    void onEvent(AgentEvent event);
}
