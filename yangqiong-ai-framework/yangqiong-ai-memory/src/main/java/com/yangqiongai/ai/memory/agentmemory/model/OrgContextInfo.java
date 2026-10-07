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
package com.yangqiongai.ai.memory.agentmemory.model;

import java.time.LocalDateTime;

/**
 * 组织上下文记忆
 * @author yangqiong
 */
public class OrgContextInfo {

    /**
     * 类型：术语
     */
    public static final String TYPE_TERM = "TERM";

    /**
     * 类型：别名（映射到标准术语，用于工具参数归一）
     */
    public static final String TYPE_ALIAS = "ALIAS";

    /**
     * 类型：血缘（术语到上级术语的归属关系）
     */
    public static final String TYPE_LINEAGE = "LINEAGE";

    /**
     * 主键
     */
    private Long id;

    /**
     * 上下文类型(TERM/ALIAS/LINEAGE)
     */
    private String contextType;

    /**
     * 术语（TERM=术语名，ALIAS=别名，LINEAGE=子术语）
     */
    private String term;

    /**
     * 目标术语（TERM=标准名，ALIAS=映射的标准术语，LINEAGE=上级术语）
     */
    private String targetTerm;

    /**
     * 备注
     */
    private String remark;

    /**
     * 冲突标记（1=存在冲突，如同一别名映射到不同标准术语）
     */
    private Integer conflictFlag;

    /**
     * 启用状态（1=启用，0=停用）
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getTargetTerm() {
        return targetTerm;
    }

    public void setTargetTerm(String targetTerm) {
        this.targetTerm = targetTerm;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getConflictFlag() {
        return conflictFlag;
    }

    public void setConflictFlag(Integer conflictFlag) {
        this.conflictFlag = conflictFlag;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
