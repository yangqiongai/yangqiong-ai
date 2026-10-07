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
package com.yangqiongai.ai.memory.agentmemory.api;

import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.model.OrgContextInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.service.AgentMemoryEraseService;
import com.yangqiongai.ai.memory.agentmemory.service.AgentMemoryGovernService;
import com.yangqiongai.ai.memory.agentmemory.service.OrgContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent运行记忆管理
 * @author yangqiong
 */
@Tag(name = "Agent运行记忆管理接口")
@RestController
@RequestMapping("/api/agent-memory")
public class AgentMemoryController {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_SIZE = 20;

    private static final int MAX_SIZE = 200;

    @Autowired
    private AgentMemoryEntryRepository entryRepository;

    @Autowired
    private AgentMemoryGovernService governService;

    @Autowired
    private AgentMemoryEraseService eraseService;

    @Autowired
    private OrgContextService orgContextService;

    /**
     * 分页查询记忆条目
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @param status
     * @param page
     * @param size
     * @return
     */
    @Operation(summary = "分页查询记忆条目")
    @GetMapping("/entries")
    public ResponseEntity<Map<String, Object>> listEntries(
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) String userAnchor,
            @RequestParam(required = false) String memoryType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        long total = entryRepository.countByCondition(agentCode, userAnchor, memoryType, status);
        List<AgentMemoryEntryInfo> records = total > 0
                ? entryRepository.findPage(agentCode, userAnchor, memoryType, status,
                        (safePage - 1) * safeSize, safeSize)
                : List.of();
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("records", records);
        return ResponseEntity.ok(result);
    }

    /**
     * 记忆指标统计
     * @return
     */
    @Operation(summary = "记忆指标统计")
    @GetMapping("/entries/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> result = new HashMap<>();
        result.put("active", entryRepository.countByStatus(AgentMemoryEntryInfo.STATUS_ACTIVE));
        result.put("stale", entryRepository.countByStatus(AgentMemoryEntryInfo.STATUS_STALE));
        result.put("quarantined", entryRepository.countByStatus(AgentMemoryEntryInfo.STATUS_QUARANTINED));
        result.put("orgContexts", orgContextService.listAll().size());
        return ResponseEntity.ok(result);
    }

    /**
     * 查看记忆条目
     * @param id
     * @return
     */
    @Operation(summary = "查看记忆条目")
    @GetMapping("/entries/{id}")
    public ResponseEntity<AgentMemoryEntryInfo> getEntry(@PathVariable Long id) {
        return ResponseEntity.ok(entryRepository.selectById(id));
    }

    /**
     * 治理处置：隔离
     * @param id
     * @param reason
     * @return
     */
    @Operation(summary = "隔离记忆条目")
    @PostMapping("/entries/{id}/quarantine")
    public ResponseEntity<AgentMemoryEntryInfo> quarantine(@PathVariable Long id,
                                                       @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(governService.quarantine(id, reason));
    }

    /**
     * 治理处置：解除隔离
     * @param id
     * @return
     */
    @Operation(summary = "解除隔离")
    @PostMapping("/entries/{id}/release")
    public ResponseEntity<AgentMemoryEntryInfo> release(@PathVariable Long id) {
        return ResponseEntity.ok(governService.release(id));
    }

    /**
     * 治理处置：负反馈降权
     * @param id
     * @return
     */
    @Operation(summary = "负反馈降权")
    @PostMapping("/entries/{id}/negative-feedback")
    public ResponseEntity<AgentMemoryEntryInfo> negativeFeedback(@PathVariable Long id) {
        return ResponseEntity.ok(governService.reportNegativeFeedback(id));
    }

    /**
     * 治理处置：归档
     * @param id
     * @return
     */
    @Operation(summary = "归档记忆条目")
    @PostMapping("/entries/{id}/archive")
    public ResponseEntity<AgentMemoryEntryInfo> archive(@PathVariable Long id) {
        return ResponseEntity.ok(governService.archive(id));
    }

    /**
     * 合规擦除：按用户锚点
     * @param userAnchor
     * @return
     */
    @Operation(summary = "按用户锚点擦除")
    @DeleteMapping("/entries/by-user")
    public ResponseEntity<Map<String, Object>> eraseByUser(@RequestParam String userAnchor) {
        return ResponseEntity.ok(resultOf(eraseService.eraseByUser(userAnchor)));
    }

    /**
     * 合规擦除：按Agent编码
     * @param agentCode
     * @return
     */
    @Operation(summary = "按Agent编码擦除")
    @DeleteMapping("/entries/by-agent")
    public ResponseEntity<Map<String, Object>> eraseByAgent(@RequestParam String agentCode) {
        return ResponseEntity.ok(resultOf(eraseService.eraseByAgent(agentCode)));
    }

    /**
     * 组织上下文列表
     * @return
     */
    @Operation(summary = "组织上下文列表")
    @GetMapping("/org-contexts")
    public ResponseEntity<List<OrgContextInfo>> listOrgContextInfos() {
        return ResponseEntity.ok(orgContextService.listAll());
    }

    /**
     * 创建组织上下文
     * @param context
     * @return
     */
    @Operation(summary = "创建组织上下文")
    @PostMapping("/org-contexts")
    public ResponseEntity<OrgContextInfo> createOrgContextInfo(@RequestBody OrgContextInfo context) {
        return ResponseEntity.ok(orgContextService.create(context));
    }

    /**
     * 更新组织上下文
     * @param id
     * @param context
     * @return
     */
    @Operation(summary = "更新组织上下文")
    @PutMapping("/org-contexts/{id}")
    public ResponseEntity<Void> updateOrgContextInfo(@PathVariable Long id, @RequestBody OrgContextInfo context) {
        context.setId(id);
        orgContextService.update(context);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除组织上下文
     * @param id
     * @return
     */
    @Operation(summary = "删除组织上下文")
    @DeleteMapping("/org-contexts/{id}")
    public ResponseEntity<Void> deleteOrgContextInfo(@PathVariable Long id) {
        orgContextService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * CSV导入组织上下文
     * @param csvContent
     * @return
     */
    @Operation(summary = "CSV导入组织上下文")
    @PostMapping("/org-contexts/import")
    public ResponseEntity<OrgContextService.ImportSummary> importOrgContextInfos(@RequestBody String csvContent) {
        return ResponseEntity.ok(orgContextService.importCsv(csvContent));
    }

    /**
     * 构建擦除结果
     * @param erasedCount
     * @return
     */
    private Map<String, Object> resultOf(int erasedCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("erasedCount", erasedCount);
        return result;
    }
}
