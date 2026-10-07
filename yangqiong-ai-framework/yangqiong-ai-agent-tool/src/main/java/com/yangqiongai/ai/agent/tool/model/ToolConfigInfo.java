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
package com.yangqiongai.ai.agent.tool.model;

/**
 * 工具配置
 * @author yangqiong
 */
public class ToolConfigInfo {

    /**
     * 主键
     */
    private Long id;

    /**
     * 工具编码(对应Tool类SimpleName)
     */
    private String toolCode;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具描述(对应@AgentTool.value)
     */
    private String toolDesc;

    /**
     * 工具实现类全名(自动同步填充)
     */
    private String toolClass;

    /**
     * 工具类型(TOOL/MCP/WIKI/RAG)
     */
    private String toolType;

    /**
     * 工具分类(SEARCH/EXEC/REVIEW/RAG/WIKI 等)
     */
    private String toolCategory;

    /**
     * 排序号
     */
    private Integer toolOrder;

    /**
     * 工具配置(JSON)
     */
    private String toolConfig;

    /**
     * 工具状态(0-禁用 1-启用)
     */
    private Integer toolStatus;

    /**
     * 备注
     */
    private String remark;

    /**
     * 所属分类编码（分类树节点code，空为未分类）
     */
    private String category;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolDesc() {
        return toolDesc;
    }

    public void setToolDesc(String toolDesc) {
        this.toolDesc = toolDesc;
    }

    public String getToolClass() {
        return toolClass;
    }

    public void setToolClass(String toolClass) {
        this.toolClass = toolClass;
    }

    public String getToolType() {
        return toolType;
    }

    public void setToolType(String toolType) {
        this.toolType = toolType;
    }

    public String getToolCategory() {
        return toolCategory;
    }

    public void setToolCategory(String toolCategory) {
        this.toolCategory = toolCategory;
    }

    public Integer getToolOrder() {
        return toolOrder;
    }

    public void setToolOrder(Integer toolOrder) {
        this.toolOrder = toolOrder;
    }

    public String getToolConfig() {
        return toolConfig;
    }

    public void setToolConfig(String toolConfig) {
        this.toolConfig = toolConfig;
    }

    public Integer getToolStatus() {
        return toolStatus;
    }

    public void setToolStatus(Integer toolStatus) {
        this.toolStatus = toolStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
