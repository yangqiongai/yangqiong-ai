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
package com.yangqiongai.ai.agent.registry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;

import java.util.List;

/**
 * Agent注册中心管理
 * @author yangqiong
 */
public interface AgentRegistryService {

    /**
     * 创建Agent定义
     * @param definition
     * @return
     */
    AgentDefinition createDefinition(AgentDefinition definition);

    /**
     * 更新Agent定义元数据
     * @param agentCode
     * @param definition
     * @return
     */
    AgentDefinition updateDefinition(String agentCode, AgentDefinition definition);

    /**
     * 查询Agent定义
     * @param agentCode
     * @return
     */
    AgentDefinition getDefinition(String agentCode);

    /**
     * 分页查询Agent定义
     * @param pageNum
     * @param pageSize
     * @param status
     * @param keyword
     * @return
     */
    Page<AgentDefinition> listDefinitions(int pageNum, int pageSize, String status, String keyword);

    /**
     * 启用/禁用Agent定义（启用时物化当前生效版本）
     * @param agentCode
     * @param enabled
     * @return
     */
    AgentDefinition updateStatus(String agentCode, boolean enabled);

    /**
     * 启用/禁用A2A卡片对外发布
     * @param agentCode
     * @param enabled
     * @return
     */
    AgentDefinition updateCardEnabled(String agentCode, boolean enabled);

    /**
     * 删除Agent定义（仅DRAFT且无版本）
     * @param agentCode
     */
    void deleteDefinition(String agentCode);

    /**
     * 创建版本草稿
     * @param agentCode
     * @param version
     * @return
     */
    AgentVersion createVersion(String agentCode, AgentVersion version);

    /**
     * 更新版本草稿（仅DRAFT，重算hash）
     * @param versionId
     * @param version
     * @return
     */
    AgentVersion updateDraft(Long versionId, AgentVersion version);

    /**
     * 查询Agent的版本列表
     * @param agentCode
     * @return
     */
    List<AgentVersion> listVersions(String agentCode);

    /**
     * 查询版本
     * @param versionId
     * @return
     */
    AgentVersion getVersion(Long versionId);
}
