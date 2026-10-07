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
package com.yangqiongai.ai.memory.agent;

/**
 * 记忆整理动作
 * @author yangqiong
 */
public enum MemoryManagerAction {

    /**
     * 合并：将相似记忆合并为一条新记忆，删除原记忆
     */
    MERGE,

    /**
     * 归档：标记为 DEPRECATED，从检索中排除
     */
    ARCHIVE,

    /**
     * 遗忘：彻底删除记忆（含向量索引）
     */
    FORGET,

    /**
     * 保留：不做任何操作
     */
    KEEP
}
