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
package com.yangqiongai.ai.data.manager;

import com.yangqiongai.ai.memory.LongTermMemoryManager;
import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * loadByQuery 单元测试
 * @author yangqiong
 */
@DisplayName("loadByQuery 单元测试")
class LongTermMemoryManagerLoadByQueryTest {

    @Mock
    private UserLongTermMemoryRepository userLongTermMemoryRepository;

    private LongTermMemoryManager service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new LongTermMemoryManager();
        injectField(service, "userLongTermMemoryRepository", userLongTermMemoryRepository);
        injectField(service, "maxContentChars", 2000);
        injectField(service, "vectorEnabled", false);
        injectField(service, "graphRetrievalEnabled", false);
    }

    @Test
    @DisplayName("关键词匹配：返回包含查询关键词的记忆")
    void loadByQuery_keywordMatch_returnsMemory() {
        when(userLongTermMemoryRepository.findMemoriesForKeywordMatch("user1")).thenReturn(List.of(
                buildMemory(1L, "用户喜欢喝咖啡"),
                buildMemory(2L, "今天天气不错")
        ));

        String result = service.loadByQuery("user1", "咖啡", 1000);

        assertThat(result).contains("咖啡");
        assertThat(result).doesNotContain("天气");
    }

    @Test
    @DisplayName("相关性排序：按相关性降序返回，高匹配记忆在前")
    void loadByQuery_multipleMatches_sortedByRelevance() {
        when(userLongTermMemoryRepository.findMemoriesForKeywordMatch("user1")).thenReturn(List.of(
                buildMemory(1L, "咖啡"),
                buildMemory(2L, "用户喜欢喝咖啡和茶")
        ));

        String result = service.loadByQuery("user1", "咖啡 茶", 1000);

        assertThat(result).startsWith("用户喜欢喝咖啡和茶");
    }

    @Test
    @DisplayName("无匹配：返回空字符串")
    void loadByQuery_noMatch_returnsEmpty() {
        when(userLongTermMemoryRepository.findMemoriesForKeywordMatch("user1")).thenReturn(List.of(
                buildMemory(1L, "今天天气不错")
        ));

        String result = service.loadByQuery("user1", "咖啡", 1000);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Token 预算裁剪：预算为 0 时所有记忆被跳过")
    void loadByQuery_tokenBudgetZero_returnsEmpty() {
        when(userLongTermMemoryRepository.findMemoriesForKeywordMatch("user1")).thenReturn(List.of(
                buildMemory(1L, "咖啡")
        ));

        String result = service.loadByQuery("user1", "咖啡", 0);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空查询：返回空字符串不查询数据库")
    void loadByQuery_blankQuery_returnsEmpty() {
        String result = service.loadByQuery("user1", "", 1000);

        assertThat(result).isEmpty();
        verify(userLongTermMemoryRepository, never()).findMemoriesForKeywordMatch(any());
    }

    @Test
    @DisplayName("空用户：返回空字符串不查询数据库")
    void loadByQuery_blankUser_returnsEmpty() {
        String result = service.loadByQuery("", "query", 1000);

        assertThat(result).isEmpty();
        verify(userLongTermMemoryRepository, never()).findMemoriesForKeywordMatch(any());
    }

    @Test
    @DisplayName("无记忆：返回空字符串")
    void loadByQuery_noMemories_returnsEmpty() {
        when(userLongTermMemoryRepository.findMemoriesForKeywordMatch("user1")).thenReturn(List.of());

        String result = service.loadByQuery("user1", "query", 1000);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("英文关键词匹配：按空白切分后匹配")
    void loadByQuery_englishKeywords_matched() {
        when(userLongTermMemoryRepository.findMemoriesForKeywordMatch("user1")).thenReturn(List.of(
                buildMemory(1L, "user likes coffee and tea")
        ));

        String result = service.loadByQuery("user1", "coffee", 1000);

        assertThat(result).contains("coffee");
    }

    private UserLongTermMemoryInfo buildMemory(Long id, String content) {
        UserLongTermMemoryInfo memory = new UserLongTermMemoryInfo();
        memory.setId(id);
        memory.setUserId("user1");
        memory.setMemoryType("SUMMARY");
        memory.setContent(content);
        memory.setLastAccessedAt(LocalDateTime.now());
        return memory;
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
