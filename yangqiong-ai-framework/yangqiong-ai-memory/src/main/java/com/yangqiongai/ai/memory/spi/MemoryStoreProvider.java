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

import com.yangqiongai.ai.memory.repository.ChatMemoryRepository;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;

/**
 * 记忆存储提供者（商业分布式实现挂载点：v2 Redis/DB 等存储经此替换社区默认存储）
 * @author yangqiong
 */
public interface MemoryStoreProvider {

    /**
     * 提供对话记忆存储仓储，返回 null 时使用社区默认实现
     * @return
     */
    ChatMemoryRepository getChatMemoryRepository();

    /**
     * 提供用户长期记忆存储仓储，返回 null 时使用社区默认实现
     * @return
     */
    UserLongTermMemoryRepository getUserLongTermMemoryRepository();
}
