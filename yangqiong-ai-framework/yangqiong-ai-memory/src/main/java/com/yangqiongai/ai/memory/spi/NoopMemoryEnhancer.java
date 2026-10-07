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
 * 记忆读写增强默认实现（社区缺省行为：不做去重与衰减）
 * @author yangqiong
 */
public class NoopMemoryEnhancer implements MemoryEnhancer {

    /**
     * 写入前增强检查，社区缺省不做去重，一律返回新建决策
     * @param userId
     * @param content
     * @param key
     * @param memoryType
     * @return
     */
    @Override
    public DedupDecision onStore(String userId, String content, String key, String memoryType) {
        return DedupDecision.add();
    }

    /**
     * 检索时增强，社区缺省降级为记忆重要性分数
     * @param memory
     * @return
     */
    @Override
    public double onRecall(UserLongTermMemoryInfo memory) {
        if (memory == null) {
            return 0;
        }
        return memory.getImportanceScore() != null ? memory.getImportanceScore() : 0;
    }
}
