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
package com.yangqiongai.ai.memory.api;

import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.memory.ChatMemoryManager;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.MemoryPageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Memory管理
 * @author yangqiong
 */
@Tag(name = "Memory管理接口")
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_SIZE = 20;

    private static final int MAX_SIZE = 200;

    @Autowired
    private ChatMemoryManager chatMemoryManager;

    /**
     * 分页查询记忆列表
     * @param sessionId
     * @param userId
     * @param messageRole
     * @param keyword
     * @param page
     * @param size
     * @return
     */
    @Operation(summary = "分页查询记忆列表")
    @GetMapping
    public ResponseEntity<MemoryPageResult> list(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String messageRole,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        String scopeId = ScopeContext.getScopeId();
        MemoryPageResult result = chatMemoryManager.findPage(sessionId, userId, messageRole, scopeId, keyword, safePage, safeSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 关键字检索记忆内容
     * @param keyword
     * @param limit
     * @return
     */
    @Operation(summary = "关键字检索记忆内容")
    @GetMapping("/search")
    public ResponseEntity<List<ChatMemoryRecord>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        String scopeId = ScopeContext.getScopeId();
        int safeLimit = limit < 1 ? DEFAULT_SIZE : Math.min(limit, MAX_SIZE);
        return ResponseEntity.ok(chatMemoryManager.search(keyword, scopeId, safeLimit));
    }

    /**
     * 查询记忆详情
     * @param id
     * @return
     */
    @Operation(summary = "查询记忆详情")
    @GetMapping("/{id}")
    public ResponseEntity<ChatMemoryRecord> detail(@PathVariable Long id) {
        ChatMemoryRecord record = chatMemoryManager.findById(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        if (!matchesScope(record)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(record);
    }

    /**
     * 删除单条记忆
     * @param id
     * @return
     */
    @Operation(summary = "删除单条记忆")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ChatMemoryRecord record = chatMemoryManager.findById(id);
        if (record == null || !matchesScope(record)) {
            return ResponseEntity.notFound().build();
        }
        chatMemoryManager.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除记忆
     * @param sessionId
     * @param userId
     * @return
     */
    @Operation(summary = "批量删除记忆")
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteBatch(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId) {
        if (isBlank(sessionId) && isBlank(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId 或 userId 至少传一项"));
        }
        String scopeId = ScopeContext.getScopeId();
        int affected = chatMemoryManager.deleteBatch(sessionId, userId, scopeId);
        return ResponseEntity.ok(Map.of("affected", affected));
    }

    /**
     * 按消息角色分组统计
     * @return
     */
    @Operation(summary = "按消息角色分组统计")
    @GetMapping("/stats")
    public ResponseEntity<List<Map<String, Object>>> stats() {
        String scopeId = ScopeContext.getScopeId();
        return ResponseEntity.ok(chatMemoryManager.statsByRole(scopeId));
    }

    private boolean matchesScope(ChatMemoryRecord record) {
        String currentScope = ScopeContext.getScopeId();
        if (isBlank(currentScope)) {
            return true;
        }
        return currentScope.equals(record.getScopeId());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
