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
package com.yangqiongai.ai.data.memory.agentmemory.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.data.memory.agentmemory.entity.AgentMemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent运行记忆条目
 * @author yangqiong
 */
@Mapper
public interface AgentMemoryEntryMapper extends BaseMapper<AgentMemoryEntry> {

    /**
     * 按状态统计记忆条目数（治理驾驶舱信号数据源，跨scope）
     * @param status 记忆状态
     * @return
     */
    @Select("SELECT COUNT(*) FROM ai_agent_memory_entry WHERE status = #{status}")
    @InterceptorIgnore(tenantLine = "true")
    long selectCountByStatus(@Param("status") String status);

    /**
     * 按Agent分组统计指定状态的记忆条目数（治理驾驶舱信号徽标数据源，跨scope）
     * @param status 记忆状态
     * @return
     */
    @Select("SELECT agent_code, COUNT(*) AS cnt, MAX(update_time) AS lastTime "
            + "FROM ai_agent_memory_entry WHERE status = #{status} GROUP BY agent_code")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectCountByAgentByStatus(@Param("status") String status);
}
