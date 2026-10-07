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
import com.yangqiongai.ai.platform.api.llm.ModelInfoController;
import com.yangqiongai.ai.platform.api.llm.ModelConnectivityTester;
import com.yangqiongai.ai.data.llm.entity.ModelInfo;
import com.yangqiongai.ai.data.llm.ModelInfoManager;
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
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelInfoController 单元测试")
class ModelInfoControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ModelInfoManager modelInfoManager;

    @Mock
    private ModelConnectivityTester modelConnectivityTester;

    @InjectMocks
    private ModelInfoController modelInfoController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(modelInfoController).build();
    }

    @Test
    @DisplayName("POST /api/model/info → 创建模型返回掩码密钥")
    void create_returnsMaskedModel() throws Exception {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setModelCode("gpt-4");
        modelInfo.setModelName("GPT-4");
        modelInfo.setApiKey("sk-cb76ae78aacc449198affe72df2069e3");

        when(modelInfoManager.create(any(ModelInfo.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/model/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modelInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.modelCode").value("gpt-4"))
                .andExpect(jsonPath("$.data.modelName").value("GPT-4"))
                .andExpect(jsonPath("$.data.apiKey").value("sk-cb76****69e3"))
                .andExpect(jsonPath("$.data.apiKey").value(org.hamcrest.Matchers.not("sk-cb76ae78aacc449198affe72df2069e3")));

        verify(modelInfoManager).create(any(ModelInfo.class));
    }

    @Test
    @DisplayName("GET /api/model/info → 查询所有模型且密钥脱敏")
    void list_masksApiKey() throws Exception {
        ModelInfo m1 = new ModelInfo();
        m1.setModelCode("gpt-4");
        m1.setModelName("GPT-4");
        m1.setApiKey("sk-abcdef1234567890abcdef1234567890");
        ModelInfo m2 = new ModelInfo();
        m2.setModelCode("claude-3");
        m2.setModelName("Claude 3");
        m2.setApiKey("sk-abcdef1234567890abcdef1234567891");

        when(modelInfoManager.findAll()).thenReturn(List.of(m1, m2));

        mockMvc.perform(get("/api/model/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].modelCode").value("gpt-4"))
                .andExpect(jsonPath("$.data[1].modelCode").value("claude-3"))
                .andExpect(jsonPath("$.data[0].apiKey").value("sk-abcd****7890"))
                .andExpect(jsonPath("$.data[0].apiKey").value(org.hamcrest.Matchers.not("sk-abcdef1234567890abcdef1234567890")))
                .andExpect(jsonPath("$.data[1].apiKey").value("sk-abcd****7891"));
    }

    @Test
    @DisplayName("GET /api/model/info/{modelCode} → 找到模型且密钥脱敏")
    void getByModelCode_masksApiKey() throws Exception {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setModelCode("gpt-4");
        modelInfo.setModelName("GPT-4");
        modelInfo.setApiKey("sk-abcdef1234567890abcdef1234567890");

        when(modelInfoManager.findByModelCode("gpt-4")).thenReturn(Optional.of(modelInfo));

        mockMvc.perform(get("/api/model/info/gpt-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.modelCode").value("gpt-4"))
                .andExpect(jsonPath("$.data.apiKey").value("sk-abcd****7890"));
    }

    @Test
    @DisplayName("GET /api/model/info/{modelCode} → 未找到返回success=false")
    void getByModelCode_notFound() throws Exception {
        when(modelInfoManager.findByModelCode("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/model/info/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(70001));
    }

    @Test
    @DisplayName("PUT /api/model/info → 掩码密钥更新时保留库中原密钥")
    void update_maskedKeyKeepsOriginal() throws Exception {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setModelCode("gpt-4");
        modelInfo.setModelName("GPT-4 Updated");
        modelInfo.setApiKey("sk-****7890");

        doNothing().when(modelInfoManager).update(any(ModelInfo.class));

        mockMvc.perform(put("/api/model/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modelInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(modelInfoManager).update(argThat(updated ->
                updated.getModelCode().equals("gpt-4")
                        && updated.getModelName().equals("GPT-4 Updated")
                        && updated.getApiKey() == null));
    }

    @Test
    @DisplayName("PUT /api/model/info → 新明文密钥正常更新")
    void update_plainKeyPassThrough() throws Exception {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setModelCode("gpt-4");
        modelInfo.setModelName("GPT-4 Updated");
        modelInfo.setApiKey("sk-newkey1234567890abcdef1234567890");

        doNothing().when(modelInfoManager).update(any(ModelInfo.class));

        mockMvc.perform(put("/api/model/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modelInfo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(modelInfoManager).update(argThat(updated ->
                updated.getModelCode().equals("gpt-4")
                        && updated.getApiKey().equals("sk-newkey1234567890abcdef1234567890")));
    }

    @Test
    @DisplayName("POST /api/model/info/test → 掩码密钥回退库中真实值再测试")
    void test_maskedKeyFallsBackToStoredKey() throws Exception {
        ModelInfo stored = new ModelInfo();
        stored.setModelCode("gpt-4");
        stored.setApiKey("sk-realkey1234567890abcdef1234567890");

        ModelInfo request = new ModelInfo();
        request.setModelCode("gpt-4");
        request.setApiKey("sk-****7890");

        when(modelInfoManager.findByModelCode("gpt-4")).thenReturn(Optional.of(stored));
        when(modelConnectivityTester.test(any(ModelInfo.class))).thenReturn(
                Map.of("success", true, "latencyMs", 120, "reply", "OK"));

        mockMvc.perform(post("/api/model/info/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        verify(modelConnectivityTester).test(argThat(testing ->
                testing.getApiKey().equals("sk-realkey1234567890abcdef1234567890")));
    }

    @Test
    @DisplayName("DELETE /api/model/info/{modelCode} → 删除模型")
    void delete_deletesModel() throws Exception {
        doNothing().when(modelInfoManager).delete("gpt-4");

        mockMvc.perform(delete("/api/model/info/gpt-4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(modelInfoManager).delete("gpt-4");
    }
}
