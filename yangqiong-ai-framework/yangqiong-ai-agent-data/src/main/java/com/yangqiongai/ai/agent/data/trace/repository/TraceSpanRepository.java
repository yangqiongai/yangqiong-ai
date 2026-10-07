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

import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent运行Span存储
 * @author yangqiong
 */
public interface TraceSpanRepository {

    /**
     * 批量保存Span(单条失败仅记录日志，不中断)
     * @param spans
     */
    void batchSave(List<TraceSpanEntity> spans);

    /**
     * 按追踪ID查询全部Span(按开始时间排序)
     * @param traceId
     * @return
     */
    List<TraceSpanEntity> findByTraceId(String traceId);

    /**
     * 分页查询根Span(agent_run)，按开始时间倒序
     * @param agentCode Agent编码(可空)
     * @param taskId 任务ID(可空)
     * @param status 状态(可空)
     * @param startTime 开始时间下界(可空)
     * @param endTime 开始时间上界(可空)
     * @param offset
     * @param limit
     * @return
     */
    List<TraceSpanEntity> findRootSpans(String agentCode, String taskId, String status,
                                        LocalDateTime startTime, LocalDateTime endTime,
                                        int offset, int limit);

    /**
     * 统计根Span数量(过滤条件同findRootSpans)
     * @param agentCode
     * @param taskId
     * @param status
     * @param startTime
     * @param endTime
     * @return
     */
    long countRootSpans(String agentCode, String taskId, String status,
                        LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按追踪ID列表统计各trace的Span数
     * @param traceIds
     * @return traceId → Span数量
     */
    Map<String, Long> countByTraceIds(List<String> traceIds);

    /**
     * 按追踪ID删除全部Span
     * @param traceId
     * @return
     */
    int deleteByTraceId(String traceId);

    /**
     * 删除指定时间前创建的Span(分批，用于保留期清理)
     * @param threshold
     * @param limit
     * @return
     */
    int deleteCreatedBefore(LocalDateTime threshold, int limit);

    /**
     * 按scope删除指定时间前创建的Span(分批，供scope级保留期覆盖清理)
     * @param scopeId
     * @param threshold
     * @param limit
     * @return
     */
    int deleteCreatedBeforeByScope(String scopeId, LocalDateTime threshold, int limit);

    /**
     * 删除指定时间前创建的Span并排除覆盖scope(分批，覆盖scope按各自保留期单独清理)
     * @param threshold
     * @param limit
     * @param excludeScopeIds
     * @return
     */
    int deleteCreatedBeforeExcludingScopes(LocalDateTime threshold, int limit, java.util.Collection<String> excludeScopeIds);
}
