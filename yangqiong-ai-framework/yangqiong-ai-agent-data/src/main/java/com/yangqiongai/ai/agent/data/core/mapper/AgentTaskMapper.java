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
package com.yangqiongai.ai.agent.data.core.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.agent.data.core.entity.AgentTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent浠诲姟Mapper
 * @author yangqiong
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEntity> {

    /**
     * 按taskId查询任务（引擎内部按唯一ID查询，跨scope）
     * @param taskId 任务ID
     * @return 任务实体
     */
    @Select("SELECT * FROM ai_agent_task WHERE task_id = #{taskId} LIMIT 1")
    @InterceptorIgnore(tenantLine = "true")
    AgentTaskEntity selectByTaskId(@Param("taskId") String taskId);

    /**
     * 按taskId查询任务scope（更新链路绑定scope用，跨scope）
     * @param taskId 任务ID
     * @return scopeId
     */
    @Select("SELECT scope_id FROM ai_agent_task WHERE task_id = #{taskId} LIMIT 1")
    @InterceptorIgnore(tenantLine = "true")
    String selectScopeByTaskId(@Param("taskId") String taskId);

    /**
     * 閫掑綊鏌ヨ浠诲姟鍙婂叾鎵€鏈夊瓩浠诲姟锛屾寜 agent_path 鎺掑簭
     * @param taskId 鏍逛换鍔＄D
     * @return 浠诲姟鏍戝垪琛
     */
    @Select("<script>"
            + "WITH RECURSIVE task_tree AS ("
            + "  SELECT * FROM ai_agent_task WHERE task_id = #{taskId}"
            + "  UNION ALL"
            + "  SELECT t.* FROM ai_agent_task t JOIN task_tree tt ON t.parent_task_id = tt.task_id"
            + ")"
            + "SELECT * FROM task_tree ORDER BY agent_path"
            + "</script>")
    @InterceptorIgnore(tenantLine = "true")
    List<AgentTaskEntity> selectTaskTree(@Param("taskId") String taskId);

    /**
     * 鏌ヨ鏌愪换鍔＄殑鐩存帴瀛愪换锛堟寜鍒涘缓鏃堕棿鎺掑簭锛
     * @param parentTaskId 鐖朵换鍔＄D
     * @return 瀛愪换鍒楄〃
     */
    @Select("SELECT * FROM ai_agent_task WHERE parent_task_id = #{parentTaskId} ORDER BY create_time ASC")
    @InterceptorIgnore(tenantLine = "true")
    List<AgentTaskEntity> selectChildTasks(@Param("parentTaskId") String parentTaskId);

    /**
     * 鎸夌敤鎴稩D鍒嗛〉鏌ヨ浠诲姟鍘嗗彶锛堜粎鏍逛换鍔★紝鎺掗櫎瀛愪唬鐞嗕换鍔★級
     * @param userId 鐢ㄦ埛ID
     * @param offset 鍋忕Щ閲?     * @param limit 姣忛〉鏁伴噺
     * @return 浠诲姟鍒楄〃
     */
    @Select("SELECT * FROM ai_agent_task WHERE user_id = #{userId} AND parent_task_id IS NULL "
            + "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<AgentTaskEntity> selectTaskHistoryByUser(@Param("userId") String userId,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /**
     * 缁熻鐢ㄦ埛鐨勪换鍔℃€绘暟锛堜粎鏍逛换鍔★級
     * @param userId 鐢ㄦ埛ID
     * @return 浠诲姟鎬绘暟
     */
    @Select("SELECT COUNT(*) FROM ai_agent_task WHERE user_id = #{userId} AND parent_task_id IS NULL")
    long countTaskHistoryByUser(@Param("userId") String userId);

    /**
     * 鎸夌姸鎬佺粺璁′换鍔℃暟閲?     * @return 鍚勭姸鎬佷换鍔℃暟閲?     */
    @Select("SELECT task_status, COUNT(*) AS cnt FROM ai_agent_task GROUP BY task_status")
    List<java.util.Map<String, Object>> selectTaskCountByStatus();

    /**
     * 扫描指定统计日的已终态任务行（SLA聚合数据源，仅取聚合必需列，跨scope）
     * @param statDate
     * @return
     */
    @Select("SELECT scope_id, agent_code, task_status, duration_ms, error_message "
            + "FROM ai_agent_task "
            + "WHERE create_time >= #{statDate} AND create_time < DATE_ADD(#{statDate}, INTERVAL 1 DAY) "
            + "AND task_status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    @InterceptorIgnore(tenantLine = "true")
    List<java.util.Map<String, Object>> selectSlaAggRows(@Param("statDate") java.time.LocalDate statDate);

    /**
     * 按Agent聚合近N天根任务运行量与失败量（治理驾驶舱数据源，跨scope）
     * @param fromTime 起始时间(含)
     * @return
     */
    @Select("SELECT agent_code, COUNT(*) AS totalRuns, "
            + "COALESCE(SUM(task_status = 'FAILED'), 0) AS failedRuns "
            + "FROM ai_agent_task "
            + "WHERE create_time >= #{fromTime} AND parent_task_id IS NULL "
            + "GROUP BY agent_code")
    @InterceptorIgnore(tenantLine = "true")
    List<java.util.Map<String, Object>> selectAgentRunAggSince(@Param("fromTime") java.time.LocalDateTime fromTime);

    /**
     * 按日聚合近N天根任务运行量趋势（治理驾驶舱数据源，跨scope）
     * @param fromTime 起始时间(含)
     * @return
     */
    @Select("SELECT DATE(create_time) AS statDay, COUNT(*) AS cnt "
            + "FROM ai_agent_task "
            + "WHERE create_time >= #{fromTime} AND parent_task_id IS NULL "
            + "GROUP BY DATE(create_time)")
    @InterceptorIgnore(tenantLine = "true")
    List<java.util.Map<String, Object>> selectRunTrendSince(@Param("fromTime") java.time.LocalDateTime fromTime);
}

