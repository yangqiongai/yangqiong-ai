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
package com.yangqiongai.ai.memory.agentmemory.event;

/**
 * Agent记忆擦除事件（企业侧监听后写审计链闭环）
 * @author yangqiong
 */
public class AgentMemoryErasedEvent {

    /**
     * 擦除维度（USER=按用户锚点，AGENT=按Agent编码）
     */
    public static final String SCOPE_USER = "USER";

    /**
     * 擦除维度：按Agent编码
     */
    public static final String SCOPE_AGENT = "AGENT";

    /**
     * 擦除维度
     */
    private final String scope;

    /**
     * 擦除目标（用户锚点或Agent编码）
     */
    private final String target;

    /**
     * 擦除条数
     */
    private final int erasedCount;

    /**
     * 向量清除条数（-1表示向量库不可用未清除）
     */
    private final int vectorDeleted;

    public AgentMemoryErasedEvent(String scope, String target, int erasedCount, int vectorDeleted) {
        this.scope = scope;
        this.target = target;
        this.erasedCount = erasedCount;
        this.vectorDeleted = vectorDeleted;
    }

    public String getScope() {
        return scope;
    }

    public String getTarget() {
        return target;
    }

    public int getErasedCount() {
        return erasedCount;
    }

    public int getVectorDeleted() {
        return vectorDeleted;
    }
}
