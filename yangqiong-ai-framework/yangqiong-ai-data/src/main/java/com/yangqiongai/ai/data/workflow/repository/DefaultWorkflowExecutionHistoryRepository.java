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
package com.yangqiongai.ai.data.workflow.repository;

import com.yangqiongai.ai.data.workflow.entity.WorkflowExecutionHistoryEntity;
import com.yangqiongai.ai.data.workflow.mapper.WorkflowExecutionHistoryMapper;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;

/**
 * 工作流执行历史
 * @author yangqiong
 */
public class DefaultWorkflowExecutionHistoryRepository implements WorkflowExecutionHistoryRepository {

    @Autowired
    private WorkflowExecutionHistoryMapper workflowExecutionHistoryMapper;

    @Override
    public void recordCompletion(WorkflowState state, String outputSummary, String errorMessage) {
        WorkflowExecutionHistoryEntity entity = new WorkflowExecutionHistoryEntity();
        entity.setInstanceId(state.getInstanceId());
        entity.setDefinitionName(state.getDefinitionName());
        entity.setDefinitionVersion(state.getDefinitionVersion());
        entity.setStatus(state.getStatus().name());
        entity.setUserId(state.getVariable("userId") != null ? state.getVariable("userId").toString() : null);

        Object input = state.getVariable("input");
        entity.setInputSummary(input != null ? truncate(input.toString(), 500) : null);
        entity.setOutputSummary(outputSummary != null ? truncate(outputSummary, 500) : null);
        entity.setErrorMessage(errorMessage != null ? truncate(errorMessage, 1000) : null);

        if (state.getCreateTime() != null) {
            entity.setStartTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(state.getCreateTime()), java.time.ZoneId.systemDefault()));
        }
        entity.setEndTime(LocalDateTime.now());

        if (state.getCreateTime() != null) {
            entity.setDurationMs(System.currentTimeMillis() - state.getCreateTime());
        }

        workflowExecutionHistoryMapper.insert(entity);
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
