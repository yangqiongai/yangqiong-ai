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
package com.yangqiongai.ai.agent.registry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.registry.service.AgentRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent注册中心接口单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRegistryController 单元测试")
class AgentRegistryControllerTest {

    @Mock
    private AgentRegistryService registryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentRegistryController controller = new AgentRegistryController();
        ReflectionTestUtils.setField(controller, "registryService", registryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * 构造定义
     * @return
     */
    private AgentDefinition definition() {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode("agent-a");
        definition.setAgentName("选矿顾问");
        definition.setStatus("DRAFT");
        return definition;
    }

    @Test
    @DisplayName("创建Agent定义成功返回data")
    void createDefinitionSuccess() throws Exception {
        when(registryService.createDefinition(any(AgentDefinition.class))).thenReturn(definition());

        mockMvc.perform(post("/api/agent/registry/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentCode\":\"agent-a\",\"agentName\":\"选矿顾问\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentCode").value("agent-a"));

        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(registryService).createDefinition(captor.capture());
        assertThat(captor.getValue().getAgentCode()).isEqualTo("agent-a");
    }

    @Test
    @DisplayName("查询不存在的定义返回HTTP 200且success=false")
    void getDefinitionNotFound() throws Exception {
        when(registryService.getDefinition("no-exist")).thenReturn(null);

        mockMvc.perform(get("/api/agent/registry/definitions/no-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("更新不存在的定义返回HTTP 200且success=false")
    void updateDefinitionNotFound() throws Exception {
        when(registryService.getDefinition("no-exist")).thenReturn(null);

        mockMvc.perform(put("/api/agent/registry/definitions/no-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentName\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("删除不存在的定义返回HTTP 200且success=false")
    void deleteDefinitionNotFound() throws Exception {
        when(registryService.getDefinition("no-exist")).thenReturn(null);

        mockMvc.perform(delete("/api/agent/registry/definitions/no-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    @DisplayName("分页查询定义透传分页参数")
    void listDefinitionsPassesPageParams() throws Exception {
        Page<AgentDefinition> page = new Page<>(2, 5);
        when(registryService.listDefinitions(2, 5, "ENABLED", "选矿")).thenReturn(page);

        mockMvc.perform(get("/api/agent/registry/definitions")
                        .param("pageNum", "2").param("pageSize", "5")
                        .param("status", "ENABLED").param("keyword", "选矿"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("查询版本列表返回数组")
    void listVersionsReturnsArray() throws Exception {
        AgentVersion version = new AgentVersion();
        version.setVersionNo("v1");
        when(registryService.listVersions("agent-a")).thenReturn(List.of(version));

        mockMvc.perform(get("/api/agent/registry/definitions/agent-a/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNo").value("v1"));
    }

    @Test
    @DisplayName("禁用Agent透传agentCode")
    void disablePassesAgentCode() throws Exception {
        mockMvc.perform(post("/api/agent/registry/definitions/agent-a/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"tom\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(registryService).updateStatus("agent-a", false);
    }
}
