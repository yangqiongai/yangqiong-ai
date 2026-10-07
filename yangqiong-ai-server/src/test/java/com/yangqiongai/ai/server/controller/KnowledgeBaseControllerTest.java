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
import com.yangqiongai.ai.platform.knowledge.api.KnowledgeBaseController;
import com.yangqiongai.ai.platform.knowledge.service.KnowledgeBaseService;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeBaseController 单元测试")
class KnowledgeBaseControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @InjectMocks
    private KnowledgeBaseController knowledgeBaseController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(knowledgeBaseController).build();
    }

    @Test
    @DisplayName("POST /api/knowledge-base → 创建知识库")
    void create_returnsCreatedKb() throws Exception {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbId("kb-1");
        kb.setKbName("测试知识库");

        when(knowledgeBaseService.create(any(KnowledgeBase.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/knowledge-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(kb)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kbId").value("kb-1"))
                .andExpect(jsonPath("$.data.kbName").value("测试知识库"));

        verify(knowledgeBaseService).create(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("GET /api/knowledge-base → 查询所有知识库")
    void list_returnsAllKbs() throws Exception {
        KnowledgeBase kb1 = new KnowledgeBase();
        kb1.setKbId("kb-1");
        kb1.setKbName("知识库1");
        KnowledgeBase kb2 = new KnowledgeBase();
        kb2.setKbId("kb-2");
        kb2.setKbName("知识库2");

        when(knowledgeBaseService.findAll()).thenReturn(List.of(kb1, kb2));

        mockMvc.perform(get("/api/knowledge-base"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].kbId").value("kb-1"))
                .andExpect(jsonPath("$.data[1].kbId").value("kb-2"));
    }

    @Test
    @DisplayName("GET /api/knowledge-base/{kbId} → 找到知识库")
    void getByKbId_found() throws Exception {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setKbId("kb-1");
        kb.setKbName("测试知识库");

        when(knowledgeBaseService.findByKbId("kb-1")).thenReturn(Optional.of(kb));

        mockMvc.perform(get("/api/knowledge-base/kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.kbId").value("kb-1"));
    }

    @Test
    @DisplayName("GET /api/knowledge-base/{kbId} → 未找到返回success=false")
    void getByKbId_notFound() throws Exception {
        when(knowledgeBaseService.findByKbId("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/knowledge-base/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(50004));
    }

    @Test
    @DisplayName("DELETE /api/knowledge-base/{kbId} → 删除知识库")
    void delete_deletesKb() throws Exception {
        doNothing().when(knowledgeBaseService).deleteByKbId("kb-1");

        mockMvc.perform(delete("/api/knowledge-base/kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(knowledgeBaseService).deleteByKbId("kb-1");
    }
}
