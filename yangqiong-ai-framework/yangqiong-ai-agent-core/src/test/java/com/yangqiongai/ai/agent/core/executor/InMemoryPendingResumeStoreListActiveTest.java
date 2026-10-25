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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存暂停恢复登记存储只读枚举测试
 * @author yangqiong
 */
@DisplayName("InMemoryPendingResumeStore listActive 单元测试")
class InMemoryPendingResumeStoreListActiveTest {

    private final InMemoryPendingResumeStore store = new InMemoryPendingResumeStore();

    private PendingResumeEntry entry(String requestId, String userId, String resumeType, Instant expireTime) {
        PendingResumeEntry entry = new PendingResumeEntry();
        entry.setRequestId(requestId);
        entry.setUserId(userId);
        entry.setResumeType(resumeType);
        entry.setExpireTime(expireTime);
        entry.setCreatedAt(Instant.now());
        return entry;
    }

    @Test
    @DisplayName("登记不同用户的确认与澄清及过期条目时仅返回未过期两条")
    void listActiveFiltersExpiredEntries() {
        store.register(entry("confirm:s1", "u1", "CONFIRM", Instant.now().plusSeconds(1800)));
        store.register(entry("clarification:s2:tool1", "u2", "CLARIFICATION", Instant.now().plusSeconds(1800)));
        store.register(entry("confirm:s3", "u1", "CONFIRM", Instant.now().minusSeconds(1)));

        List<PendingResumeEntry> active = store.listActive();

        assertThat(active).hasSize(2);
        assertThat(active).extracting(PendingResumeEntry::getRequestId)
                .containsExactlyInAnyOrder("confirm:s1", "clarification:s2:tool1");
    }

    @Test
    @DisplayName("未设置过期时间的登记视为未过期")
    void listActiveKeepsEntriesWithoutExpireTime() {
        store.register(entry("confirm:s1", "u1", "CONFIRM", null));

        assertThat(store.listActive()).hasSize(1);
    }

    @Test
    @DisplayName("弹出登记后不再出现在枚举结果中")
    void listActiveReflectsPop() {
        store.register(entry("confirm:s1", "u1", "CONFIRM", Instant.now().plusSeconds(1800)));
        store.pop("confirm:s1");

        assertThat(store.listActive()).isEmpty();
    }

    @Test
    @DisplayName("无登记时返回空列表")
    void listActiveReturnsEmptyWhenNoRegistry() {
        assertThat(store.listActive()).isEmpty();
    }

    @Test
    @DisplayName("枚举为只读操作不改变登记状态")
    void listActiveDoesNotMutateRegistry() {
        store.register(entry("confirm:s1", "u1", "CONFIRM", Instant.now().plusSeconds(1800)));

        store.listActive();
        store.listActive();

        assertThat(store.exists("confirm:s1")).isTrue();
        assertThat(store.listActive()).hasSize(1);
    }
}
