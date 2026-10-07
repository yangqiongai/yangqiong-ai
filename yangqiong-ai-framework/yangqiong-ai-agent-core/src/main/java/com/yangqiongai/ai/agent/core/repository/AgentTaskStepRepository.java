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
package com.yangqiongai.ai.agent.core.repository;

import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;

import java.util.List;

/**
 * Agent任务步骤仓库
 * @author yangqiong
 */
public interface AgentTaskStepRepository {

    /**
     * 批量写入步骤
     * @param steps
     */
    void saveSteps(List<AgentTaskStepInfo> steps);

    /**
     * 查询任务步骤
     * @param taskId
     * @return
     */
    List<AgentTaskStepInfo> querySteps(String taskId);

    /**
     * 递归查询任务树的完整执行步骤
     * @param taskId
     * @return
     */
    List<AgentTaskStepInfo> queryStepsByTaskTree(String taskId);
}
