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
package com.yangqiongai.ai.agent.harness.memory;

import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Harness长期记忆桥接器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class HarnessLongTermMemoryBridgeTest {

    @Mock
    private LongTermMemoryManager longTermMemoryManager;

    @AfterEach
    void clearContext() {
        SessionContext.clear();
    }

    /**
     * 存储记忆委托给 mergeSessionSummary（存储为异步执行，需等待异步任务完成）
     */
    @Test
    void storeDelegatesToMergeSessionSummary() {
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        bridge.store("u1", "s1", "content", null);
        verify(longTermMemoryManager, timeout(2000)).mergeSessionSummary("u1", "s1", "content");
    }

    /**
     * userId 为空时从 SessionContext 兜底（存储为异步执行，需等待异步任务完成）
     */
    @Test
    void storeWithNullUserIdUsesSessionContext() {
        SessionContext.setUserId("u-ctx");
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        bridge.store(null, "s1", "content", null);
        verify(longTermMemoryManager, timeout(2000)).mergeSessionSummary("u-ctx", "s1", "content");
    }

    /**
     * userId 与 SessionContext 均空时为空操作
     */
    @Test
    void storeWithNullUserIdAndNoSessionContextIsNoOp() {
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        bridge.store(null, "s1", "content", null);
        verifyNoInteractions(longTermMemoryManager);
    }

    /**
     * 内容为空时为空操作
     */
    @Test
    void storeWithBlankContentIsNoOp() {
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        bridge.store("u1", "s1", "   ", null);
        verifyNoInteractions(longTermMemoryManager);
    }

    /**
     * 存储异常被吞咽，不向外抛出
     */
    @Test
    void storeSwallowsException() {
        doThrow(new RuntimeException("db error")).when(longTermMemoryManager)
                .mergeSessionSummary("u1", "s1", "content");
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        bridge.store("u1", "s1", "content", null);
        verify(longTermMemoryManager, timeout(2000)).mergeSessionSummary("u1", "s1", "content");
    }

    /**
     * 检索记忆委托给 loadByQuery 并包装为单元素列表，始终使用 retrieveMaxTokens 作为 Token 预算
     */
    @Test
    void searchDelegatesToLoadByQueryReturnsSingletonList() {
        when(longTermMemoryManager.loadByQuery("u1", "query", 500)).thenReturn("memory-text");
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        List<String> result = bridge.search("u1", "query", 5);
        assertThat(result).containsExactly("memory-text");
    }

    /**
     * 查询为空时返回空列表且不调用底层
     */
    @Test
    void searchWithBlankQueryReturnsEmpty() {
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        List<String> result = bridge.search("u1", "  ", 5);
        assertThat(result).isEmpty();
        verifyNoInteractions(longTermMemoryManager);
    }

    /**
     * 底层返回空串时包装为空列表
     */
    @Test
    void searchWithBlankResultReturnsEmpty() {
        when(longTermMemoryManager.loadByQuery("u1", "query", 500)).thenReturn("");
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        List<String> result = bridge.search("u1", "query", 5);
        assertThat(result).isEmpty();
    }

    /**
     * 检索异常被吞咽并返回空列表
     */
    @Test
    void searchSwallowsExceptionReturnsEmpty() {
        when(longTermMemoryManager.loadByQuery("u1", "query", 500))
                .thenThrow(new RuntimeException("db error"));
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        List<String> result = bridge.search("u1", "query", 5);
        assertThat(result).isEmpty();
    }

    /**
     * 检索时 userId 为空从 SessionContext 兜底
     */
    @Test
    void searchWithNullUserIdUsesSessionContext() {
        SessionContext.setUserId("u-ctx");
        when(longTermMemoryManager.loadByQuery("u-ctx", "query", 500)).thenReturn("memory");
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        List<String> result = bridge.search(null, "query", 5);
        assertThat(result).containsExactly("memory");
    }

    /**
     * 删除记忆为空操作且不调用底层
     */
    @Test
    void deleteIsNoOp() {
        HarnessLongTermMemoryBridge bridge = new HarnessLongTermMemoryBridge(longTermMemoryManager, 500);
        bridge.delete("m1");
        verifyNoInteractions(longTermMemoryManager);
    }
}
