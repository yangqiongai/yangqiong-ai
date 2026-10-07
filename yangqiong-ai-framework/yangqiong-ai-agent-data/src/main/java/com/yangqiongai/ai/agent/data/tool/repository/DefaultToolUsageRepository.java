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
package com.yangqiongai.ai.agent.data.tool.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yangqiongai.ai.agent.data.tool.entity.ToolUsageEntity;
import com.yangqiongai.ai.agent.data.tool.mapper.ToolUsageMapper;
import com.yangqiongai.ai.agent.tool.model.ToolUsageInfo;
import com.yangqiongai.ai.agent.tool.repository.ToolUsageRepository;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工具使用量仓库默认实现
 * @author yangqiong
 */
public class DefaultToolUsageRepository extends ServiceImpl<ToolUsageMapper, ToolUsageEntity> implements ToolUsageRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolUsageRepository.class);

    /**
     * 排行最大返回条数，防止恶意大分页
     */
    private static final int MAX_LIMIT = 100;

    /**
     * 描述入库最大长度，防止超长文本
     */
    private static final int MAX_DESC_LENGTH = 1000;

    @Override
    public void bumpCall(String toolCode, String toolName, String toolDesc, String scopeId) {
        try {
            ToolUsageEntity entity = getOrCreate(toolCode, toolName, toolDesc, scopeId);
            entity.setCallCount(entity.getCallCount() + 1);
            entity.setLastCalledAt(LocalDateTime.now());
            // 计数更新绑定工具行自身作用域，避免引擎线程被兜底域过滤拦截
            runWithScope(scopeId, () -> updateById(entity));
        } catch (Exception e) {
            // 统计失败不影响工具调用主流程
            log.warn("工具调用计数失败: toolCode={}", toolCode, e);
        }
    }

    @Override
    public void bumpResult(String toolCode, boolean success, String scopeId) {
        try {
            ToolUsageEntity entity = getOrCreate(toolCode, null, null, scopeId);
            if (success) {
                entity.setSuccessCount(entity.getSuccessCount() + 1);
            } else {
                entity.setFailCount(entity.getFailCount() + 1);
            }
            // 计数更新绑定工具行自身作用域，避免引擎线程被兜底域过滤拦截
            runWithScope(scopeId, () -> updateById(entity));
        } catch (Exception e) {
            // 统计失败不影响工具调用主流程
            log.warn("工具结果计数失败: toolCode={}", toolCode, e);
        }
    }

    /**
     * 以指定作用域执行数据库操作，缺失时保持当前上下文
     * @param scopeId
     * @param action
     * @return
     */
    private <T> T runWithScope(String scopeId, java.util.function.Supplier<T> action) {
        if (scopeId == null || scopeId.isBlank() || scopeId.equals(ScopeContext.getScopeId())) {
            return action.get();
        }
        ScopeContext.setScopeId(scopeId);
        try {
            return action.get();
        } finally {
            ScopeContext.clear();
        }
    }

    @Override
    public ToolUsageInfo getByToolCode(String toolCode) {
        ToolUsageEntity entity = getByToolCodeEntity(toolCode);
        return entity != null ? toInfo(entity) : null;
    }

    @Override
    public List<ToolUsageInfo> listTopByCallCount(int limit) {
        return list(new LambdaQueryWrapper<ToolUsageEntity>()
                .orderByDesc(ToolUsageEntity::getCallCount)
                .orderByDesc(ToolUsageEntity::getSuccessCount)
                .last("LIMIT " + Math.min(Math.max(limit, 1), MAX_LIMIT))).stream().map(this::toInfo).toList();
    }

    private ToolUsageEntity getByToolCodeEntity(String toolCode) {
        return getOne(new LambdaQueryWrapper<ToolUsageEntity>()
                .eq(ToolUsageEntity::getToolCode, toolCode));
    }

    private ToolUsageEntity getOrCreate(String toolCode, String toolName, String toolDesc, String scopeId) {
        return runWithScope(scopeId, () -> {
            ToolUsageEntity entity = getOne(new LambdaQueryWrapper<ToolUsageEntity>()
                    .eq(ToolUsageEntity::getToolCode, toolCode)
                    .last("LIMIT 1"));
            if (entity != null) {
                // 元数据为空时尽力回填
                if (entity.getToolName() == null && toolName != null) {
                    entity.setToolName(toolName);
                }
                if (entity.getToolDesc() == null && toolDesc != null) {
                    entity.setToolDesc(truncateDesc(toolDesc));
                }
                return entity;
            }
            entity = new ToolUsageEntity();
            entity.setToolCode(toolCode);
            entity.setToolName(toolName);
            entity.setToolDesc(truncateDesc(toolDesc));
            entity.setScopeId(scopeId);
            entity.setCallCount(0L);
            entity.setSuccessCount(0L);
            entity.setFailCount(0L);
            save(entity);
            return entity;
        });
    }

    private String truncateDesc(String toolDesc) {
        if (toolDesc == null) {
            return null;
        }
        return toolDesc.length() > MAX_DESC_LENGTH ? toolDesc.substring(0, MAX_DESC_LENGTH) : toolDesc;
    }

    private ToolUsageInfo toInfo(ToolUsageEntity entity) {
        ToolUsageInfo info = new ToolUsageInfo();
        info.setToolCode(entity.getToolCode());
        info.setToolName(entity.getToolName());
        info.setToolDesc(entity.getToolDesc());
        info.setScopeId(entity.getScopeId());
        info.setCallCount(entity.getCallCount());
        info.setSuccessCount(entity.getSuccessCount());
        info.setFailCount(entity.getFailCount());
        info.setLastCalledAt(entity.getLastCalledAt());
        return info;
    }
}
