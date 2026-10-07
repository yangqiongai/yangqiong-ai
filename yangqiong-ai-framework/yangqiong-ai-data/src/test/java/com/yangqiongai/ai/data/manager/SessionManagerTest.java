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

import com.yangqiongai.ai.memory.SessionManager;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("SessionManager 单元测试")
@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    private ConversationSessionRepository conversationSessionRepository;

    @InjectMocks
    private SessionManager sessionManager;

    private ConversationSessionInfo buildSession(String sessionId, String userId) {
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setId(1L);
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setSessionStatus(1);
        return session;
    }

    @Nested
    @DisplayName("create 测试")
    class CreateTest {

        @Test
        @DisplayName("创建会话：插入并返回")
        void shouldInsertAndReturn() {
            ConversationSessionInfo session = buildSession("sess-001", "user-001");

            ConversationSessionInfo result = sessionManager.create(session);

            assertThat(result).isSameAs(session);
            verify(conversationSessionRepository).create(session);
        }
    }

    @Nested
    @DisplayName("findBySessionId 测试")
    class FindBySessionIdTest {

        @Test
        @DisplayName("找到会话：返回Optional包含结果")
        void shouldReturnPresentWhenFound() {
            ConversationSessionInfo session = buildSession("sess-001", "user-001");
            when(conversationSessionRepository.findBySessionId("sess-001")).thenReturn(Optional.of(session));

            Optional<ConversationSessionInfo> result = sessionManager.findBySessionId("sess-001");

            assertThat(result).isPresent();
            assertThat(result.get().getSessionId()).isEqualTo("sess-001");
        }

        @Test
        @DisplayName("未找到会话：返回空Optional")
        void shouldReturnEmptyWhenNotFound() {
            when(conversationSessionRepository.findBySessionId("not-exist")).thenReturn(Optional.empty());

            Optional<ConversationSessionInfo> result = sessionManager.findBySessionId("not-exist");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserId 测试")
    class FindByUserIdTest {

        @Test
        @DisplayName("返回用户会话列表")
        void shouldReturnUserSessions() {
            ConversationSessionInfo s1 = buildSession("sess-001", "user-001");
            ConversationSessionInfo s2 = buildSession("sess-002", "user-001");
            when(conversationSessionRepository.findByUserId("user-001")).thenReturn(List.of(s2, s1));

            List<ConversationSessionInfo> result = sessionManager.findByUserId("user-001");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSessionId()).isEqualTo("sess-002");
        }
    }

    @Nested
    @DisplayName("update 测试")
    class UpdateTest {

        @Test
        @DisplayName("更新会话")
        void shouldUpdateSession() {
            ConversationSessionInfo session = buildSession("sess-001", "user-001");

            sessionManager.update(session);

            verify(conversationSessionRepository).update(session);
        }
    }

    @Nested
    @DisplayName("updateSummaryText 测试")
    class UpdateSummaryTextTest {

        @Test
        @DisplayName("更新会话摘要")
        void shouldUpdateSummaryText() {
            sessionManager.updateSummaryText("sess-001", "这是摘要内容");

            verify(conversationSessionRepository).updateSummaryText("sess-001", "这是摘要内容");
        }
    }

    @Nested
    @DisplayName("deleteBySessionId 测试")
    class DeleteBySessionIdTest {

        @Test
        @DisplayName("会话存在：删除成功")
        void shouldDeleteWhenExists() {
            ConversationSessionInfo session = buildSession("sess-001", "user-001");
            when(conversationSessionRepository.findBySessionId("sess-001")).thenReturn(Optional.of(session));

            sessionManager.deleteBySessionId("sess-001");

            verify(conversationSessionRepository).deleteById(1L);
        }

        @Test
        @DisplayName("会话不存在：静默处理，不抛异常")
        void shouldDoNothingWhenNotExists() {
            when(conversationSessionRepository.findBySessionId("not-exist")).thenReturn(Optional.empty());

            sessionManager.deleteBySessionId("not-exist");

            verify(conversationSessionRepository, never()).deleteById(any());
        }
    }
}
