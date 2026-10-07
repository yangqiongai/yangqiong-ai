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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.data.workflow.entity.WorkflowNodeExecutionEntity;
import com.yangqiongai.ai.data.workflow.mapper.WorkflowNodeExecutionMapper;
import com.yangqiongai.ai.workflow.model.WorkflowNodeTrace;
import com.yangqiongai.ai.workflow.repository.WorkflowNodeTraceRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点执行轨迹
 * @author yangqiong
 */
public class DefaultWorkflowNodeTraceRepository implements WorkflowNodeTraceRepository {

    private static final int MAX_JSON_LENGTH = 4096;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private WorkflowNodeExecutionMapper workflowNodeExecutionMapper;

    DefaultWorkflowNodeTraceRepository(WorkflowNodeExecutionMapper workflowNodeExecutionMapper) {
        this.workflowNodeExecutionMapper = workflowNodeExecutionMapper;
    }

    public DefaultWorkflowNodeTraceRepository() {
    }

    @Override
    public void record(WorkflowNodeTrace trace) {
        WorkflowNodeExecutionEntity entity = selectByInstanceAndNode(trace.getInstanceId(), trace.getNodeId());
        boolean exists = entity != null;
        if (!exists) {
            entity = new WorkflowNodeExecutionEntity();
        }
        entity.setInstanceId(trace.getInstanceId());
        entity.setNodeId(trace.getNodeId());
        entity.setNodeName(truncateText(trace.getNodeName(), 128));
        entity.setNodeType(trace.getNodeType());
        entity.setExecutionOrder(trace.getExecutionOrder());
        entity.setStatus(trace.getStatus());
        entity.setInputData(truncateJson(trace.getInputData()));
        entity.setOutputData(truncateJson(trace.getOutputData()));
        entity.setErrorMessage(truncateText(trace.getErrorMessage(), 1024));
        entity.setRetryCount(trace.getRetryCount() != null ? trace.getRetryCount() : 0);
        entity.setIterationCount(trace.getIterationCount() != null ? trace.getIterationCount() : 0);
        entity.setBranchTaken(truncateText(trace.getBranchTaken(), 128));
        entity.setStartTime(toLocalDateTime(trace.getStartTime()));
        entity.setEndTime(toLocalDateTime(trace.getEndTime()));
        entity.setDurationMs(trace.getDurationMs());
        if (trace.getScopeId() != null && !trace.getScopeId().isBlank()) {
            entity.setScopeId(trace.getScopeId());
        }
        if (exists) {
            // 同实例同节点覆盖写，保证回放时一个节点一行
            workflowNodeExecutionMapper.updateById(entity);
        } else {
            workflowNodeExecutionMapper.insert(entity);
        }
    }

    @Override
    public List<WorkflowNodeTrace> listByInstance(String instanceId) {
        LambdaQueryWrapper<WorkflowNodeExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNodeExecutionEntity::getInstanceId, instanceId);
        wrapper.orderByAsc(WorkflowNodeExecutionEntity::getExecutionOrder);
        wrapper.orderByAsc(WorkflowNodeExecutionEntity::getId);
        return workflowNodeExecutionMapper.selectList(wrapper).stream().map(this::toTrace).toList();
    }

    @Override
    public int deleteExpiredBefore(LocalDateTime beforeTime) {
        LambdaQueryWrapper<WorkflowNodeExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(WorkflowNodeExecutionEntity::getStartTime, beforeTime);
        return workflowNodeExecutionMapper.delete(wrapper);
    }

    /**
     * 查询同实例同节点的已有轨迹记录
     * @param instanceId
     * @param nodeId
     * @return
     */
    private WorkflowNodeExecutionEntity selectByInstanceAndNode(String instanceId, String nodeId) {
        LambdaQueryWrapper<WorkflowNodeExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNodeExecutionEntity::getInstanceId, instanceId);
        wrapper.eq(WorkflowNodeExecutionEntity::getNodeId, nodeId);
        wrapper.last("LIMIT 1");
        return workflowNodeExecutionMapper.selectOne(wrapper);
    }

    /**
     * 实体转轨迹模型
     * @param entity
     * @return
     */
    private WorkflowNodeTrace toTrace(WorkflowNodeExecutionEntity entity) {
        WorkflowNodeTrace trace = new WorkflowNodeTrace();
        trace.setInstanceId(entity.getInstanceId());
        trace.setNodeId(entity.getNodeId());
        trace.setNodeName(entity.getNodeName());
        trace.setNodeType(entity.getNodeType());
        trace.setExecutionOrder(entity.getExecutionOrder());
        trace.setStatus(entity.getStatus());
        trace.setInputData(parseJson(entity.getInputData()));
        trace.setOutputData(parseJson(entity.getOutputData()));
        trace.setErrorMessage(entity.getErrorMessage());
        trace.setRetryCount(entity.getRetryCount());
        trace.setIterationCount(entity.getIterationCount());
        trace.setBranchTaken(entity.getBranchTaken());
        if (entity.getStartTime() != null) {
            trace.setStartTime(entity.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        if (entity.getEndTime() != null) {
            trace.setEndTime(entity.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        trace.setDurationMs(entity.getDurationMs());
        trace.setScopeId(entity.getScopeId());
        return trace;
    }

    /**
     * 序列化为JSON，超4KB截断并在内容头部标记
     * @param data
     * @return
     */
    private String truncateJson(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            if (json.length() <= MAX_JSON_LENGTH) {
                return json;
            }
            String truncated = "{\"__truncated\":true,\"data\":"
                    + OBJECT_MAPPER.writeValueAsString(json.substring(0, MAX_JSON_LENGTH)) + "}";
            return truncated;
        } catch (Exception e) {
            return "{\"__truncated\":true,\"error\":\"JSON序列化失败\"}";
        }
    }

    /**
     * 解析JSON字符串为Map，解析失败时降级为raw文本
     * @param json
     * @return
     */
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }

    private String truncateText(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }

    private LocalDateTime toLocalDateTime(Long epochMilli) {
        return epochMilli == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
