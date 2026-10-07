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
package com.yangqiongai.ai.data.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * 工作流定义
 * @author yangqiong
 */
@TableName("ai_workflow_definition")
public class WorkflowDefinitionEntity extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 工作流定义名称(唯一标识)
     */
    private String definitionName;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 工作流描述
     */
    private String description;

    /**
     * 分类
     */
    private String category;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 工作流定义JSON
     */
    private String definitionJson;

    /**
     * 状态(0-禁用 1-启用)
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public void setDefinitionName(String definitionName) {
        this.definitionName = definitionName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public void setDefinitionJson(String definitionJson) {
        this.definitionJson = definitionJson;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 从模型对象转换为实体
     */
    public static WorkflowDefinitionEntity fromModel(com.yangqiongai.ai.workflow.model.WorkflowDefinition model, ObjectMapper objectMapper) {
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setDefinitionName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setVersion(1);
        entity.setStatus(1);
        try {
            entity.setDefinitionJson(objectMapper.writeValueAsString(model));
        } catch (Exception e) {
            throw new RuntimeException("工作流定义序列化失败", e);
        }
        return entity;
    }

    /**
     * 转换为模型对象
     */
    public com.yangqiongai.ai.workflow.model.WorkflowDefinition toModel(ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(definitionJson, com.yangqiongai.ai.workflow.model.WorkflowDefinition.class);
        } catch (Exception e) {
            throw new RuntimeException("工作流定义反序列化失败: name=" + definitionName, e);
        }
    }
}
