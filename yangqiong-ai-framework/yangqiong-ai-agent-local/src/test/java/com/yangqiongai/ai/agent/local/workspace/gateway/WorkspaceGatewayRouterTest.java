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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区网关路由测试
 * @author yangqiong
 */
class WorkspaceGatewayRouterTest {

    /**
     * 按supports分派：SERVER命中服务器网关，自定义类型分派到对应网关，大小写不敏感
     */
    @Test
    void routeDispatchesBySupports() {
        ServerGatewayStub serverGateway = new ServerGatewayStub();
        MockGatewayStub mockGateway = new MockGatewayStub("MOCK");
        WorkspaceGatewayRouter router = new WorkspaceGatewayRouter(List.of(serverGateway, mockGateway));

        assertSame(serverGateway, router.route("SERVER"));
        assertSame(mockGateway, router.route("MOCK"));
        assertSame(mockGateway, router.route("mock"));
        assertSame(mockGateway, router.route(" Mock "));
    }

    /**
     * 空白类型视为SERVER存量默认
     */
    @Test
    void routeDefaultsBlankToServer() {
        ServerGatewayStub serverGateway = new ServerGatewayStub();
        WorkspaceGatewayRouter router = new WorkspaceGatewayRouter(List.of(serverGateway));

        assertSame(serverGateway, router.route(null));
        assertSame(serverGateway, router.route("  "));
    }

    /**
     * 未知类型与无匹配网关时报明确业务异常
     */
    @Test
    void routeRejectsUnknownType() {
        WorkspaceGatewayRouter router = new WorkspaceGatewayRouter(List.of(new ServerGatewayStub()));

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> router.route("BROWSER"));
        assertTrue(unknown.getMessage().contains("不支持的工作区类型"));

        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> new WorkspaceGatewayRouter(List.of()).route("SERVER"));
        assertTrue(empty.getMessage().contains("未找到匹配的工作区网关"));
    }

    /**
     * routable按supports判断类型可路由性
     */
    @Test
    void routableChecksGatewaySupport() {
        WorkspaceGatewayRouter router = new WorkspaceGatewayRouter(List.of(new ServerGatewayStub()));

        assertTrue(router.routable("SERVER"));
        assertTrue(router.routable(null));
        assertFalse(router.routable("CONNECTOR"));
    }

    /**
     * 服务器类型网关桩
     */
    static class ServerGatewayStub implements WorkspaceGateway {

        @Override
        public boolean supports(String type) {
            return WorkspaceRef.TYPE_SERVER.equalsIgnoreCase(type);
        }

        @Override
        public List<Map<String, Object>> tree(WorkspaceRef ref, String dir) {
            return List.of();
        }

        @Override
        public Map<String, Object> read(WorkspaceRef ref, String path, PreviewOptions options) {
            return Map.of();
        }

        @Override
        public WriteResult write(WorkspaceRef ref, String path, byte[] content) {
            return WriteResult.of(path, content == null ? 0 : content.length);
        }

        @Override
        public Map<String, Object> mkdir(WorkspaceRef ref, String path, String name) {
            return Map.of();
        }

        @Override
        public Map<String, Object> move(WorkspaceRef ref, String path, String targetDir) {
            return Map.of();
        }

        @Override
        public void rename(WorkspaceRef ref, String path, String name) {
        }

        @Override
        public void delete(WorkspaceRef ref, String path) {
        }

        @Override
        public Map<String, Object> copy(WorkspaceRef ref, String path, String targetDir) {
            return Map.of();
        }
    }

    /**
     * 自定义类型网关桩（模拟后续连接器/浏览器桥网关注入）
     */
    static class MockGatewayStub extends ServerGatewayStub {

        private final String type;

        MockGatewayStub(String type) {
            this.type = type;
        }

        @Override
        public boolean supports(String supportedType) {
            return type.equalsIgnoreCase(supportedType);
        }
    }
}
