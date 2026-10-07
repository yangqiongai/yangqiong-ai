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
package com.yangqiongai.ai.open.capability.catalog;

import java.time.Instant;

/**
 * 能力定义实体
 * <p>
 * 对应数据库 ai_open_capability 表，支持三种操作：
 * 1. 新增能力：definitionJson 为完整 CapabilitySpec JSON，code 与 YAML 定义不重复
 * 2. 覆盖能力：definitionJson 为完整 CapabilitySpec JSON，code 与 YAML 定义的 code 相同，覆盖其全部配置
 * 3. 禁用能力：enabled=false，该能力不可用
 * 同时存储对应能力目录下的输入/输出Schema和Prompt模板内容。
 * </p>
 * @author yangqiong
 */
public class CapabilityDefinition {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 能力编码（唯一标识）
     */
    private String code;

    /**
     * 能力名称（用于界面展示）
     */
    private String name;

    /**
     * 备注说明（用于界面展示和维护说明）
     */
    private String description;

    /**
     * 能力定义JSON（完整CapabilitySpec JSON）
     */
    private String definitionJson;

    /**
     * 输入Schema内容（JSON格式，对应能力目录下的input.schema.json）
     */
    private String inputSchema;

    /**
     * 输出Schema内容（JSON格式，对应能力目录下的output.schema.json）
     */
    private String outputSchema;

    /**
     * Prompt模板内容（对应能力目录下的prompt.tpl）
     */
    private String promptTemplate;

    /**
     * 是否启用（false时禁用该能力）
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getDefinitionJson() {
        return definitionJson;
    }

    public void setDefinitionJson(String definitionJson) {
        this.definitionJson = definitionJson;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}