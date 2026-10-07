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
package com.yangqiongai.ai.trust.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDate;

/**
 * Agent动作审计链锚点
 * @author yangqiong
 */
@TableName("ai_agent_action_anchor")
public class AgentActionAnchor extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 链日期
     */
    private LocalDate chainDate;

    /**
     * 当日链头哈希
     */
    private String chainHeadHash;

    /**
     * 当日日志条数
     */
    private Integer logCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getChainDate() {
        return chainDate;
    }

    public void setChainDate(LocalDate chainDate) {
        this.chainDate = chainDate;
    }

    public String getChainHeadHash() {
        return chainHeadHash;
    }

    public void setChainHeadHash(String chainHeadHash) {
        this.chainHeadHash = chainHeadHash;
    }

    public Integer getLogCount() {
        return logCount;
    }

    public void setLogCount(Integer logCount) {
        this.logCount = logCount;
    }
}
