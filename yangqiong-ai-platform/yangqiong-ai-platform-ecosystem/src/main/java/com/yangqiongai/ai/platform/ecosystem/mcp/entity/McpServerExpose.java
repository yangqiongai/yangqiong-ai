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
package com.yangqiongai.ai.platform.ecosystem.mcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * MCP服务暴露白名单
 * @author yangqiong
 */
@TableName("ai_mcp_server_expose")
public class McpServerExpose extends ScopeEntity {

    /**
     * 暴露类型: TOOL平台工具/AGENT代理长任务/PROMPT能力提示词/RESOURCE知识资源
     */
    public static final String TYPE_TOOL = "TOOL";

    public static final String TYPE_AGENT = "AGENT";

    public static final String TYPE_PROMPT = "PROMPT";

    public static final String TYPE_RESOURCE = "RESOURCE";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 暴露类型(TOOL/AGENT/PROMPT/RESOURCE)
     */
    private String exposeType;

    /**
     * 暴露编码(工具编码/代理编码/能力编码/资源来源键)
     */
    private String exposeCode;

    /**
     * 对外显示名称
     */
    private String displayName;

    /**
     * 对外描述
     */
    private String description;

    /**
     * 是否允许MCP Apps渲染(0否/1是,MCP Apps SEP-1865预留)
     */
    private Integer renderAllowed;

    /**
     * 是否启用(0否/1是)
     */
    private Integer enabled;

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

    public String getExposeType() {
        return exposeType;
    }

    public void setExposeType(String exposeType) {
        this.exposeType = exposeType;
    }

    public String getExposeCode() {
        return exposeCode;
    }

    public void setExposeCode(String exposeCode) {
        this.exposeCode = exposeCode;
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

    public Integer getRenderAllowed() {
        return renderAllowed;
    }

    public void setRenderAllowed(Integer renderAllowed) {
        this.renderAllowed = renderAllowed;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
