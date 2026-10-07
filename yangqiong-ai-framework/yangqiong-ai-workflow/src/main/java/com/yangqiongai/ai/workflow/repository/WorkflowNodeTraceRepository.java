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
package com.yangqiongai.ai.workflow.repository;

import com.yangqiongai.ai.workflow.model.WorkflowNodeTrace;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流节点执行轨迹
 * @author yangqiong
 */
public interface WorkflowNodeTraceRepository {

    /**
     * 记录节点执行轨迹（同实例同节点覆盖写）
     * @param trace
     */
    void record(WorkflowNodeTrace trace);

    /**
     * 按实例查询节点轨迹，按执行顺序排序
     * @param instanceId
     * @return
     */
    List<WorkflowNodeTrace> listByInstance(String instanceId);

    /**
     * 删除指定时间之前的节点轨迹（保留期清理用，默认不支持返回0）
     * @param beforeTime
     * @return
     */
    default int deleteExpiredBefore(LocalDateTime beforeTime) {
        return 0;
    }
}
