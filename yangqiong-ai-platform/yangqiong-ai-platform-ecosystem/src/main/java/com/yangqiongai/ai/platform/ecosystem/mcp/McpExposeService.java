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
package com.yangqiongai.ai.platform.ecosystem.mcp;

import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;

import java.util.List;

/**
 * MCP暴露白名单管理
 * @author yangqiong
 */
public interface McpExposeService {

    /**
     * 保存暴露配置(编码+类型唯一)
     * @param expose
     * @return
     */
    McpServerExpose save(McpServerExpose expose);

    /**
     * 启用/禁用暴露配置
     * @param id
     * @return
     */
    void toggle(Long id);

    /**
     * 删除暴露配置
     * @param id
     * @return
     */
    void delete(Long id);

    /**
     * 查询单个暴露配置
     * @param id
     * @return
     */
    McpServerExpose get(Long id);

    /**
     * 查询暴露配置列表
     * @param exposeType
     * @param exposeCode
     * @return
     */
    List<McpServerExpose> list(String exposeType, String exposeCode);

    /**
     * 查询已启用的暴露配置列表
     * @param exposeType
     * @return
     */
    List<McpServerExpose> listEnabled(String exposeType);

    /**
     * 按类型与编码查询已启用的暴露配置
     * @param exposeType
     * @param exposeCode
     * @return 不存在或未启用时返回null
     */
    McpServerExpose getEnabled(String exposeType, String exposeCode);
}
