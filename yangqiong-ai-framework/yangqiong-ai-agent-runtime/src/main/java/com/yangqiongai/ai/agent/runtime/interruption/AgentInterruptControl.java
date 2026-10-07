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
package com.yangqiongai.ai.agent.runtime.interruption;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

/**
 * Agent中断控制
 * @author yangqiong
 */
public interface AgentInterruptControl {

    /**
     * 触发中断
     * @param source
     * @param message
     */
    void trigger(AgentInterruptSource source, AgentMessage message);

    /**
     * 查询是否已被中断
     * @return
     */
    default boolean isInterrupted() {
        return false;
    }

    /**
     * 重置中断状态
     */
    default void reset() {
    }
}
