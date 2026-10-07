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

import java.util.List;

/**
 * 能力规格
 * <p>
 * 描述一个AI生成型能力的完整元数据，包括入参/输出Schema、Prompt模板、执行策略、契约校验等。
 * 通过YAML文件加载，支持数据库覆盖。
 * </p>
 * @author yangqiong
 */
public class CapabilitySpec {

    /**
     * 能力编码（唯一标识，如 project-overview）
     */
    private String code;

    /**
     * 能力名称
     */
    private String name;

    /**
     * 能力描述
     */
    private String description;

    /**
     * 版本号
     */
    private String version;

    /**
     * 分类（如 budget-review / contract-audit）
     */
    private String category;

    /**
     * 调用的Agent编码（路由到现有AgentProcessor）
     */
    private String agentCode;

    /**
     * 执行体类型（AGENT=执行Agent，WORKFLOW=执行工作流，缺省AGENT）
     */
    private String execType;

    /**
     * 工作流定义名称（execType=WORKFLOW时生效）
     */
    private String workflowCode;

    /**
     * Agent参数覆盖配置（可选，未配置则使用ai_agent表默认配置）
     */
    private AgentOverrides agentOverrides;

    /**
     * 入参JSON Schema文件名（相对于能力目录，默认 input.schema.json）
     */
    private String inputSchema;

    /**
     * 输出JSON Schema文件名（相对于能力目录，默认 output.schema.json）
     */
    private String outputSchema;

    /**
     * Prompt模板文件名（相对于能力目录，默认 prompt.tpl）
     */
    private String promptTemplate;

    /**
     * 输入Schema内容（JSON格式，DB_CREATED时直接存储，无需加载classpath文件）
     */
    private String inputSchemaContent;

    /**
     * 输出Schema内容（JSON格式，DB_CREATED时直接存储，无需加载classpath文件）
     */
    private String outputSchemaContent;

    /**
     * Prompt模板内容（DB_CREATED时直接存储，无需加载classpath文件）
     */
    private String promptTemplateContent;

    /**
     * 输入Schema补充描述（JSON，字段联动与检查等控制信息，随提示词提供给大模型）
     */
    private String inputSchemaDescription;

    /**
     * 输出Schema补充描述（JSON，字段联动与检查等控制信息，随提示词提供给大模型）
     */
    private String outputSchemaDescription;

    /**
     * 执行配置
     */
    private ExecutionConfig execution;

    /**
     * 输出契约配置
     */
    private OutputContractConfig contract;

    /**
     * 数据上下文配置
     */
    private DataContextConfig context;

    /**
     * 审计配置
     */
    private AuditConfig audit;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getExecType() {
        return execType;
    }

    public void setExecType(String execType) {
        this.execType = execType;
    }

    public String getWorkflowCode() {
        return workflowCode;
    }

    public void setWorkflowCode(String workflowCode) {
        this.workflowCode = workflowCode;
    }

    public AgentOverrides getAgentOverrides() {
        return agentOverrides;
    }

    public void setAgentOverrides(AgentOverrides agentOverrides) {
        this.agentOverrides = agentOverrides;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) {
        this.outputSchema = outputSchema;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getInputSchemaContent() {
        return inputSchemaContent;
    }

    public void setInputSchemaContent(String inputSchemaContent) {
        this.inputSchemaContent = inputSchemaContent;
    }

    public String getOutputSchemaContent() {
        return outputSchemaContent;
    }

    public void setOutputSchemaContent(String outputSchemaContent) {
        this.outputSchemaContent = outputSchemaContent;
    }

    public String getPromptTemplateContent() {
        return promptTemplateContent;
    }

    public void setPromptTemplateContent(String promptTemplateContent) {
        this.promptTemplateContent = promptTemplateContent;
    }

    public String getInputSchemaDescription() {
        return inputSchemaDescription;
    }

    public void setInputSchemaDescription(String inputSchemaDescription) {
        this.inputSchemaDescription = inputSchemaDescription;
    }

    public String getOutputSchemaDescription() {
        return outputSchemaDescription;
    }

    public void setOutputSchemaDescription(String outputSchemaDescription) {
        this.outputSchemaDescription = outputSchemaDescription;
    }

    public ExecutionConfig getExecution() {
        return execution;
    }

    public void setExecution(ExecutionConfig execution) {
        this.execution = execution;
    }

    public OutputContractConfig getContract() {
        return contract;
    }

    public void setContract(OutputContractConfig contract) {
        this.contract = contract;
    }

    public DataContextConfig getContext() {
        return context;
    }

    public void setContext(DataContextConfig context) {
        this.context = context;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }
}