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
package com.yangqiongai.ai.agent.mcp.repository;

import com.yangqiongai.ai.agent.mcp.model.McpServerConfigInfo;

import java.util.List;

/**
 * MCP服务配置仓库
 * @author yangqiong
 */
public interface McpServerConfigRepository {

    /**
     * 查询所有配置
     * @return
     */
    List<McpServerConfigInfo> list();

    /**
     * 根据serverCode查询配置
     * @param serverCode
     * @return
     */
    McpServerConfigInfo getByServerCode(String serverCode);

    /**
     * 查询所有启用的配置
     * @return
     */
    List<McpServerConfigInfo> listEnabled();

    /**
     * 保存配置
     * @param entity
     */
    void save(McpServerConfigInfo entity);

    /**
     * 按ID更新
     * @param entity
     */
    void updateById(McpServerConfigInfo entity);

    /**
     * 切换服务状态
     * @param serverCode
     * @param status
     * @return
     */
    boolean toggleStatus(String serverCode, int status);

    /**
     * 自动下线
     * @param serverCode
     * @param reason
     * @return
     */
    boolean autoOffline(String serverCode, String reason);

    /**
     * 按ID删除
     * @param id
     * @return
     */
    boolean removeById(Long id);

    /**
     * 查询可恢复的服务（状态启用但含下线原因）
     * @return
     */
    List<McpServerConfigInfo> findRecoverableServices();

    /**
     * 按serverCode显式清单查询启用配置
     * @param serverCodes agentConfig.mcpServers声明的serverCode清单
     * @return
     */
    List<McpServerConfigInfo> listByServerCodes(List<String> serverCodes);
}
