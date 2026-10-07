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
package com.yangqiongai.ai.trust.audit.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.trust.audit.entity.AgentActionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent动作审计日志
 * @author yangqiong
 */
@Mapper
public interface AgentActionLogMapper extends BaseMapper<AgentActionLog> {

    /**
     * 统计近N天拒绝判定数量（治理驾驶舱审计异常信号数据源，跨scope）
     * @param fromTime 起始时间(含)
     * @return
     */
    @Select("SELECT COUNT(*) FROM ai_agent_action_log "
            + "WHERE decision = 'DENY' AND create_time >= #{fromTime}")
    @InterceptorIgnore(tenantLine = "true")
    long selectDenyCountSince(@Param("fromTime") LocalDateTime fromTime);

    /**
     * 按日统计近N天拒绝判定数量（治理驾驶舱信号趋势数据源，跨scope）
     * @param fromTime 起始时间(含)
     * @return
     */
    @Select("SELECT DATE(create_time) AS statDay, COUNT(*) AS cnt "
            + "FROM ai_agent_action_log WHERE decision = 'DENY' AND create_time >= #{fromTime} "
            + "GROUP BY DATE(create_time)")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectDenyTrendSince(@Param("fromTime") LocalDateTime fromTime);

    /**
     * 查询近N天最近拒绝判定记录（治理驾驶舱雷达流数据源，跨scope）
     * @param fromTime 起始时间(含)
     * @param limit 条数
     * @return
     */
    @Select("SELECT agent_code, action_type, resource, summary, create_time "
            + "FROM ai_agent_action_log WHERE decision = 'DENY' AND create_time >= #{fromTime} "
            + "ORDER BY create_time DESC LIMIT #{limit}")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectRecentDeny(@Param("fromTime") LocalDateTime fromTime,
                                               @Param("limit") int limit);

    /**
     * 按Agent分组统计近N天拒绝判定数量（治理驾驶舱信号徽标数据源，跨scope）
     * @param fromTime 起始时间(含)
     * @return
     */
    @Select("SELECT agent_code, COUNT(*) AS cnt FROM ai_agent_action_log "
            + "WHERE decision = 'DENY' AND create_time >= #{fromTime} GROUP BY agent_code")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectDenyCountByAgentSince(@Param("fromTime") LocalDateTime fromTime);
}
