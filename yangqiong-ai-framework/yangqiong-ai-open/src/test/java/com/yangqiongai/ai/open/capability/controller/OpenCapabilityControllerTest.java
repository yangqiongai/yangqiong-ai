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
package com.yangqiongai.ai.open.capability.controller;

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.context.DataContextHub;
import com.yangqiongai.ai.open.capability.engine.CapabilityEngine;
import com.yangqiongai.ai.open.capability.ingest.CapabilityDocumentIngestPort;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 开放能力API入口单测
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenCapabilityControllerTest {

    @Mock
    private CapabilityCatalog catalog;

    @Mock
    private DataContextHub dataContextHub;

    @Mock
    private CapabilityEngine engine;

    @Mock
    private ObjectProvider<CapabilityDocumentIngestPort> ingestPortProvider;

    private OpenCapabilityController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenCapabilityController(catalog, dataContextHub, engine, ingestPortProvider);
    }

    @Test
    @DisplayName("对外视图剥离Agent编码/覆盖配置/执行/契约/上下文/审计/提示词/Schema文件名等内部字段")
    void publicViewStripsInternalFields() {
        CapabilitySpec spec = baseSpec();
        spec.setAgentCode("default");
        spec.setAgentOverrides(new com.yangqiongai.ai.open.capability.spec.AgentOverrides());
        spec.setExecution(new com.yangqiongai.ai.open.capability.spec.ExecutionConfig());
        spec.setContract(new com.yangqiongai.ai.open.capability.spec.OutputContractConfig());
        spec.setContext(new com.yangqiongai.ai.open.capability.spec.DataContextConfig());
        spec.setAudit(new com.yangqiongai.ai.open.capability.spec.AuditConfig());
        spec.setInputSchema("classpath:capabilities/demo/input.schema.json");
        spec.setOutputSchema("classpath:capabilities/demo/output.schema.json");
        spec.setPromptTemplate("classpath:capabilities/demo/prompt.txt");
        when(catalog.get("demo")).thenReturn(spec);

        Map<String, Object> view = controller.getCapability("demo").getData();

        assertThat(view.keySet()).containsExactlyInAnyOrder(
                "code", "name", "description", "version", "category", "argumentHints", "endpoints");
    }

    @Test
    @DisplayName("Agent执行体返回同步/异步/查询/流式四个调用端点")
    void agentEndpointsContainAllModes() {
        CapabilitySpec spec = baseSpec();
        when(catalog.get("demo")).thenReturn(spec);

        List<Map<String, Object>> endpoints = endpointsOf(controller.getCapability("demo"));

        assertThat(endpoints).hasSize(4);
        assertThat(endpoints).extracting(e -> e.get("mode"))
                .containsExactly("sync", "async", "query", "stream");
        assertThat(endpoints.stream().filter(e -> "stream".equals(e.get("mode")))
                .findFirst().orElseThrow().get("path")).isEqualTo("/open/v1/run/stream");
    }

    @Test
    @DisplayName("工作流执行体为企业版能力，仅返回同步端点并提示")
    void workflowEndpointsSyncOnly() {
        CapabilitySpec spec = baseSpec();
        spec.setExecType("WORKFLOW");
        when(catalog.get("demo")).thenReturn(spec);

        List<Map<String, Object>> endpoints = endpointsOf(controller.getCapability("demo"));

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0).get("mode")).isEqualTo("sync");
        assertThat(endpoints.get(0).get("path")).isEqualTo("/open/v1/run");
        assertThat((String) endpoints.get(0).get("description")).contains("企业版");
    }

    /**
     * 从详情响应提取endpoints列表
     * @param result
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> endpointsOf(ApiResult<Map<String, Object>> result) {
        return (List<Map<String, Object>>) result.getData().get("endpoints");
    }

    @Test
    @DisplayName("argumentHints为Schema参数与模板占位符的合集，Schema参数在前")
    void argumentHintsMergesSchemaAndTemplate() {
        CapabilitySpec spec = baseSpec();
        spec.setInputSchemaContent("{\"properties\":{"
                + "\"question\":{\"type\":\"string\",\"description\":\"问题\"},"
                + "\"scene\":{\"type\":\"string\"}"
                + "},\"required\":[\"question\"]}");
        spec.setPromptTemplateContent("请回答${question}，场景${scene}，参考${dataContexts}，忽略${description}");
        when(catalog.get("demo")).thenReturn(spec);

        List<Map<String, Object>> hints = hintsOf(controller.getCapability("demo"));

        assertThat(hints).hasSize(2);
        assertThat(hints.get(0)).containsEntry("name", "question")
                .containsEntry("required", true)
                .containsEntry("description", "问题");
        assertThat(hints.get(1)).containsEntry("name", "scene")
                .containsEntry("required", false);
    }

    @Test
    @DisplayName("无Schema时argumentHints取模板占位符并排除引擎内置变量")
    void argumentHintsFallsBackToTemplateOnly() {
        CapabilitySpec spec = baseSpec();
        spec.setPromptTemplateContent("生成${title}的概况，上下文：${dataContexts}，描述：${description}");
        when(catalog.get("demo")).thenReturn(spec);

        List<Map<String, Object>> hints = hintsOf(controller.getCapability("demo"));

        assertThat(hints).hasSize(1);
        assertThat(hints.get(0)).containsEntry("name", "title")
                .containsEntry("required", false);
    }

    @Test
    @DisplayName("Schema解析异常时回退模板提取argumentHints")
    void argumentHintsSurvivesBrokenSchema() {
        CapabilitySpec spec = baseSpec();
        spec.setInputSchemaContent("{invalid-json");
        spec.setPromptTemplateContent("请回答${question}");
        when(catalog.get("demo")).thenReturn(spec);

        List<Map<String, Object>> hints = hintsOf(controller.getCapability("demo"));

        assertThat(hints).hasSize(1);
        assertThat(hints.get(0)).containsEntry("name", "question");
    }

    @Test
    @DisplayName("能力不存在返回失败")
    void detailFailsWhenCapabilityMissing() {
        when(catalog.get("ghost")).thenReturn(null);

        ApiResult<Map<String, Object>> result = controller.getCapability("ghost");

        assertThat(result.isSuccess()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> hintsOf(ApiResult<Map<String, Object>> result) {
        return (List<Map<String, Object>>) result.getData().get("argumentHints");
    }

    private CapabilitySpec baseSpec() {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("demo");
        spec.setName("演示能力");
        spec.setDescription("演示");
        spec.setVersion("1.0.0");
        spec.setCategory("test");
        return spec;
    }
}
