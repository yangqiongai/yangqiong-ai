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

import com.yangqiongai.ai.workflow.model.WorkflowPauseHistory;

import java.util.List;

/**
 * 工作流暂停恢复流水存储
 * @author yangqiong
 */
public interface WorkflowPauseHistoryStore {

    /**
     * 记录暂停/恢复流水
     * @param history
     */
    void record(WorkflowPauseHistory history);

    /**
     * 按实例ID查询暂停恢复流水（按操作时间正序）
     * @param instanceId
     * @return
     */
    List<WorkflowPauseHistory> listByInstanceId(String instanceId);
}
