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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 无持久化的工作流暂停恢复流水存储
 * @author yangqiong
 */
public class NoopWorkflowPauseHistoryStore implements WorkflowPauseHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(NoopWorkflowPauseHistoryStore.class);

    /**
     * 记录暂停/恢复流水（无持久化，仅日志留痕）
     * @param history
     */
    @Override
    public void record(WorkflowPauseHistory history) {
        if (history != null) {
            log.info("暂停恢复流水(未持久化): instanceId={}, action={}, operator={}, reason={}",
                    history.getInstanceId(), history.getAction(), history.getOperator(), history.getReason());
        }
    }

    /**
     * 按实例ID查询暂停恢复流水
     * @param instanceId
     * @return
     */
    @Override
    public List<WorkflowPauseHistory> listByInstanceId(String instanceId) {
        return List.of();
    }
}
