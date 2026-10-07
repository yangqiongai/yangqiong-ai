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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigCategoryRepository;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigRepository;
import com.yangqiongai.ai.agent.data.tool.entity.AiToolConfig;
import com.yangqiongai.ai.agent.data.tool.mapper.AiToolConfigMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 工具配置仓库默认实现
 * @author yangqiong
 */
public class DefaultToolConfigRepository extends ServiceImpl<AiToolConfigMapper, AiToolConfig> implements ToolConfigRepository {

    /**
     * 缓存过期时间（分钟），默认5分钟
     */
    @Value("${ai.cache.tool-config.ttl-minutes:5}")
    private long cacheTtlMinutes;

    /**
     * 缓存最大条目数，默认2000
     */
    @Value("${ai.cache.tool-config.max-size:2000}")
    private long cacheMaxSize;

    /**
     * Caffeine本地缓存，key=toolCode，value=ToolConfigInfo
     */
    private Cache<String, ToolConfigInfo> cache;

    /**
     * 初始化Caffeine缓存
     */
    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .build();
    }

    @Override
    public List<ToolConfigInfo> listAll() {
        return list(new LambdaQueryWrapper<AiToolConfig>()
                .orderByAsc(AiToolConfig::getToolOrder)
                .orderByAsc(AiToolConfig::getToolCode)).stream().map(this::toInfo).toList();
    }

    @Override
    public List<ToolConfigInfo> listByStatus(Integer status) {
        return list(new LambdaQueryWrapper<AiToolConfig>().eq(AiToolConfig::getToolStatus, status))
                .stream().map(this::toInfo).toList();
    }

    @Override
    public ToolConfigInfo getByToolCode(String toolCode) {
        if (toolCode == null) {
            return null;
        }
        // 先从缓存读取，若未命中则从数据库加载并回填缓存
        return cache.get(toolCode, key -> {
            AiToolConfig entity = getOne(new LambdaQueryWrapper<AiToolConfig>().eq(AiToolConfig::getToolCode, key));
            return entity != null ? toInfo(entity) : null;
        });
    }

    @Override
    public void save(ToolConfigInfo config) {
        AiToolConfig entity = toEntity(config);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        save(entity);
        config.setId(entity.getId());
        // 同步缓存
        cache.put(config.getToolCode(), config);
    }

    @Override
    public void updateById(ToolConfigInfo config) {
        AiToolConfig entity = toEntity(config);
        entity.setUpdateTime(LocalDateTime.now());
        updateById(entity);
        // 同步缓存
        cache.put(config.getToolCode(), config);
    }

    @Override
    public boolean toggleStatus(String toolCode) {
        AiToolConfig config = getOne(new LambdaQueryWrapper<AiToolConfig>().eq(AiToolConfig::getToolCode, toolCode));
        if (config == null) return false;
        config.setToolStatus(config.getToolStatus() == 1 ? 0 : 1);
        config.setUpdateTime(LocalDateTime.now());
        boolean result = updateById(config);
        if (result) {
            // 缓存失效，下次查询重新加载
            cache.invalidate(toolCode);
        }
        return result;
    }

    @Override
    public List<ToolConfigInfo> searchByKeyword(String keyword, String categoryCode, Set<String> subtreeCodes, int offset, int limit) {
        // 空子树表示分类下无任何节点，直接返回空结果
        if (isSubtreeEmpty(categoryCode, subtreeCodes)) {
            return List.of();
        }
        LambdaQueryWrapper<AiToolConfig> wrapper = buildKeywordWrapper(keyword, categoryCode, subtreeCodes);
        wrapper.last("LIMIT " + limit + " OFFSET " + Math.max(offset, 0));
        return list(wrapper).stream().map(this::toInfo).toList();
    }

    @Override
    public long countByKeyword(String keyword, String categoryCode, Set<String> subtreeCodes) {
        // 空子树表示分类下无任何节点，直接返回0
        if (isSubtreeEmpty(categoryCode, subtreeCodes)) {
            return 0L;
        }
        return count(buildKeywordWrapper(keyword, categoryCode, subtreeCodes));
    }

    @Override
    public boolean deleteByToolCode(String toolCode) {
        AiToolConfig config = getOne(new LambdaQueryWrapper<AiToolConfig>().eq(AiToolConfig::getToolCode, toolCode));
        if (config == null) return false;
        boolean result = removeById(config.getId());
        if (result) {
            cache.invalidate(toolCode);
        }
        return result;
    }

    /**
     * 判断分类子树是否为空集（指定分类编码且子树无任何节点时为空）
     * @param categoryCode
     * @param subtreeCodes
     * @return
     */
    private boolean isSubtreeEmpty(String categoryCode, Set<String> subtreeCodes) {
        if (categoryCode == null || categoryCode.isBlank()
                || ToolConfigCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
            return false;
        }
        return subtreeCodes == null || subtreeCodes.isEmpty();
    }

    /**
     * 构建关键字匹配查询条件（匹配toolCode/toolName，支持按分类子树过滤）
     * @param keyword
     * @param categoryCode
     * @param subtreeCodes
     * @return
     */
    private LambdaQueryWrapper<AiToolConfig> buildKeywordWrapper(String keyword, String categoryCode, Set<String> subtreeCodes) {
        LambdaQueryWrapper<AiToolConfig> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AiToolConfig::getToolCode, keyword)
                    .or().like(AiToolConfig::getToolName, keyword));
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            if (ToolConfigCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
                // 未分类：分类编码为空或空串
                wrapper.and(w -> w.isNull(AiToolConfig::getCategory).or().eq(AiToolConfig::getCategory, ""));
            } else if (subtreeCodes != null && !subtreeCodes.isEmpty()) {
                wrapper.in(AiToolConfig::getCategory, subtreeCodes);
            }
        }
        wrapper.orderByAsc(AiToolConfig::getToolOrder).orderByAsc(AiToolConfig::getToolCode);
        return wrapper;
    }

    private ToolConfigInfo toInfo(AiToolConfig entity) {
        ToolConfigInfo info = new ToolConfigInfo();
        info.setId(entity.getId());
        info.setToolCode(entity.getToolCode());
        info.setToolName(entity.getToolName());
        info.setToolDesc(entity.getToolDesc());
        info.setToolClass(entity.getToolClass());
        info.setToolType(entity.getToolType());
        info.setToolCategory(entity.getToolCategory());
        info.setToolOrder(entity.getToolOrder());
        info.setToolConfig(entity.getToolConfig());
        info.setToolStatus(entity.getToolStatus());
        info.setRemark(entity.getRemark());
        info.setCategory(entity.getCategory());
        return info;
    }

    private AiToolConfig toEntity(ToolConfigInfo info) {
        AiToolConfig entity = new AiToolConfig();
        entity.setId(info.getId());
        entity.setToolCode(info.getToolCode());
        entity.setToolName(info.getToolName());
        entity.setToolDesc(info.getToolDesc());
        entity.setToolClass(info.getToolClass());
        entity.setToolType(info.getToolType());
        entity.setToolCategory(info.getToolCategory());
        entity.setToolOrder(info.getToolOrder());
        entity.setToolConfig(info.getToolConfig());
        entity.setToolStatus(info.getToolStatus());
        entity.setRemark(info.getRemark());
        entity.setCategory(info.getCategory());
        return entity;
    }
}