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
package com.yangqiongai.ai.agent.data.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent运行Span Mapper
 * @author yangqiong
 */
@Mapper
public interface TraceSpanMapper extends BaseMapper<TraceSpanEntity> {

    /**
     * 删除指定时间前创建的Span(跨scope清理，供保留期调度使用)
     * @param threshold 时间阈值
     * @param limit 单批上限
     * @return 删除条数
     */
    @Delete("DELETE FROM ai_agent_trace_span WHERE create_time < #{threshold} LIMIT #{limit}")
    @InterceptorIgnore(tenantLine = "true")
    int deleteCreatedBefore(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);

    /**
     * 按scope删除指定时间前创建的Span(供scope级保留期覆盖清理使用)
     * @param scopeId 作用域ID
     * @param threshold 时间阈值
     * @param limit 单批上限
     * @return 删除条数
     */
    @Delete("DELETE FROM ai_agent_trace_span WHERE scope_id = #{scopeId} "
            + "AND create_time < #{threshold} LIMIT #{limit}")
    @InterceptorIgnore(tenantLine = "true")
    int deleteCreatedBeforeByScope(@Param("scopeId") String scopeId,
                                   @Param("threshold") LocalDateTime threshold,
                                   @Param("limit") int limit);

    /**
     * 删除指定时间前创建的Span并排除覆盖scope(跨scope清理，保留覆盖scope按各自保留期单独清理)
     * @param threshold 时间阈值
     * @param limit 单批上限
     * @param excludeScopeIds 排除的作用域ID列表
     * @return 删除条数
     */
    @Delete("<script>DELETE FROM ai_agent_trace_span WHERE create_time &lt; #{threshold} "
            + "AND (scope_id IS NULL OR scope_id NOT IN "
            + "<foreach collection='excludeScopeIds' item='item' open='(' separator=',' close=')'>#{item}</foreach>) "
            + "LIMIT #{limit}</script>")
    @InterceptorIgnore(tenantLine = "true")
    int deleteCreatedBeforeExcludingScopes(@Param("threshold") LocalDateTime threshold,
                                           @Param("limit") int limit,
                                           @Param("excludeScopeIds") java.util.Collection<String> excludeScopeIds);

    /**
     * 查询统计日内的错误根Span(跨scope，供失败模式聚类聚合使用)
     * @param dayStart 统计日起始时间(含)
     * @param dayEnd 统计日结束时间(不含)
     * @return 每行含 scopeId/agentCode/traceId/taskId/errorMessage/createTime
     */
    @Select("SELECT scope_id AS scopeId, agent_code AS agentCode, trace_id AS traceId, task_id AS taskId, "
            + "error_message AS errorMessage, create_time AS createTime "
            + "FROM ai_agent_trace_span "
            + "WHERE operation = 'agent_run' AND status = 'ERROR' "
            + "AND create_time >= #{dayStart} AND create_time < #{dayEnd}")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectErrorRootSpans(@Param("dayStart") LocalDateTime dayStart,
                                                   @Param("dayEnd") LocalDateTime dayEnd);
}
