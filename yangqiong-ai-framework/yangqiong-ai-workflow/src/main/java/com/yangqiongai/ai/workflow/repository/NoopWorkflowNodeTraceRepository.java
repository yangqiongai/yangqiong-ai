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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流节点执行轨迹空实现（未接入数据模块时兜底，引擎零负担）
 * @author yangqiong
 */
@Service
public class NoopWorkflowNodeTraceRepository implements WorkflowNodeTraceRepository {

    @Override
    public void record(WorkflowNodeTrace trace) {
        // 空实现，不落库
    }

    @Override
    public List<WorkflowNodeTrace> listByInstance(String instanceId) {
        return List.of();
    }
}
