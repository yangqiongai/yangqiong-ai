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
package com.yangqiongai.ai.memory.spi;

import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;

/**
 * 记忆读写增强（商业实现挂载点：去重、衰减等进阶能力经此接入社区记忆链路）
 * @author yangqiong
 */
public interface MemoryEnhancer {

    /**
     * 写入前增强检查，返回去重决策（社区核心按决策执行 ADD/UPDATE/SKIP/CONFLICT/DELETE）
     * @param userId
     * @param content
     * @param key
     * @param memoryType
     * @return
     */
    DedupDecision onStore(String userId, String content, String key, String memoryType);

    /**
     * 检索时增强，返回记忆有效分数用于相关性排序
     * @param memory
     * @return
     */
    double onRecall(UserLongTermMemoryInfo memory);
}
