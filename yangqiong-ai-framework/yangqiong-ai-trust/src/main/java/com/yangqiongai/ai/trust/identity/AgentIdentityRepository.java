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

import com.yangqiongai.ai.trust.identity.entity.AgentIdentity;

import java.util.List;

/**
 * Agent身份档案存储
 * @author yangqiong
 */
public interface AgentIdentityRepository {

    /**
     * 按身份唯一标识查询
     * @param identityUid
     * @return
     */
    AgentIdentity findByUid(String identityUid);

    /**
     * 按Agent编码查询
     * @param agentCode
     * @return
     */
    AgentIdentity findByAgentCode(String agentCode);

    /**
     * 查询全部身份档案
     * @return
     */
    List<AgentIdentity> findAll();

    /**
     * 新增身份档案
     * @param identity
     * @return
     */
    void insert(AgentIdentity identity);

    /**
     * 更新身份档案
     * @param identity
     * @return
     */
    void update(AgentIdentity identity);
}
