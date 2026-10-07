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
package com.yangqiongai.ai.agent.data.trace.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.yangqiongai.ai.agent.data.trace.mapper.TraceSpanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent运行Span存储
 * @author yangqiong
 */
public class DefaultTraceSpanRepository implements TraceSpanRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultTraceSpanRepository.class);

    @Autowired
    private TraceSpanMapper traceSpanMapper;

    @Override
    public void batchSave(List<TraceSpanEntity> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        for (TraceSpanEntity span : spans) {
            try {
                traceSpanMapper.insert(span);
            } catch (Exception e) {
                log.warn("Span落库失败: traceId={}, spanId={}", span.getTraceId(), span.getSpanId(), e);
            }
        }
    }

    @Override
    public List<TraceSpanEntity> findByTraceId(String traceId) {
        LambdaQueryWrapper<TraceSpanEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceSpanEntity::getTraceId, traceId)
                .orderByAsc(TraceSpanEntity::getStartTime);
        return traceSpanMapper.selectList(wrapper);
    }

    @Override
    public List<TraceSpanEntity> findRootSpans(String agentCode, String taskId, String status,
                                               LocalDateTime startTime, LocalDateTime endTime,
                                               int offset, int limit) {
        LambdaQueryWrapper<TraceSpanEntity> wrapper = rootSpanWrapper(agentCode, taskId, status, startTime, endTime);
        wrapper.orderByDesc(TraceSpanEntity::getStartTime)
                .last("LIMIT " + Math.max(1, limit) + " OFFSET " + Math.max(0, offset));
        return traceSpanMapper.selectList(wrapper);
    }

    @Override
    public long countRootSpans(String agentCode, String taskId, String status,
                               LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<TraceSpanEntity> wrapper = rootSpanWrapper(agentCode, taskId, status, startTime, endTime);
        return traceSpanMapper.selectCount(wrapper);
    }

    /**
     * 构建根Span查询条件(根Span=agent_run操作)
     * @param agentCode
     * @param taskId
     * @param status
     * @param startTime
     * @param endTime
     * @return
     */
    private LambdaQueryWrapper<TraceSpanEntity> rootSpanWrapper(String agentCode, String taskId, String status,
                                                                LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<TraceSpanEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceSpanEntity::getOperation, "agent_run");
        if (agentCode != null && !agentCode.isBlank()) {
            wrapper.eq(TraceSpanEntity::getAgentCode, agentCode);
        }
        if (taskId != null && !taskId.isBlank()) {
            wrapper.eq(TraceSpanEntity::getTaskId, taskId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(TraceSpanEntity::getStatus, status);
        }
        if (startTime != null) {
            wrapper.ge(TraceSpanEntity::getStartTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(TraceSpanEntity::getStartTime, endTime);
        }
        return wrapper;
    }

    @Override
    public Map<String, Long> countByTraceIds(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            return java.util.Map.of();
        }
        QueryWrapper<TraceSpanEntity> wrapper = new QueryWrapper<>();
        wrapper.select("trace_id", "COUNT(*) AS span_count")
                .in("trace_id", traceIds)
                .groupBy("trace_id");
        Map<String, Long> result = new java.util.HashMap<>();
        for (Map<String, Object> row : traceSpanMapper.selectMaps(wrapper)) {
            Object traceId = row.get("trace_id");
            Object count = row.get("span_count");
            if (traceId != null && count instanceof Number number) {
                result.put(String.valueOf(traceId), number.longValue());
            }
        }
        return result;
    }

    @Override
    public int deleteByTraceId(String traceId) {
        LambdaQueryWrapper<TraceSpanEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TraceSpanEntity::getTraceId, traceId);
        return traceSpanMapper.delete(wrapper);
    }

    @Override
    public int deleteCreatedBefore(LocalDateTime threshold, int limit) {
        // 走mapper专用DELETE(跨scope)，供后台保留期调度使用
        return traceSpanMapper.deleteCreatedBefore(threshold, Math.max(1, limit));
    }

    @Override
    public int deleteCreatedBeforeByScope(String scopeId, LocalDateTime threshold, int limit) {
        // 走mapper专用DELETE(指定scope)，供scope级保留期覆盖清理使用
        return traceSpanMapper.deleteCreatedBeforeByScope(scopeId, threshold, Math.max(1, limit));
    }

    @Override
    public int deleteCreatedBeforeExcludingScopes(LocalDateTime threshold, int limit,
                                                  java.util.Collection<String> excludeScopeIds) {
        // 走mapper专用DELETE(跨scope排除覆盖)，供保留期调度使用
        return traceSpanMapper.deleteCreatedBeforeExcludingScopes(threshold, Math.max(1, limit), excludeScopeIds);
    }
}
