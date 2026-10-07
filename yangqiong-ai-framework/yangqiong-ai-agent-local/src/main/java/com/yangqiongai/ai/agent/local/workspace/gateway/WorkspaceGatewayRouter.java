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
package com.yangqiongai.ai.agent.local.workspace.gateway;

import java.util.List;
import java.util.Locale;

/**
 * 工作区网关组合路由器
 * <p>
 * 汇集容器内全部WorkspaceGateway实现，按工作区类型分派；
 * 后续连接器/浏览器桥网关实现SPI注册bean即自动接入，无需改动路由器。
 * </p>
 * @author yangqiong
 */
public class WorkspaceGatewayRouter {

    /**
     * 容器内全部网关实现
     */
    private final List<WorkspaceGateway> gateways;

    /**
     * 按容器内全部网关实现构建路由器
     * @param gateways
     */
    public WorkspaceGatewayRouter(List<WorkspaceGateway> gateways) {
        this.gateways = gateways == null ? List.of() : List.copyOf(gateways);
    }

    /**
     * 按工作区类型分派网关（类型大小写不敏感，空白视为SERVER存量默认）
     * @param type
     * @return
     */
    public WorkspaceGateway route(String type) {
        String normalized = normalize(type);
        for (WorkspaceGateway gateway : gateways) {
            if (gateway.supports(normalized)) {
                return gateway;
            }
        }
        throw new IllegalArgumentException("不支持的工作区类型：" + type + "，未找到匹配的工作区网关");
    }

    /**
     * 判断类型是否可路由
     * @param type
     * @return
     */
    public boolean routable(String type) {
        String normalized = normalize(type);
        return gateways.stream().anyMatch(gateway -> gateway.supports(normalized));
    }

    private String normalize(String type) {
        return type == null || type.isBlank() ? WorkspaceRef.TYPE_SERVER : type.trim().toUpperCase(Locale.ROOT);
    }
}
