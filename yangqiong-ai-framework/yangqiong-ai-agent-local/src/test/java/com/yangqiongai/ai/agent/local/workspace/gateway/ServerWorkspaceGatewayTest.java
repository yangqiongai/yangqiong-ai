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

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.repository.UserWorkspaceRepository;
import com.yangqiongai.ai.agent.local.workspace.UserWorkspaceService;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务器工作区网关测试
 * @author yangqiong
 */
class ServerWorkspaceGatewayTest {

    @TempDir
    Path tempDir;

    private UserWorkspaceService service;

    private WorkspaceProperties properties;

    private ServerWorkspaceGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        service = new UserWorkspaceService();
        properties = new WorkspaceProperties();
        properties.setEnabled(true);
        inject(service, "userWorkspaceRepository", new InMemoryRepository());
        inject(service, "workspaceProperties", properties);
        gateway = new ServerWorkspaceGateway(service);
    }

    /**
     * 仅支持SERVER类型
     */
    @Test
    void supportsOnlyServerType() {
        assertTrue(gateway.supports("SERVER"));
        assertTrue(gateway.supports("server"));
        assertFalse(gateway.supports("CONNECTOR"));
        assertFalse(gateway.supports("BROWSER"));
        assertFalse(gateway.supports(null));
    }

    /**
     * 目录树包装行为与UserWorkspaceService直调一致
     */
    @Test
    void treeMatchesDirectServiceCall() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.createDirectories(root.resolve("b-dir"));
        Files.writeString(root.resolve("z.txt"), "zz");
        WorkspaceInfo info = service.create("u1", "工作区", root.toString(), null, null, false);
        WorkspaceRef ref = WorkspaceRef.of(info);

        assertEquals(service.tree(info.getId(), "u1", null), gateway.tree(ref, null));
        assertEquals(service.tree(info.getId(), "u1", "b-dir"), gateway.tree(ref, "b-dir"));
        assertThrows(IllegalArgumentException.class, () -> gateway.tree(ref, "..\\secret"));
        assertThrows(IllegalArgumentException.class, () -> gateway.tree(ref, "not-exists"));

        // 归属校验保留在网关调用链上：篡改引用归属人后被拒绝
        WorkspaceRef forged = WorkspaceRef.of(info);
        forged.setUserId("u2");
        assertThrows(IllegalArgumentException.class, () -> gateway.tree(forged, null));
    }

    /**
     * 预览包装行为与UserWorkspaceService直调一致（结果Map逐字段相等）
     */
    @Test
    void readMatchesDirectPreviewCall() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "hello 中文");
        WorkspaceInfo info = service.create("u1", "工作区", root.toString(), null, null, false);
        WorkspaceRef ref = WorkspaceRef.of(info);

        assertEquals(service.preview(info.getId(), "u1", "a.txt"),
                gateway.read(ref, "a.txt", PreviewOptions.empty()));
        assertEquals("hello 中文", gateway.read(ref, "a.txt", null).get("content"));
        assertThrows(IllegalArgumentException.class, () -> gateway.read(ref, "..\\..\\outside.txt", PreviewOptions.empty()));
        assertThrows(IllegalArgumentException.class, () -> gateway.read(ref, ".", PreviewOptions.empty()));
    }

    /**
     * 写入创建/覆盖文件并自动补建父目录，结果与直调writeFile一致，越界拒绝
     */
    @Test
    void writeCreatesOverwritesAndRejectsTraversal() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "工作区", root.toString(), null, null, false);
        WorkspaceRef ref = WorkspaceRef.of(info);

        WriteResult created = gateway.write(ref, "docs/n.txt", "你好".getBytes(StandardCharsets.UTF_8));
        assertEquals("docs/n.txt", created.getPath());
        assertEquals("你好".getBytes(StandardCharsets.UTF_8).length, created.getSize());
        assertEquals("你好", Files.readString(root.resolve("docs").resolve("n.txt")));

        WriteResult overwritten = gateway.write(ref, "docs/n.txt", "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals(3, overwritten.getSize());
        assertEquals("abc", Files.readString(root.resolve("docs").resolve("n.txt")));

        assertThrows(IllegalArgumentException.class, () -> gateway.write(ref, "../escape.txt", "x".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> gateway.write(ref, " ", "x".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> gateway.write(ref, "a\\..\\escape.txt", "x".getBytes()));
    }

    /**
     * mkdir/move/rename/copy/delete包装行为与UserWorkspaceService直调一致
     */
    @Test
    void managementOperationsMatchDirectServiceCall() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "A");
        WorkspaceInfo info = service.create("u1", "工作区", root.toString(), null, null, false);
        WorkspaceRef ref = WorkspaceRef.of(info);

        Map<String, Object> mkdirResult = gateway.mkdir(ref, "", "目录");
        assertEquals("目录", mkdirResult.get("path"));
        assertTrue(Files.isDirectory(root.resolve("目录")));

        Map<String, Object> moveResult = gateway.move(ref, "a.txt", "目录");
        assertEquals("目录/a.txt", moveResult.get("path"));
        assertFalse(Files.exists(root.resolve("a.txt")));
        assertTrue(Files.exists(root.resolve("目录").resolve("a.txt")));

        gateway.rename(ref, "目录/a.txt", "b.txt");
        assertEquals("A", Files.readString(root.resolve("目录").resolve("b.txt")));
        assertThrows(IllegalArgumentException.class, () -> gateway.rename(ref, "目录/b.txt", "c:d"));

        Map<String, Object> copyResult = gateway.copy(ref, "目录/b.txt", null);
        assertEquals("b.txt", copyResult.get("path"));
        assertEquals("A", Files.readString(root.resolve("b.txt")));

        gateway.delete(ref, "b.txt");
        assertFalse(Files.exists(root.resolve("b.txt")));
        assertThrows(IllegalArgumentException.class, () -> gateway.delete(ref, null));
        assertThrows(IllegalArgumentException.class, () -> gateway.delete(ref, "."));
    }

    /**
     * 全部操作保留归属校验与根目录保护（他人/根目录操作拒绝）
     */
    @Test
    void gatewayKeepsOwnershipAndRootProtections() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        Files.writeString(root.resolve("a.txt"), "A");
        WorkspaceInfo info = service.create("u1", "工作区", root.toString(), null, null, false);
        WorkspaceRef ref = WorkspaceRef.of(info);

        WorkspaceInfo foreign = new WorkspaceInfo();
        foreign.setId(info.getId());
        foreign.setUserId("u2");
        foreign.setName("冒名引用");
        foreign.setRootPath(info.getRootPath());
        foreign.setApprovalMode("MANUAL");
        foreign.setStatus(1);
        WorkspaceRef forged = WorkspaceRef.of(foreign);
        forged.setType("SERVER");

        assertThrows(IllegalArgumentException.class, () -> gateway.tree(forged, null));
        assertThrows(IllegalArgumentException.class, () -> gateway.read(forged, "a.txt", PreviewOptions.empty()));
        assertThrows(IllegalArgumentException.class, () -> gateway.write(forged, "x.txt", "x".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> gateway.mkdir(forged, "", "d"));
        assertThrows(IllegalArgumentException.class, () -> gateway.move(forged, "a.txt", ""));
        assertThrows(IllegalArgumentException.class, () -> gateway.rename(forged, "a.txt", "b.txt"));
        assertThrows(IllegalArgumentException.class, () -> gateway.delete(forged, "a.txt"));
        assertThrows(IllegalArgumentException.class, () -> gateway.copy(forged, "a.txt", null));

        assertThrows(IllegalArgumentException.class, () -> gateway.delete(ref, "."));
        assertThrows(IllegalArgumentException.class, () -> gateway.move(ref, null, ""));
        assertThrows(IllegalArgumentException.class, () -> gateway.copy(ref, null, null));
    }

    /**
     * SERVER引用携带根路径，非SERVER类型根路径置空
     */
    @Test
    void refCarriesRootPathOnlyForServerType() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = service.create("u1", "工作区", root.toString(), null, null, false);
        WorkspaceRef ref = WorkspaceRef.of(info);
        assertEquals("SERVER", ref.getType());
        assertEquals(info.getRootPath(), ref.getRootPath());
        assertEquals("u1", ref.getUserId());

        WorkspaceInfo connectorLike = new WorkspaceInfo();
        connectorLike.setId(9L);
        connectorLike.setUserId("u1");
        connectorLike.setType("CONNECTOR");
        connectorLike.setRootPath("D:/should-not-leak");
        WorkspaceRef connectorRef = WorkspaceRef.of(connectorLike);
        assertEquals("CONNECTOR", connectorRef.getType());
        assertNull(connectorRef.getRootPath());
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 内存版用户工作区仓库
     */
    static class InMemoryRepository implements UserWorkspaceRepository {

        private final List<WorkspaceInfo> store = new ArrayList<>();

        private final AtomicLong idGen = new AtomicLong(1);

        @Override
        public void save(WorkspaceInfo info) {
            info.setId(idGen.getAndIncrement());
            store.add(info);
        }

        @Override
        public void updateById(WorkspaceInfo info) {
            for (int i = 0; i < store.size(); i++) {
                if (store.get(i).getId().equals(info.getId())) {
                    store.set(i, info);
                    return;
                }
            }
        }

        @Override
        public void deleteById(Long id) {
            store.removeIf(w -> w.getId().equals(id));
        }

        @Override
        public WorkspaceInfo findById(Long id) {
            return store.stream()
                    .filter(w -> w.getId().equals(id))
                    .findFirst().orElse(null);
        }

        @Override
        public WorkspaceInfo findByIdAndUserId(Long id, String userId) {
            return store.stream()
                    .filter(w -> w.getId().equals(id) && w.getUserId().equals(userId))
                    .findFirst().orElse(null);
        }

        @Override
        public List<WorkspaceInfo> listByUserId(String userId) {
            return store.stream().filter(w -> w.getUserId().equals(userId)).toList();
        }

        @Override
        public boolean existsByRootPath(String rootPath) {
            return store.stream().anyMatch(w -> w.getRootPath().equals(rootPath));
        }

        @Override
        public long countByUserId(String userId) {
            return store.stream().filter(w -> w.getUserId().equals(userId)).count();
        }
    }
}
