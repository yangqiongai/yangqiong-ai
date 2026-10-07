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
package com.yangqiongai.ai.workflow.model;

/**
 * 并行网关节点
 * @author yangqiong
 */
public class ParallelNode extends WorkflowNode {

    /**
     * 获取分支数量
     * @return
     */
    public Integer getBranchCount() {
        return getConfigInt("branchCount");
    }

    /**
     * 设置分支数量
     * @param branchCount
     */
    public void setBranchCount(Integer branchCount) {
        setConfigValue("branchCount", branchCount);
    }

    /**
     * 获取汇聚类型（ALL/ANY）
     * @return
     */
    public String getJoinType() {
        return getConfigString("joinType");
    }

    /**
     * 设置汇聚类型
     * @param joinType
     */
    public void setJoinType(String joinType) {
        setConfigValue("joinType", joinType);
    }
}
