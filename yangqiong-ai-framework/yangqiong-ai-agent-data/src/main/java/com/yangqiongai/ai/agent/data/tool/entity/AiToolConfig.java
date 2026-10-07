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
package com.yangqiongai.ai.agent.data.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * 宸ュ叿閰嶇疆
 *
 * @author yangqiong
 */
@TableName("ai_tool_config")
public class AiToolConfig extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 宸ュ叿缂栫爜(瀵瑰簲Tool绫籗impleName)
     */
    private String toolCode;

    /**
     * 宸ュ叿鍚嶇О
     */
    private String toolName;

    /**
     * 宸ュ叿鎻忚堪(瀵瑰簲@AgentTool.value)
     */
    private String toolDesc;

    /**
     * 宸ュ叿瀹炵幇绫诲叏鍚?鑷姩鍚屾濉厖)
     */
    private String toolClass;

    /**
     * 宸ュ叿绫诲瀷(TOOL/MCP/WIKI/RAG)
     */
    private String toolType;

    /**
     * 宸ュ叿鍒嗙被(SEARCH/EXEC/REVIEW/RAG/WIKI 绛?
     */
    private String toolCategory;

    /**
     * 鎺掑簭鍙?
     */
    private Integer toolOrder;

    /**
     * 宸ュ叿閰嶇疆(JSON)
     */
    private String toolConfig;

    /**
     * 宸ュ叿鐘舵€?0-绂佺敤 1-鍚敤)
     */
    private Integer toolStatus;

    /**
     * 澶囨敞
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

