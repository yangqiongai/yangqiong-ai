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
package com.yangqiongai.ai.trigger.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.trigger.entity.AgentTriggerLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent触发记录
 * @author yangqiong
 */
@Mapper
public interface AgentTriggerLogMapper extends BaseMapper<AgentTriggerLogEntity> {

    /**
     * 查询最近N次触发任务全部失败的启用触发器（治理驾驶舱连续失败信号数据源，跨scope）
     * <p>
     * 按触发器分组取最近N条已关联任务的日志，全部FAILED视为连续失败；
     * 最新一次任务非终态(RUNNING等)时计入窗口导致FAILED数不足，不误报。
     * </p>
     * @param minFails 连续失败次数阈值
     * @return
     */
    @Select("SELECT trigger_id, trigger_code, agent_code, MAX(create_time) AS lastTime, COUNT(*) AS fails "
            + "FROM ("
            + "  SELECT l.trigger_id, l.trigger_code, t.agent_code, t.task_status, l.create_time, "
            + "         ROW_NUMBER() OVER (PARTITION BY l.trigger_id ORDER BY l.create_time DESC) AS rn "
            + "  FROM ai_agent_trigger_log l "
            + "  INNER JOIN ai_agent_task t ON t.task_id = l.task_id "
            + "  WHERE l.trigger_id IN (SELECT id FROM ai_agent_trigger WHERE enabled = 1)"
            + ") ranked "
            + "WHERE rn <= #{minFails} AND task_status = 'FAILED' "
            + "GROUP BY trigger_id, trigger_code, agent_code "
            + "HAVING COUNT(*) = #{minFails}")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectConsecutiveFailures(@Param("minFails") int minFails);
}
