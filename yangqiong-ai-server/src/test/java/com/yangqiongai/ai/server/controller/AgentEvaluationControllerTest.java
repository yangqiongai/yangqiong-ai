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
import com.yangqiongai.ai.platform.api.evaluation.AgentEvaluationController;
import com.yangqiongai.ai.evaluation.dataset.GoldenCase;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import com.yangqiongai.ai.evaluation.EvaluationRunner;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentEvaluationController 单元测试")
class AgentEvaluationControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private EvaluationRunner evaluationService;

    @InjectMocks
    private AgentEvaluationController agentEvaluationController;

    private EvaluationReport sampleReport;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agentEvaluationController).build();
        sampleReport = new EvaluationReport("ds-1", "test", 1, 1, 0, 1.0, true, List.of(), null, 100L);
    }

    @Test
    @DisplayName("POST /api/agent/evaluation/evaluate/file → 从文件评测")
    void evaluateFromFile_evaluates() throws Exception {
        when(evaluationService.evaluateFromFile("dataset/test.json")).thenReturn(sampleReport);

        mockMvc.perform(post("/api/agent/evaluation/evaluate/file")
                        .param("filePath", "dataset/test.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.datasetId").value("ds-1"))
                .andExpect(jsonPath("$.data.overallPassed").value(true));

        verify(evaluationService).evaluateFromFile("dataset/test.json");
    }

    @Test
    @DisplayName("POST /api/agent/evaluation/evaluate/file 路径遍历'..' → success=false")
    void evaluateFromFile_pathTraversal_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/evaluation/evaluate/file")
                        .param("filePath", "../etc/passwd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        verify(evaluationService, never()).evaluateFromFile(anyString());
    }

    @Test
    @DisplayName("POST /api/agent/evaluation/evaluate/file 绝对路径 → success=false")
    void evaluateFromFile_absolutePath_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/evaluation/evaluate/file")
                        .param("filePath", "/etc/passwd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        verify(evaluationService, never()).evaluateFromFile(anyString());
    }

    @Test
    @DisplayName("POST /api/agent/evaluation/evaluate/file Windows绝对路径 → success=false")
    void evaluateFromFile_windowsAbsolutePath_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/evaluation/evaluate/file")
                        .param("filePath", "C:\\Users\\test\\data.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        verify(evaluationService, never()).evaluateFromFile(anyString());
    }

    @Test
    @DisplayName("POST /api/agent/evaluation/evaluate/json → 从JSON评测")
    void evaluateFromJson_evaluates() throws Exception {
        String json = "{\"datasetId\":\"ds-1\",\"name\":\"test\",\"cases\":[]}";
        when(evaluationService.evaluateFromJson(anyString())).thenReturn(sampleReport);

        mockMvc.perform(post("/api/agent/evaluation/evaluate/json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.datasetId").value("ds-1"));

        verify(evaluationService).evaluateFromJson(anyString());
    }

    @Test
    @DisplayName("POST /api/agent/evaluation/evaluate → 直接评测")
    void evaluate_evaluates() throws Exception {
        GoldenDataset dataset = new GoldenDataset("ds-1", "test", "desc", List.of());
        when(evaluationService.evaluate(any(GoldenDataset.class))).thenReturn(sampleReport);

        mockMvc.perform(post("/api/agent/evaluation/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dataset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.datasetId").value("ds-1"));

        verify(evaluationService).evaluate(any(GoldenDataset.class));
    }
}
