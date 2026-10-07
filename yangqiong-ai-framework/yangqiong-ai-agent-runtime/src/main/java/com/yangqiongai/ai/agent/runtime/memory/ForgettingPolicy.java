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
package com.yangqiongai.ai.agent.runtime.memory;

/**
 * 记忆遗忘策略
 * <p>
 * 判定记忆条目是否应被遗忘，配合向量长期记忆在存储时清理过期条目。
 * </p>
 * @author yangqiong
 */
public interface ForgettingPolicy {

    /**
     * 记忆条目信息快照
     * @param memoryId
     * @param content
     * @param createdAt
     * @param lastAccessedAt
     * @param accessCount
     */
    record EntryInfo(String memoryId, String content, long createdAt,
                     long lastAccessedAt, int accessCount) {
    }

    /**
     * 判定记忆条目是否应被遗忘
     * @param info
     * @return
     */
    boolean shouldForget(EntryInfo info);
}
