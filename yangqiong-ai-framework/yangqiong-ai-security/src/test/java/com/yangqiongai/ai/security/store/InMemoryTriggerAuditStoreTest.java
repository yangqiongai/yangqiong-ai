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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 护栏触发审计内存存储单元测试
 *
 * @author yangqiong
 */
@DisplayName("InMemoryTriggerAuditStore 测试")
class InMemoryTriggerAuditStoreTest {

    /**
     * 构建触发日志
     * @param ruleName
     * @return
     */
    private GuardrailTriggerLog buildLog(String ruleName) {
        GuardrailTriggerLog log = new GuardrailTriggerLog();
        log.setRuleName(ruleName);
        log.setHookPoint("INPUT");
        log.setAction("BLOCK");
        log.setCreateTime(LocalDateTime.now());
        return log;
    }

    @Test
    @DisplayName("保存后应可读取快照")
    void shouldSaveAndSnapshot() {
        InMemoryTriggerAuditStore store = new InMemoryTriggerAuditStore();
        store.save(buildLog("r1"));
        store.save(buildLog("r2"));

        List<GuardrailTriggerLog> snapshot = store.snapshot();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(0).getRuleName()).isEqualTo("r1");
        assertThat(snapshot.get(1).getRuleName()).isEqualTo("r2");
    }

    @Test
    @DisplayName("null日志应被忽略")
    void shouldIgnoreNullLog() {
        InMemoryTriggerAuditStore store = new InMemoryTriggerAuditStore();
        store.save(null);

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("超过容量上限时应淘汰最早的日志")
    void shouldEvictOldestWhenFull() {
        InMemoryTriggerAuditStore store = new InMemoryTriggerAuditStore();
        for (int i = 0; i < 1001; i++) {
            store.save(buildLog("r" + i));
        }

        List<GuardrailTriggerLog> snapshot = store.snapshot();
        assertThat(snapshot).hasSize(1000);
        assertThat(snapshot.get(0).getRuleName()).isEqualTo("r1");
        assertThat(snapshot.get(999).getRuleName()).isEqualTo("r1000");
    }
}
