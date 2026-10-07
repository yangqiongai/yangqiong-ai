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
package com.yangqiongai.ai.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区路径校验测试
 * @author yangqiong
 */
class WorkspacePathUtilsTest {

    @TempDir
    Path tempDir;

    /**
     * 归一化应解析父级引用并统一路径
     */
    @Test
    void canonicalResolvesParentReferences() throws Exception {
        Path base = Files.createDirectories(tempDir.resolve("base"));
        Path nested = Files.createDirectories(base.resolve("sub"));
        String canonical = WorkspacePathUtils.canonical(nested.resolve("..").resolve("sub").toString());
        assertEquals(nested.toRealPath().toString(), canonical);
        assertEquals(base.toRealPath().toString(), WorkspacePathUtils.canonical(nested.resolve("..").toString()));
    }

    /**
     * 非法路径返回null
     */
    @Test
    void canonicalReturnsNullForInvalidPath() {
        assertNull(WorkspacePathUtils.canonical(null));
        assertNull(WorkspacePathUtils.canonical("  "));
    }

    /**
     * 目录存在性判断
     */
    @Test
    void isDirectoryChecksExistence() {
        assertTrue(WorkspacePathUtils.isDirectory(tempDir.toString()));
        assertFalse(WorkspacePathUtils.isDirectory(tempDir.resolve("not-exists").toString()));
        assertFalse(WorkspacePathUtils.isDirectory(null));
    }

    /**
     * createDirectories应支持自动创建
     */
    @Test
    void createDirectoriesCreatesMissingTree() {
        Path target = tempDir.resolve("a").resolve("b");
        assertTrue(WorkspacePathUtils.createDirectories(target.toString()));
        assertTrue(Files.isDirectory(target));
        assertFalse(WorkspacePathUtils.createDirectories(null));
    }

    /**
     * 组件级前缀比较：同前缀名目录不算在根内
     */
    @Test
    void isUnderRootRejectsSamePrefixSibling() {
        String root = tempDir.resolve("ws").toString();
        assertTrue(WorkspacePathUtils.isUnderRoot(root, tempDir.resolve("ws").resolve("a.txt").toString()));
        assertFalse(WorkspacePathUtils.isUnderRoot(root, tempDir.resolve("ws2").resolve("a.txt").toString()));
        assertFalse(WorkspacePathUtils.isUnderRoot(root, tempDir.resolve("ws-other").toString()));
        assertFalse(WorkspacePathUtils.isUnderRoot(null, "x"));
        assertFalse(WorkspacePathUtils.isUnderRoot(root, null));
    }
}
