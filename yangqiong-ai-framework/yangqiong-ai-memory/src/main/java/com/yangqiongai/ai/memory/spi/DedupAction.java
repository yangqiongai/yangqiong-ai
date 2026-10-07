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

/**
 * 记忆去重动作
 * @author yangqiong
 */
public enum DedupAction {

    /**
     * 新建
     */
    ADD,

    /**
     * 更新已有
     */
    UPDATE,

    /**
     * 跳过（重复）
     */
    SKIP,

    /**
     * 冲突（同key不同内容）
     */
    CONFLICT,

    /**
     * 删除旧记忆（严重冲突）
     */
    DELETE
}
