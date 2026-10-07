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
package com.yangqiongai.ai.trigger.api;

import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.entity.AgentTriggerLogEntity;
import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.repository.AgentTriggerLogRepository;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
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
 * Agent触发器管理
 * @author yangqiong
 */
@Tag(name = "Agent触发器管理接口")
@RestController
@RequestMapping("/api/agent-trigger")
public class AgentTriggerController {

    private static final int DEFAULT_PAGE = 1;

    private static final int DEFAULT_SIZE = 20;

    private static final int MAX_SIZE = 200;

    @Autowired
    private AgentTriggerService triggerService;

    @Autowired
    private AgentTriggerRepository triggerRepository;

    @Autowired
    private AgentTriggerLogRepository logRepository;

    /**
     * 分页查询触发规则
     * @param triggerType
     * @param agentCode
     * @param enabled
     * @param page
     * @param size
     * @return
     */
    @Operation(summary = "分页查询触发规则")
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String triggerType,
                                                    @RequestParam(required = false) String agentCode,
                                                    @RequestParam(required = false) Integer enabled,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        int safePage = page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        long total = triggerRepository.countByCondition(triggerType, agentCode, enabled);
        List<AgentTriggerEntity> records = total > 0
                ? triggerRepository.findPage(triggerType, agentCode, enabled,
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
     * 查询触发规则详情
     * @param id
     * @return
     */
    @Operation(summary = "查询触发规则详情")
    @GetMapping("/{id}")
    public ResponseEntity<AgentTriggerEntity> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(triggerService.getById(id));
    }

    /**
     * 新建触发规则
     * @param entity
     * @return
     */
    @Operation(summary = "新建触发规则")
    @PostMapping
    public ResponseEntity<AgentTriggerEntity> create(@RequestBody AgentTriggerEntity entity) {
        return ResponseEntity.ok(triggerService.create(entity));
    }

    /**
     * 更新触发规则
     * @param entity
     * @return
     */
    @Operation(summary = "更新触发规则")
    @PutMapping
    public ResponseEntity<Void> update(@RequestBody AgentTriggerEntity entity) {
        triggerService.update(entity);
        return ResponseEntity.ok().build();
    }

    /**
     * 启停触发规则
     * @param id
     * @param enabled
     * @return
     */
    @Operation(summary = "启停触发规则")
    @PutMapping("/{id}/enabled/{enabled}")
    public ResponseEntity<Void> changeEnabled(@PathVariable("id") Long id,
                                              @PathVariable("enabled") boolean enabled) {
        triggerService.changeEnabled(id, enabled);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除触发规则
     * @param id
     * @return
     */
    @Operation(summary = "删除触发规则")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        triggerService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 手动试跑触发规则
     * @param id
     * @param payload 试跑载荷(可选)
     * @return
     */
    @Operation(summary = "手动试跑触发规则")
    @PostMapping("/{id}/test-fire")
    public ResponseEntity<AgentTriggerFireResult> testFire(@PathVariable("id") Long id,
                                                           @RequestBody(required = false) String payload) {
        AgentTriggerEntity trigger = triggerService.getById(id);
        if (trigger == null) {
            return ResponseEntity.notFound().build();
        }
        String body = payload == null || payload.isBlank() ? "手动试跑" : payload;
        return ResponseEntity.ok(triggerService.fire(trigger.getTriggerCode(), null, body, "MANUAL"));
    }

    /**
     * 分页查询触发历史
     * @param id
     * @param page
     * @param size
     * @return
     */
    @Operation(summary = "分页查询触发历史")
    @GetMapping("/{id}/logs")
    public ResponseEntity<Map<String, Object>> logs(@PathVariable("id") Long id,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        int safePage = page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        long total = logRepository.countByTrigger(id);
        List<AgentTriggerLogEntity> records = total > 0
                ? logRepository.findByTrigger(id, (safePage - 1) * safeSize, safeSize)
                : List.of();
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("records", records);
        return ResponseEntity.ok(result);
    }
}
