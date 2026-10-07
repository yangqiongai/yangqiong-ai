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
package com.yangqiongai.ai.data.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.data.workflow.entity.WorkflowDefinitionEntity;
import com.yangqiongai.ai.data.workflow.mapper.WorkflowDefinitionMapper;
import com.yangqiongai.ai.workflow.api.dto.WorkflowDefinitionSaveRequest;
import com.yangqiongai.ai.workflow.api.dto.WorkflowDefinitionUpdateRequest;
import com.yangqiongai.ai.workflow.event.WorkflowDefinitionDeletedEvent;
import com.yangqiongai.ai.workflow.event.WorkflowDefinitionSavedEvent;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流定义管理
 * @author yangqiong
 */
@Service
public class WorkflowDefinitionService extends ServiceImpl<WorkflowDefinitionMapper, WorkflowDefinitionEntity> {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    public WorkflowDefinitionService(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 根据名称查询最新启用版本的工作流定义
     * @param definitionName
     * @return
     */
    public WorkflowDefinition loadByName(String definitionName) {
        WorkflowDefinitionEntity entity = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .eq(WorkflowDefinitionEntity::getStatus, 1)
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        return entity.toModel(objectMapper);
    }

    /**
     * 根据名称和版本查询工作流定义
     * @param definitionName
     * @param version
     * @return
     */
    public WorkflowDefinition loadByNameAndVersion(String definitionName, int version) {
        WorkflowDefinitionEntity entity = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .eq(WorkflowDefinitionEntity::getVersion, version));
        if (entity == null) {
            return null;
        }
        return entity.toModel(objectMapper);
    }

    /**
     * 保存工作流定义（从模型对象）
     * @param definition
     * @return
     */
    public Long saveDefinition(WorkflowDefinition definition) {
        WorkflowDefinitionEntity entity = WorkflowDefinitionEntity.fromModel(definition, objectMapper);
        WorkflowDefinitionEntity existing = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definition.getName())
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1"));
        if (existing != null) {
            entity.setVersion(existing.getVersion() + 1);
        }
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        save(entity);
        log.info("保存工作流定义: name={}, version={}", entity.getDefinitionName(), entity.getVersion());
        eventPublisher.publishEvent(new WorkflowDefinitionSavedEvent(entity.getDefinitionName(), entity.getDefinitionJson()));
        return entity.getId();
    }

    /**
     * 保存工作流定义（从DTO请求）
     * @param request
     * @return
     */
    public Long saveDefinition(WorkflowDefinitionSaveRequest request) {
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setDefinitionName(request.getDefinitionName());
        entity.setDisplayName(request.getDisplayName());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        entity.setRemark(request.getRemark());
        entity.setStatus(1);

        if (request.getDefinition() != null) {
            try {
                entity.setDefinitionJson(objectMapper.writeValueAsString(request.getDefinition()));
            } catch (Exception e) {
                throw new RuntimeException("工作流定义序列化失败", e);
            }
        } else {
            // 新建流程允许先创建空定义进设计器编排，默认写入空节点/边结构
            entity.setDefinitionJson("{\"name\":\"\",\"description\":\"\",\"nodes\":[],\"edges\":[]}");
        }

        WorkflowDefinitionEntity existing = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, request.getDefinitionName())
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1"));
        if (existing != null) {
            entity.setVersion(existing.getVersion() + 1);
        }
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        save(entity);
        log.info("保存工作流定义: name={}, version={}", entity.getDefinitionName(), entity.getVersion());
        eventPublisher.publishEvent(new WorkflowDefinitionSavedEvent(entity.getDefinitionName(), entity.getDefinitionJson()));
        return entity.getId();
    }

    /**
     * 更新工作流定义（从DTO请求，以path name为准，创建新版本）
     * @param definitionName
     * @param request
     * @return
     */
    public boolean updateDefinition(String definitionName, WorkflowDefinitionUpdateRequest request) {
        WorkflowDefinitionEntity existing = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1"));
        if (existing == null) {
            return false;
        }

        WorkflowDefinitionEntity newVersion = new WorkflowDefinitionEntity();
        newVersion.setDefinitionName(definitionName);
        newVersion.setVersion(existing.getVersion() + 1);
        newVersion.setStatus(existing.getStatus());
        newVersion.setCreateTime(LocalDateTime.now());
        newVersion.setUpdateTime(LocalDateTime.now());

        newVersion.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : existing.getDisplayName());
        newVersion.setDescription(request.getDescription() != null ? request.getDescription() : existing.getDescription());
        newVersion.setCategory(request.getCategory() != null ? request.getCategory() : existing.getCategory());
        newVersion.setRemark(request.getRemark() != null ? request.getRemark() : existing.getRemark());

        if (request.getDefinition() != null) {
            try {
                newVersion.setDefinitionJson(objectMapper.writeValueAsString(request.getDefinition()));
            } catch (Exception e) {
                throw new RuntimeException("工作流定义序列化失败", e);
            }
        } else {
            newVersion.setDefinitionJson(existing.getDefinitionJson());
        }

        save(newVersion);
        log.info("更新工作流定义(新版本): name={}, version={}", definitionName, newVersion.getVersion());
        eventPublisher.publishEvent(new WorkflowDefinitionSavedEvent(definitionName, newVersion.getDefinitionJson()));
        return true;
    }

    /**
     * 更新工作流定义（从模型对象，兼容旧接口）
     * @param definitionName
     * @param definition
     * @return
     */
    public boolean updateDefinition(String definitionName, WorkflowDefinition definition) {
        WorkflowDefinitionEntity entity = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1"));
        if (entity == null) {
            return false;
        }
        try {
            entity.setDefinitionJson(objectMapper.writeValueAsString(definition));
            entity.setDisplayName(definition.getName());
            entity.setDescription(definition.getDescription());
            entity.setUpdateTime(LocalDateTime.now());
            return updateById(entity);
        } catch (Exception e) {
            throw new RuntimeException("工作流定义序列化失败", e);
        }
    }

    /**
     * 分页查询工作流定义列表
     * @param pageNum
     * @param pageSize
     * @param category
     * @param status
     * @return
     */
    public Page<WorkflowDefinitionEntity> pageDefinitions(int pageNum, int pageSize, String category, Integer status) {
        LambdaQueryWrapper<WorkflowDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(WorkflowDefinitionEntity::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(WorkflowDefinitionEntity::getStatus, status);
        }
        wrapper.orderByDesc(WorkflowDefinitionEntity::getUpdateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 查询所有启用的工作流定义列表（只返回最新版本）
     * @return
     */
    public List<WorkflowDefinitionEntity> listLatestEnabled() {
        return list(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getStatus, 1)
                .orderByDesc(WorkflowDefinitionEntity::getUpdateTime));
    }

    /**
     * 切换工作流定义状态
     * @param definitionName
     * @return
     */
    public boolean toggleStatus(String definitionName) {
        WorkflowDefinitionEntity entity = getOne(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1"));
        if (entity == null) {
            return false;
        }
        entity.setStatus(entity.getStatus() == 1 ? 0 : 1);
        entity.setUpdateTime(LocalDateTime.now());
        return updateById(entity);
    }

    /**
     * 删除工作流定义（按名称删除所有版本）
     * @param definitionName
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByName(String definitionName) {
        boolean removed = remove(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
                .eq(WorkflowDefinitionEntity::getDefinitionName, definitionName));
        // 发布删除事件，企业版监听后同步清理版本表，避免删除后仍能从版本表读到定义
        if (removed) {
            eventPublisher.publishEvent(new WorkflowDefinitionDeletedEvent(definitionName));
        }
        return removed;
    }
}
