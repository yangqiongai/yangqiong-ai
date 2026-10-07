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

import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于内存的工作流状态存储
 * @author yangqiong
 */
public class InMemoryWorkflowStateStore implements WorkflowStateStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWorkflowStateStore.class);

    private static final int MAX_CAPACITY = 1000;

    private final Map<String, WorkflowState> store = Collections.synchronizedMap(
            new LinkedHashMap<String, WorkflowState>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, WorkflowState> eldest) {
                    if (size() > MAX_CAPACITY) {
                        log.warn("工作流状态存储已达上限，移除最旧条目: instanceId={}", eldest.getKey());
                        return true;
                    }
                    return false;
                }
            }
    );

    /**
     * 保存工作流状态
     * @param state
     */
    @Override
    public void save(WorkflowState state) {
        if (state == null || state.getInstanceId() == null) {
            return;
        }
        store.put(state.getInstanceId(), state);
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
        return store.get(instanceId);
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
        store.remove(instanceId);
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
        return store.values().stream()
                .filter(s -> definitionName.equals(s.getDefinitionName()))
                .collect(Collectors.toList());
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
        return store.values().stream()
                .filter(s -> pendingRequestId.equals(s.getPendingRequestId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查询全部运行中的工作流状态（僵尸实例回收扫描用）
     * @return
     */
    @Override
    public List<WorkflowState> listRunning() {
        synchronized (store) {
            return store.values().stream()
                    .filter(WorkflowState::isRunning)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 查询全部暂停中的工作流状态（服务重启后时间控制恢复扫描用）
     * @return
     */
    @Override
    public List<WorkflowState> listPaused() {
        synchronized (store) {
            return store.values().stream()
                    .filter(WorkflowState::isPaused)
                    .collect(Collectors.toList());
        }
    }
}
