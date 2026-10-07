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
package com.yangqiongai.ai.trust.identity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.trust.identity.entity.AgentIdentity;
import com.yangqiongai.ai.trust.identity.mapper.AgentIdentityMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Agent身份档案存储
 * @author yangqiong
 */
public class DefaultAgentIdentityRepository implements AgentIdentityRepository {

    @Autowired
    private AgentIdentityMapper identityMapper;

    @Override
    public AgentIdentity findByUid(String identityUid) {
        return identityMapper.selectOne(new LambdaQueryWrapper<AgentIdentity>()
                .eq(AgentIdentity::getIdentityUid, identityUid)
                .last("LIMIT 1"));
    }

    @Override
    public AgentIdentity findByAgentCode(String agentCode) {
        return identityMapper.selectOne(new LambdaQueryWrapper<AgentIdentity>()
                .eq(AgentIdentity::getAgentCode, agentCode)
                .last("LIMIT 1"));
    }

    @Override
    public List<AgentIdentity> findAll() {
        return identityMapper.selectList(new LambdaQueryWrapper<AgentIdentity>()
                .orderByDesc(AgentIdentity::getUpdateTime));
    }

    @Override
    public void insert(AgentIdentity identity) {
        identityMapper.insert(identity);
    }

    @Override
    public void update(AgentIdentity identity) {
        identityMapper.updateById(identity);
    }
}
