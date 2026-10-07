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
package com.yangqiongai.ai.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.platform.conversation.api.ConversationController;
import com.yangqiongai.ai.platform.conversation.governance.SessionQuotaService;
import com.yangqiongai.ai.platform.conversation.service.SessionLifecycleService;
import com.yangqiongai.ai.memory.SessionManager;
import com.yangqiongai.ai.memory.memory.DialogMemoryAdapter;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationController 单元测试")
class ConversationControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SessionManager sessionManager;

    @Mock
    private DialogMemoryAdapter dialogMemoryAdapter;

    @Mock
    private SessionLifecycleService sessionLifecycleService;

    @Mock
    private SessionQuotaService sessionQuotaService;

    @InjectMocks
    private ConversationController conversationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(conversationController).build();
    }

    @Test
    @DisplayName("POST /api/conversation/session → 创建会话")
    void create_returnsCreatedSession() throws Exception {
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId("session-1");
        session.setUserId("user-1");

        when(sessionManager.create(any(ConversationSessionInfo.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/conversation/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"));

        verify(sessionManager).create(any(ConversationSessionInfo.class));
    }

    @Test
    @DisplayName("GET /api/conversation/session/{sessionId} → 找到会话")
    void getBySessionId_found() throws Exception {
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId("session-1");
        session.setUserId("user-1");

        when(sessionManager.findBySessionId("session-1")).thenReturn(java.util.Optional.of(session));

        mockMvc.perform(get("/api/conversation/session/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("session-1"));
    }

    @Test
    @DisplayName("GET /api/conversation/session/{sessionId} → 未找到返回success=false")
    void getBySessionId_notFound() throws Exception {
        when(sessionManager.findBySessionId("nonexistent")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/conversation/session/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(90001));
    }

    @Test
    @DisplayName("GET /api/conversation/session/user/{userId} → 查询用户会话列表")
    void listByUserId_returnsSessions() throws Exception {
        ConversationSessionInfo s1 = new ConversationSessionInfo();
        s1.setSessionId("session-1");
        s1.setUserId("user-1");
        ConversationSessionInfo s2 = new ConversationSessionInfo();
        s2.setSessionId("session-2");
        s2.setUserId("user-1");

        when(sessionManager.findByUserId("user-1")).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/conversation/session/user/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$.data[1].sessionId").value("session-2"));
    }

    @Test
    @DisplayName("PUT /api/conversation/session → 更新会话")
    void update_updatesSession() throws Exception {
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId("session-1");
        session.setSessionTitle("updated title");

        doNothing().when(sessionManager).update(any(ConversationSessionInfo.class));

        mockMvc.perform(put("/api/conversation/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(session)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionManager).update(any(ConversationSessionInfo.class));
    }

    @Test
    @DisplayName("DELETE /api/conversation/session/{sessionId} → 删除会话")
    void delete_deletesSession() throws Exception {
        doNothing().when(sessionManager).deleteBySessionId("session-1");

        mockMvc.perform(delete("/api/conversation/session/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionManager).deleteBySessionId("session-1");
    }
}
