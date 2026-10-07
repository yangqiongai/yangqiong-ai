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
package com.yangqiongai.ai.workflow.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的工作流状态存储
 * @author yangqiong
 */
public class RedisWorkflowStateStore implements WorkflowStateStore {

    private static final Logger log = LoggerFactory.getLogger(RedisWorkflowStateStore.class);

    private static final String KEY_PREFIX = "workflow:state:";

    private static final String INDEX_PREFIX = "workflow:index:";

    private static final String PENDING_INDEX_PREFIX = "workflow:pending:";

    private static final long TTL_HOURS = 48;

    private static final long INDEX_TTL_HOURS = 50;

    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;

    public RedisWorkflowStateStore(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存工作流状态
     * @param state
     */
    @Override
    public void save(WorkflowState state) {
        if (state == null || state.getInstanceId() == null) {
            return;
        }
        String key = KEY_PREFIX + state.getInstanceId();
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json, TTL_HOURS, TimeUnit.HOURS);
            // 维护定义名称索引
            if (state.getDefinitionName() != null) {
                String indexKey = INDEX_PREFIX + state.getDefinitionName();
                redisTemplate.opsForSet().add(indexKey, state.getInstanceId());
                redisTemplate.expire(indexKey, INDEX_TTL_HOURS, TimeUnit.HOURS);
            }
            // 维护审批请求ID索引（用于审批完成后恢复工作流）
            if (state.getPendingRequestId() != null) {
                String pendingKey = PENDING_INDEX_PREFIX + state.getPendingRequestId();
                redisTemplate.opsForValue().set(pendingKey, state.getInstanceId(), INDEX_TTL_HOURS, TimeUnit.HOURS);
            }
        } catch (JsonProcessingException e) {
            log.warn("工作流状态序列化失败: instanceId={}", state.getInstanceId(), e);
        }
    }

    /**
     * 加载工作流状态
     * @param instanceId
     * @return
     */
    @Override
    public WorkflowState load(String instanceId) {
        if (instanceId == null) {
            return null;
        }
        String key = KEY_PREFIX + instanceId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            WorkflowState state = objectMapper.readValue(json, WorkflowState.class);
            // 反序列化后重新包装为ConcurrentHashMap，确保并发安全
            if (state.getNodeStates() != null) {
                state.setNodeStates(new ConcurrentHashMap<>(state.getNodeStates()));
            }
            if (state.getVariables() != null) {
                state.setVariables(new ConcurrentHashMap<>(state.getVariables()));
            }
            return state;
        } catch (JsonProcessingException e) {
            log.warn("工作流状态反序列化失败: instanceId={}", instanceId, e);
            return null;
        }
    }

    /**
     * 删除工作流状态
     * @param instanceId
     */
    @Override
    public void delete(String instanceId) {
        if (instanceId == null) {
            return;
        }
        // 先加载以清理索引
        WorkflowState state = load(instanceId);
        String key = KEY_PREFIX + instanceId;
        redisTemplate.delete(key);
        if (state != null && state.getDefinitionName() != null) {
            String indexKey = INDEX_PREFIX + state.getDefinitionName();
            redisTemplate.opsForSet().remove(indexKey, instanceId);
        }
        // 清理审批暂停索引
        if (state != null && state.getPendingRequestId() != null) {
            String pendingIndexKey = PENDING_INDEX_PREFIX + state.getPendingRequestId();
            redisTemplate.delete(pendingIndexKey);
        }
    }

    /**
     * 根据定义名称查询工作流状态列表
     * @param definitionName
     * @return
     */
    @Override
    public List<WorkflowState> queryByDefinitionName(String definitionName) {
        if (definitionName == null) {
            return Collections.emptyList();
        }
        String indexKey = INDEX_PREFIX + definitionName;
        Set<String> instanceIds = redisTemplate.opsForSet().members(indexKey);
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkflowState> result = new ArrayList<>();
        for (String instanceId : instanceIds) {
            WorkflowState state = load(instanceId);
            if (state != null) {
                result.add(state);
            } else {
                redisTemplate.opsForSet().remove(indexKey, instanceId);
            }
        }
        return result;
    }

    /**
     * 根据审批请求ID查找暂停的工作流状态
     * @param pendingRequestId
     * @return
     */
    @Override
    public WorkflowState findByPendingRequestId(String pendingRequestId) {
        if (pendingRequestId == null) {
            return null;
        }
        String pendingKey = PENDING_INDEX_PREFIX + pendingRequestId;
        String instanceId = redisTemplate.opsForValue().get(pendingKey);
        if (instanceId == null) {
            return null;
        }
        return load(instanceId);
    }

    /**
     * 查询全部运行中的工作流状态（僵尸实例回收扫描用）
     * <p>状态键带有TTL自动过期，规模可控，直接扫描状态键后按状态过滤</p>
     * @return
     */
    @Override
    public List<WorkflowState> listRunning() {
        return listByStatus(WorkflowState::isRunning);
    }

    /**
     * 查询全部暂停中的工作流状态（服务重启后时间控制恢复扫描用）
     * @return
     */
    @Override
    public List<WorkflowState> listPaused() {
        return listByStatus(WorkflowState::isPaused);
    }

    /**
     * 扫描全部状态键并按条件过滤
     * @param statusPredicate
     * @return
     */
    private List<WorkflowState> listByStatus(java.util.function.Predicate<WorkflowState> statusPredicate) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkflowState> result = new ArrayList<>();
        for (String key : keys) {
            WorkflowState state = load(key.substring(KEY_PREFIX.length()));
            if (state != null && statusPredicate.test(state)) {
                result.add(state);
            }
        }
        return result;
    }
}
