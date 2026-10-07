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
package com.yangqiongai.ai.agent.runtime.durable;

import java.time.Duration;
import java.util.Optional;

/**
 * 运行锁存储
 * <p>
 * 持久执行SPI：防止多节点对同一运行并发续跑。
 * </p>
 * @author yangqiong
 */
public interface RunLockStore {

    /**
     * 尝试获取运行锁
     * @param runId
     * @param ownerNodeId
     * @param lockTtl
     * @return true时获取成功
     */
    boolean tryLock(String runId, String ownerNodeId, Duration lockTtl);

    /**
     * 续期运行锁
     * @param runId
     * @param ownerNodeId
     * @param lockTtl
     */
    void renew(String runId, String ownerNodeId, Duration lockTtl);

    /**
     * 释放运行锁
     * @param runId
     * @param ownerNodeId
     */
    void unlock(String runId, String ownerNodeId);

    /**
     * 查询锁持有节点
     * @param runId
     * @return
     */
    Optional<String> owner(String runId);
}
