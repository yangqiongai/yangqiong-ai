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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存运行锁存储
 * <p>
 * 单进程兜底实现：按 runId 维护持有者与过期时间，超时按系统时钟判断。
 * 同节点重复 tryLock 视为续期，保证引擎多断点场景幂等。
 * </p>
 * @author yangqiong
 */
public class InMemoryRunLockStore implements RunLockStore {

    /**
     * 锁条目
     */
    private static final class LockEntry {

        /**
         * 持有节点ID
         */
        private final String ownerNodeId;

        /**
         * 过期时间戳（毫秒）
         */
        private final long expiresAt;

        LockEntry(String ownerNodeId, long expiresAt) {
            this.ownerNodeId = ownerNodeId;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * runId到锁条目的索引
     */
    private final Map<String, LockEntry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
        if (runId == null || ownerNodeId == null || lockTtl == null) {
            return false;
        }
        LockEntry candidate = new LockEntry(ownerNodeId, System.currentTimeMillis() + lockTtl.toMillis());
        return locks.compute(runId, (key, existing) -> {
            if (existing == null || existing.expiresAt <= System.currentTimeMillis()
                    || existing.ownerNodeId.equals(ownerNodeId)) {
                return candidate;
            }
            return existing;
        }) == candidate;
    }

    @Override
    public void renew(String runId, String ownerNodeId, Duration lockTtl) {
        locks.computeIfPresent(runId, (key, existing) -> {
            if (existing.ownerNodeId.equals(ownerNodeId) && existing.expiresAt > System.currentTimeMillis()) {
                return new LockEntry(ownerNodeId, System.currentTimeMillis() + lockTtl.toMillis());
            }
            return existing;
        });
    }

    @Override
    public void unlock(String runId, String ownerNodeId) {
        locks.computeIfPresent(runId, (key, existing) ->
                existing.ownerNodeId.equals(ownerNodeId) ? null : existing);
    }

    @Override
    public Optional<String> owner(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        LockEntry entry = locks.get(runId);
        if (entry == null || entry.expiresAt <= System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(entry.ownerNodeId);
    }
}
