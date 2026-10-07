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
package com.yangqiongai.ai.workflow.store;

import com.yangqiongai.ai.workflow.model.WorkflowState;

import java.util.List;

/**
 * 工作流状态存储
 * @author yangqiong
 */
public interface WorkflowStateStore {

    /**
     * 保存工作流状态
     * @param state
     */
    void save(WorkflowState state);

    /**
     * 加载工作流状态
     * @param instanceId
     * @return
     */
    WorkflowState load(String instanceId);

    /**
     * 删除工作流状态
     * @param instanceId
     */
    void delete(String instanceId);

    /**
     * 根据定义名称查询工作流状态列表
     * @param definitionName
     * @return
     */
    List<WorkflowState> queryByDefinitionName(String definitionName);

    /**
     * 根据审批请求ID查找暂停的工作流状态
     * @param pendingRequestId
     * @return
     */
    WorkflowState findByPendingRequestId(String pendingRequestId);

    /**
     * 查询全部运行中的工作流状态（僵尸实例回收扫描用）
     * @return
     */
    List<WorkflowState> listRunning();

    /**
     * 查询全部暂停中的工作流状态（服务重启后时间控制恢复扫描用）
     * @return
     */
    List<WorkflowState> listPaused();
}
