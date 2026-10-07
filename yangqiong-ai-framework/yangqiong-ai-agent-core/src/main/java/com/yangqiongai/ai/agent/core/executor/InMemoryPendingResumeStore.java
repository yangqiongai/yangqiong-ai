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
package com.yangqiongai.ai.agent.core.executor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存暂停恢复登记存储
 * <p>
 * 单机默认实现，登记仅存本进程内存，进程重启后暂停的恢复自然失效；
 * 内存句柄随登记携带，恢复走原有同节点快照续跑路径。
 * </p>
 * @author yangqiong
 */
public class InMemoryPendingResumeStore implements PendingResumeStore {

    private final Map<String, PendingResumeEntry> registry = new ConcurrentHashMap<>();

    @Override
    public void register(PendingResumeEntry entry) {
        registry.put(entry.getRequestId(), entry);
    }

    @Override
    public PendingResumeEntry pop(String requestId) {
        return registry.remove(requestId);
    }

    @Override
    public boolean exists(String requestId) {
        return registry.containsKey(requestId);
    }
}
