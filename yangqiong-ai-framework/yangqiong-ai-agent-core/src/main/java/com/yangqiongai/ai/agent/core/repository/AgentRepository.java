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
package com.yangqiongai.ai.agent.core.repository;

import com.yangqiongai.ai.agent.core.model.Agent;

import java.util.List;

/**
 * Agent配置仓库
 * @author yangqiong
 */
public interface AgentRepository {

    /**
     * 查询所有Agent
     * @return
     */
    List<Agent> list();

    /**
     * 根据Agent编码查询
     * @param agentCode
     * @return
     */
    Agent getByCode(String agentCode);

    /**
     * 查询所有启用的Agent
     * @return
     */
    List<Agent> listEnabled();

    /**
     * 保存Agent配置
     * @param agent
     */
    void save(Agent agent);

    /**
     * 按ID更新
     * @param agent
     */
    void updateById(Agent agent);

    /**
     * 切换启用/禁用状态
     * @param agentCode
     * @return
     */
    boolean toggleStatus(String agentCode);

    /**
     * 更新启用/禁用状态（0-禁用 1-启用）
     * @param agentCode
     * @param status
     */
    void updateStatus(String agentCode, int status);
}
