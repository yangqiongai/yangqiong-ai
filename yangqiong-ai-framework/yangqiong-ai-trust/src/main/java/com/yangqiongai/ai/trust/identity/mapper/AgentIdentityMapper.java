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
package com.yangqiongai.ai.trust.identity.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.trust.identity.entity.AgentIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent身份档案
 * @author yangqiong
 */
@Mapper
public interface AgentIdentityMapper extends BaseMapper<AgentIdentity> {

    /**
     * 查询启用轮换的ACTIVE身份列表（治理驾驶舱轮换到期信号数据源，到期判定服务端计算，跨scope）
     * @return
     */
    @Select("SELECT agent_code, identity_uid, rotate_days, last_rotated_time "
            + "FROM ai_agent_identity WHERE status = 'ACTIVE' AND rotate_days IS NOT NULL")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectActiveRotationList();
}
