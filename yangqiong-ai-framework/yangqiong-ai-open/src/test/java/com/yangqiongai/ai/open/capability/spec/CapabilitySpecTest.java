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
package com.yangqiongai.ai.open.capability.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力规格
 * @author yangqiong
 */
class CapabilitySpecTest {

    @Test
    void testGettersAndSetters() {
        CapabilitySpec spec = new CapabilitySpec();

        spec.setCode("project-overview");
        spec.setName("项目总览");
        spec.setDescription("生成项目概览报告");
        spec.setVersion("1.0.0");
        spec.setCategory("report");
        spec.setAgentCode("agent-001");
        spec.setInputSchema("input.schema.json");
        spec.setOutputSchema("output.schema.json");
        spec.setPromptTemplate("prompt.tpl");
        spec.setInputSchemaContent("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        spec.setOutputSchemaContent("{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\"}}}");
        spec.setPromptTemplateContent("请生成关于${name}的报告");

        AgentOverrides overrides = new AgentOverrides();
        spec.setAgentOverrides(overrides);

        ExecutionConfig execution = new ExecutionConfig();
        spec.setExecution(execution);

        OutputContractConfig contract = new OutputContractConfig();
        spec.setContract(contract);

        DataContextConfig context = new DataContextConfig();
        spec.setContext(context);

        AuditConfig audit = new AuditConfig();
        spec.setAudit(audit);

        assertThat(spec.getCode()).isEqualTo("project-overview");
        assertThat(spec.getName()).isEqualTo("项目总览");
        assertThat(spec.getDescription()).isEqualTo("生成项目概览报告");
        assertThat(spec.getVersion()).isEqualTo("1.0.0");
        assertThat(spec.getCategory()).isEqualTo("report");
        assertThat(spec.getAgentCode()).isEqualTo("agent-001");
        assertThat(spec.getInputSchema()).isEqualTo("input.schema.json");
        assertThat(spec.getOutputSchema()).isEqualTo("output.schema.json");
        assertThat(spec.getPromptTemplate()).isEqualTo("prompt.tpl");
        assertThat(spec.getInputSchemaContent()).isEqualTo("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        assertThat(spec.getOutputSchemaContent()).isEqualTo("{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\"}}}");
        assertThat(spec.getPromptTemplateContent()).isEqualTo("请生成关于${name}的报告");
        assertThat(spec.getAgentOverrides()).isSameAs(overrides);
        assertThat(spec.getExecution()).isSameAs(execution);
        assertThat(spec.getContract()).isSameAs(contract);
        assertThat(spec.getContext()).isSameAs(context);
        assertThat(spec.getAudit()).isSameAs(audit);
    }

    @Test
    void testDefaultValues() {
        CapabilitySpec spec = new CapabilitySpec();

        assertThat(spec.getCode()).isNull();
        assertThat(spec.getName()).isNull();
        assertThat(spec.getDescription()).isNull();
        assertThat(spec.getVersion()).isNull();
        assertThat(spec.getCategory()).isNull();
        assertThat(spec.getAgentCode()).isNull();
        assertThat(spec.getInputSchema()).isNull();
        assertThat(spec.getOutputSchema()).isNull();
        assertThat(spec.getPromptTemplate()).isNull();
        assertThat(spec.getInputSchemaContent()).isNull();
        assertThat(spec.getOutputSchemaContent()).isNull();
        assertThat(spec.getPromptTemplateContent()).isNull();
        assertThat(spec.getAgentOverrides()).isNull();
        assertThat(spec.getExecution()).isNull();
        assertThat(spec.getContract()).isNull();
        assertThat(spec.getContext()).isNull();
        assertThat(spec.getAudit()).isNull();
    }

    @Test
    void testSetNullValues() {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("test");
        spec.setName("test");
        spec.setDescription("test");
        spec.setVersion("test");
        spec.setCategory("test");
        spec.setAgentCode("test");
        spec.setInputSchema("test");
        spec.setOutputSchema("test");
        spec.setPromptTemplate("test");
        spec.setInputSchemaContent("test");
        spec.setOutputSchemaContent("test");
        spec.setPromptTemplateContent("test");

        spec.setCode(null);
        spec.setName(null);
        spec.setDescription(null);
        spec.setVersion(null);
        spec.setCategory(null);
        spec.setAgentCode(null);
        spec.setInputSchema(null);
        spec.setOutputSchema(null);
        spec.setPromptTemplate(null);
        spec.setInputSchemaContent(null);
        spec.setOutputSchemaContent(null);
        spec.setPromptTemplateContent(null);

        assertThat(spec.getCode()).isNull();
        assertThat(spec.getName()).isNull();
        assertThat(spec.getDescription()).isNull();
        assertThat(spec.getVersion()).isNull();
        assertThat(spec.getCategory()).isNull();
        assertThat(spec.getAgentCode()).isNull();
        assertThat(spec.getInputSchema()).isNull();
        assertThat(spec.getOutputSchema()).isNull();
        assertThat(spec.getPromptTemplate()).isNull();
    }
}