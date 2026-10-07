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
package com.yangqiongai.ai.platform.api.agent;

import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.spi.AgentScopeFilter;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDirectoryEntity;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.util.ImageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent管理接口
 * @author yangqiong
 */
@Tag(name = "Agent管理接口")
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentManager agentService;

    @Autowired
    private AgentDirectoryService directoryService;

    /**
     * 数据范围过滤（社区版不装配全放行，企业版按租户隔离）
     */
    @Autowired(required = false)
    private AgentScopeFilter scopeFilter;

    /**
     * 查询所有Agent
     * @return
     */
    @Operation(summary = "查询所有Agent")
    @GetMapping("/list")
    public ApiResult<List<Agent>> list() {
        return ApiResult.ok(applyListFilter(agentService.list()));
    }

    /**
     * 查询启用的Agent
     * @return
     */
    @Operation(summary = "查询启用的Agent")
    @GetMapping("/enabled")
    public ApiResult<List<Agent>> listEnabled() {
        return ApiResult.ok(applyListFilter(agentService.listEnabled()));
    }

    /**
     * 查询已注册处理器清单（内存注册来源，不查库）
     * @return
     */
    @Operation(summary = "查询已注册处理器清单")
    @GetMapping("/processors")
    public ApiResult<List<Map<String, Object>>> listProcessors() {
        return ApiResult.ok(agentService.listProcessorOptions());
    }

    /**
     * 查询单个Agent
     * @param agentCode
     * @return
     */
    @Operation(summary = "查询单个Agent")
    @GetMapping("/{agentCode}")
    public ApiResult<Agent> getByCode(@PathVariable String agentCode) {
        Agent agent = applyOneFilter(agentService.getByCode(agentCode));
        if (agent == null) {
            return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(), "Agent不存在: " + agentCode);
        }
        return ApiResult.ok(agent);
    }

    /**
     * 新增Agent
     * @param agent
     * @return
     */
    @Operation(summary = "新增Agent")
    @PostMapping
    public ApiResult<Agent> create(@RequestBody Agent agent) {
        // agentCode同作用域内唯一：复制模式下平台模板与租户副本同码共存，getByCode按当前scope查重
        if (agentService.getByCode(agent.getAgentCode()) != null) {
            return ApiResult.fail(AiErrorCode.FORBIDDEN.getCode(), "Agent编码已存在: " + agent.getAgentCode());
        }
        // 上传的base64图标统一压缩到80px内，控制存储体积
        agent.setIcon(ImageUtils.compressIconDataUrl(agent.getIcon()));
        agentService.save(agent);
        return ApiResult.ok(agent);
    }

    /**
     * 更新Agent
     * @param agentCode
     * @param agent
     * @return
     */
    @Operation(summary = "更新Agent")
    @PutMapping("/{agentCode}")
    public ApiResult<Agent> update(@PathVariable String agentCode, @RequestBody Agent agent) {
        Agent existing = agentService.getByCode(agentCode);
        if (existing == null) {
            return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(), "Agent不存在: " + agentCode);
        }
        checkEditable(existing);
        agent.setId(existing.getId());
        agent.setAgentCode(agentCode);
        // 上传的base64图标统一压缩到80px内，控制存储体积
        agent.setIcon(ImageUtils.compressIconDataUrl(agent.getIcon()));
        agentService.updateById(agent);
        return ApiResult.ok(agent);
    }

    /**
     * 切换启用/禁用状态
     * @param agentCode
     * @return
     */
    @Operation(summary = "切换启用/禁用状态")
    @PutMapping("/{agentCode}/status")
    public ApiResult<Map<String, Object>> toggleStatus(@PathVariable String agentCode) {
        Agent existing = agentService.getByCode(agentCode);
        if (existing == null) {
            return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(), "Agent不存在: " + agentCode);
        }
        checkEditable(existing);
        boolean success = agentService.toggleStatus(agentCode);
        Agent updated = agentService.getByCode(agentCode);
        return ApiResult.ok(Map.of(
            "success", success,
            "agentCode", agentCode,
            "status", updated != null && updated.getStatus() != null ? updated.getStatus() : -1
        ));
    }

    /**
     * 调整排序
     * @param sortList
     * @return
     */
    @Operation(summary = "调整排序")
    @PutMapping("/sort")
    public ApiResult<Void> sort(@RequestBody List<Map<String, Object>> sortList) {
        for (Map<String, Object> item : sortList) {
            String agentCode = (String) item.get("agentCode");
            Integer sortOrder = (Integer) item.get("sortOrder");
            if (agentCode != null && sortOrder != null) {
                Agent agent = agentService.getByCode(agentCode);
                if (agent != null) {
                    checkEditable(agent);
                    agent.setSortOrder(sortOrder);
                    agentService.updateById(agent);
                }
            }
        }
        return ApiResult.ok("排序更新成功", null);
    }

    /**
     * 手动触发同步
     * @return
     */
    @Operation(summary = "手动触发同步")
    @PostMapping("/sync")
    public ApiResult<Void> sync() {
        agentService.syncProcessorsFromSpring();
        return ApiResult.ok("同步完成", null);
    }

    /**
     * 查询智能体目录树（各节点含挂载智能体数）
     * @return
     */
    @Operation(summary = "查询智能体目录树")
    @GetMapping("/directory/tree")
    public ApiResult<Map<String, Object>> directoryTree() {
        return ApiResult.ok(directoryService.tree());
    }

    /**
     * 新增智能体目录节点
     * @param directory
     * @return
     */
    @Operation(summary = "新增智能体目录节点")
    @PostMapping("/directory")
    public ApiResult<AgentDirectoryEntity> createDirectory(@RequestBody AgentDirectoryEntity directory) {
        return ApiResult.ok(directoryService.create(directory));
    }

    /**
     * 更新智能体目录节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param directory
     * @return
     */
    @Operation(summary = "更新智能体目录节点")
    @PutMapping("/directory/{id}")
    public ApiResult<AgentDirectoryEntity> updateDirectory(
            @PathVariable Long id,
            @RequestBody AgentDirectoryEntity directory) {
        return ApiResult.ok(directoryService.update(id, directory));
    }

    /**
     * 删除智能体目录节点（存在子节点或挂载智能体时禁止删除）
     * @param id
     * @return
     */
    @Operation(summary = "删除智能体目录节点")
    @DeleteMapping("/directory/{id}")
    public ApiResult<Void> deleteDirectory(@PathVariable Long id) {
        directoryService.delete(id);
        return ApiResult.ok();
    }

    /**
     * 应用列表数据范围过滤
     * @param agents
     * @return
     */
    private List<Agent> applyListFilter(List<Agent> agents) {
        return scopeFilter != null ? scopeFilter.filterList(agents) : agents;
    }

    /**
     * 应用单条数据范围过滤
     * @param agent
     * @return
     */
    private Agent applyOneFilter(Agent agent) {
        return scopeFilter != null ? scopeFilter.filterOne(agent) : agent;
    }

    /**
     * 校验编辑权限
     * @param agent
     */
    private void checkEditable(Agent agent) {
        if (scopeFilter != null) {
            scopeFilter.checkEditable(agent);
        }
    }
}
