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
package com.yangqiongai.ai.security.store;

import com.yangqiongai.ai.security.spi.GuardrailTriggerLog;
import com.yangqiongai.ai.security.spi.TriggerAuditStore;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 护栏触发审计内存存储（社区默认实现，环形缓冲防止内存膨胀；商业触发审计以JDBC存储@Primary覆盖）
 *
 * @author yangqiong
 */
public class InMemoryTriggerAuditStore implements TriggerAuditStore {

    /**
     * 内存保留的最大触发日志条数
     */
    private static final int MAX_RETAIN = 1000;

    private final Deque<GuardrailTriggerLog> logs = new ArrayDeque<>();

    @Override
    public synchronized void save(GuardrailTriggerLog log) {
        if (log == null) {
            return;
        }
        if (logs.size() >= MAX_RETAIN) {
            logs.pollFirst();
        }
        logs.addLast(log);
    }

    /**
     * 读取最近保留的触发日志（新日志在后，供调试与测试观测）
     * @return
     */
    public synchronized List<GuardrailTriggerLog> snapshot() {
        return List.copyOf(logs);
    }
}
