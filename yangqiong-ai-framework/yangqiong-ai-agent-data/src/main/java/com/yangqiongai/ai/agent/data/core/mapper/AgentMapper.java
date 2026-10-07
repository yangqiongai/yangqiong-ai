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
import com.yangqiongai.ai.agent.data.core.entity.AgentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {

    /**
     * 查询全部Agent基础信息（治理驾驶舱清单数据源，跨scope）
     * <p>复制模式下同一 agent_code 可在多作用域共存，按 agent_code 聚合去重，避免同码副本产生重复节点</p>
     * @return
     */
    @Select("SELECT agent_code, MAX(agent_name) AS agent_name, MAX(status) AS status FROM ai_agent GROUP BY agent_code ORDER BY agent_code")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectDashboardAgents();
}

