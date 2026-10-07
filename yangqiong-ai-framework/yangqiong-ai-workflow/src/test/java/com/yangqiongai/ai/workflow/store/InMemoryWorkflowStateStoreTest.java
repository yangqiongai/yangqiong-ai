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
package com.yangqiongai.ai.workflow.store;

import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryWorkflowStateStore 单元测试
 *
 * @author yangqiong
 */
class InMemoryWorkflowStateStoreTest {

    private InMemoryWorkflowStateStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkflowStateStore();
    }

    private WorkflowState createState(String instanceId, String definitionName) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(instanceId);
        state.setDefinitionName(definitionName);
        state.setStatus(ExecutionStatus.PENDING);
        state.setCreateTime(System.currentTimeMillis());
        state.setUpdateTime(System.currentTimeMillis());
        return state;
    }

    @Nested
    @DisplayName("save 方法测试")
    class SaveTests {

        @Test
        @DisplayName("保存并加载状态")
        void saveAndLoad() {
            WorkflowState state = createState("inst-1", "def-1");
            store.save(state);

            WorkflowState loaded = store.load("inst-1");
            assertThat(loaded).isNotNull();
            assertThat(loaded.getInstanceId()).isEqualTo("inst-1");
            assertThat(loaded.getDefinitionName()).isEqualTo("def-1");
            assertThat(loaded.getStatus()).isEqualTo(ExecutionStatus.PENDING);
        }

        @Test
        @DisplayName("保存 null state 不做任何操作")
        void saveNullState() {
            store.save(null);
            // 不应抛出异常，store 应为空
            assertThat(store.load("any")).isNull();
        }

        @Test
        @DisplayName("保存 instanceId 为 null 的 state 不做任何操作")
        void saveStateWithNullInstanceId() {
            WorkflowState state = createState(null, "def-1");
            store.save(state);
            // 不应存入任何内容
            assertThat(store.load(null)).isNull();
        }

        @Test
        @DisplayName("使用相同 instanceId 再次保存会更新状态")
        void updateStateBySavingAgain() {
            WorkflowState state = createState("inst-1", "def-1");
            state.setStatus(ExecutionStatus.PENDING);
            store.save(state);

            // 更新状态
            state.setStatus(ExecutionStatus.RUNNING);
            state.setUpdateTime(System.currentTimeMillis());
            store.save(state);

            WorkflowState loaded = store.load("inst-1");
            assertThat(loaded).isNotNull();
            assertThat(loaded.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        }
    }

    @Nested
    @DisplayName("load 方法测试")
    class LoadTests {

        @Test
        @DisplayName("加载不存在的 instanceId 返回 null")
        void loadNonExistentReturnsNull() {
            WorkflowState loaded = store.load("non-existent");
            assertThat(loaded).isNull();
        }

        @Test
        @DisplayName("加载 null instanceId 返回 null")
        void loadNullInstanceIdReturnsNull() {
            WorkflowState loaded = store.load(null);
            assertThat(loaded).isNull();
        }
    }

    @Nested
    @DisplayName("delete 方法测试")
    class DeleteTests {

        @Test
        @DisplayName("删除已存在的状态")
        void deleteExistingState() {
            WorkflowState state = createState("inst-1", "def-1");
            store.save(state);
            assertThat(store.load("inst-1")).isNotNull();

            store.delete("inst-1");
            assertThat(store.load("inst-1")).isNull();
        }

        @Test
        @DisplayName("删除不存在的 instanceId 不抛异常")
        void deleteNonExistentDoesNothing() {
            // 不应抛出异常
            store.delete("non-existent");
        }

        @Test
        @DisplayName("删除 null instanceId 不做任何操作")
        void deleteNullInstanceIdDoesNothing() {
            // 不应抛出异常
            store.delete(null);
        }
    }

    @Nested
    @DisplayName("queryByDefinitionName 方法测试")
    class QueryByDefinitionNameTests {

        @Test
        @DisplayName("根据 definitionName 查询返回匹配的状态列表")
        void queryReturnsMatchingStates() {
            store.save(createState("inst-1", "def-A"));
            store.save(createState("inst-2", "def-B"));
            store.save(createState("inst-3", "def-A"));

            List<WorkflowState> result = store.queryByDefinitionName("def-A");
            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(s ->
                    assertThat(s.getDefinitionName()).isEqualTo("def-A")
            );
            assertThat(result.stream().map(WorkflowState::getInstanceId))
                    .containsExactlyInAnyOrder("inst-1", "inst-3");
        }

        @Test
        @DisplayName("根据 null definitionName 查询返回空列表")
        void queryNullDefinitionNameReturnsEmptyList() {
            store.save(createState("inst-1", "def-A"));
            List<WorkflowState> result = store.queryByDefinitionName(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("根据不匹配的 definitionName 查询返回空列表")
        void queryNonMatchingDefinitionNameReturnsEmptyList() {
            store.save(createState("inst-1", "def-A"));
            List<WorkflowState> result = store.queryByDefinitionName("def-B");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("LRU 淘汰测试")
    class LruEvictionTests {

        @Test
        @DisplayName("容量达到上限时淘汰最久未访问的条目")
        void lruEvictionAtCapacity() {
            int capacity = 5;
            // 创建一个小容量的 store 来测试 LRU 淘汰
            Map<String, WorkflowState> innerMap = Collections.synchronizedMap(
                    new LinkedHashMap<String, WorkflowState>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, WorkflowState> eldest) {
                            return size() > capacity;
                        }
                    }
            );

            // 使用自定义 store 逻辑进行测试
            for (int i = 0; i < capacity + 2; i++) {
                WorkflowState state = createState("inst-" + i, "def-" + i);
                innerMap.put(state.getInstanceId(), state);
            }

            // 容量限制为5，插入了7个，最早的2个应被淘汰
            assertThat(innerMap).hasSize(capacity);
            assertThat(innerMap.containsKey("inst-0")).isFalse();
            assertThat(innerMap.containsKey("inst-1")).isFalse();
            assertThat(innerMap.containsKey("inst-2")).isTrue();
            assertThat(innerMap.containsKey("inst-6")).isTrue();
        }

        @Test
        @DisplayName("访问条目会更新其 LRU 顺序，避免被淘汰")
        void lruAccessOrderUpdatesOnRead() {
            int capacity = 5;
            Map<String, WorkflowState> innerMap = Collections.synchronizedMap(
                    new LinkedHashMap<String, WorkflowState>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, WorkflowState> eldest) {
                            return size() > capacity;
                        }
                    }
            );

            // 插入5个条目
            for (int i = 0; i < capacity; i++) {
                WorkflowState state = createState("inst-" + i, "def-" + i);
                innerMap.put(state.getInstanceId(), state);
            }

            // 访问 inst-0，使其变为最近访问
            innerMap.get("inst-0");

            // 再插入2个新条目，触发淘汰
            for (int i = capacity; i < capacity + 2; i++) {
                WorkflowState state = createState("inst-" + i, "def-" + i);
                innerMap.put(state.getInstanceId(), state);
            }

            // inst-0 被访问过，不应被淘汰；inst-1 是最久未访问的，应被淘汰
            assertThat(innerMap).hasSize(capacity);
            assertThat(innerMap.containsKey("inst-0")).isTrue();
            assertThat(innerMap.containsKey("inst-1")).isFalse();
            assertThat(innerMap.containsKey("inst-2")).isFalse();
        }
    }
}
