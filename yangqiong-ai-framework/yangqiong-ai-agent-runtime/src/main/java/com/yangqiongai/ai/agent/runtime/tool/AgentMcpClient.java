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
package com.yangqiongai.ai.agent.runtime.tool;

/**
 * Agent MCP客户端
 * <p>
 * 类型安全的MCP客户端抽象，管理客户端生命周期。
 * 工具调用能力由具体实现提供，SPI层仅关注生命周期管理。
 * </p>
 * @author yangqiong
 */
public interface AgentMcpClient extends AutoCloseable {

    /**
     * 获取服务端编码
     * @return
     */
    String getServerCode();

    /**
     * 是否已初始化
     * @return
     */
    boolean isInitialized();

    /**
     * 关闭客户端
     */
    @Override
    void close();
}
