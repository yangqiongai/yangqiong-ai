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
package com.yangqiongai.ai.agent.data.skill.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yangqiongai.ai.agent.data.skill.entity.SkillConfigEntity;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillConfigMapper;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillVersionMapper;
import com.yangqiongai.ai.agent.data.skill.entity.SkillVersionEntity;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.SkillDefinitionPage;
import com.yangqiongai.ai.agent.skill.model.SkillVersionInfo;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;
import com.yangqiongai.ai.agent.skill.repository.SkillCategoryRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 技能配置仓库默认实现
 * @author yangqiong
 */
public class DefaultSkillConfigRepository extends ServiceImpl<SkillConfigMapper, SkillConfigEntity> implements SkillConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillConfigRepository.class);

    /**
     * 缓存过期时间（分钟），默认5分钟
     */
    @Value("${ai.cache.skill-config.ttl-minutes:5}")
    private long cacheTtlMinutes;

    /**
     * 缓存最大条目数，默认500
     */
    @Value("${ai.cache.skill-config.max-size:500}")
    private long cacheMaxSize;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillVersionMapper skillVersionMapper;

    @Autowired
    private SkillCategoryRepository skillCategoryRepository;

    /**
     * Caffeine本地缓存，key=skillId，value=SkillDefinition
     */
    private Cache<String, SkillDefinition> cache;

    /**
     * 启用技能列表缓存
     */
    private Cache<String, List<SkillDefinition>> enabledListCache;

    /**
     * 启用技能列表缓存key
     */
    private static final String ENABLED_LIST_KEY = "__enabled_list__";

    /**
     * 初始化Caffeine缓存
     */
    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .build();
        this.enabledListCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(10)
                .build();
    }

    @Override
    public SkillDefinition getBySkillId(String skillId) {
        if (skillId == null) {
            return null;
        }
        // 先从缓存读取，若未命中则从数据库加载并回填缓存
        return cache.get(skillId, key -> {
            SkillConfigEntity entity = getOne(new LambdaQueryWrapper<SkillConfigEntity>()
                    .eq(SkillConfigEntity::getSkillId, key));
            return entity != null ? toDefinition(entity) : null;
        });
    }

    @Override
    public List<SkillDefinition> listEnabled() {
        return enabledListCache.get(ENABLED_LIST_KEY, key -> {
            List<SkillConfigEntity> list = list(new LambdaQueryWrapper<SkillConfigEntity>()
                    .eq(SkillConfigEntity::getSkillStatus, 1)
                    .orderByAsc(SkillConfigEntity::getId));
            List<SkillDefinition> result = list.stream().map(this::toDefinition).toList();
            // 回填单个技能缓存
            for (SkillDefinition def : result) {
                cache.put(def.getSkillId(), def);
            }
            return result;
        });
    }

    @Override
    public List<SkillDefinition> listAll() {
        return list(new LambdaQueryWrapper<SkillConfigEntity>()
                .orderByAsc(SkillConfigEntity::getId))
                .stream()
                .map(this::toDefinition)
                .toList();
    }

    /**
     * 分页查询技能配置(含禁用，实时查库不走缓存，用于管理端列表)
     * @param keyword
     * @param categoryCode 分类节点编码（限制返回范围为该节点子树，__ungrouped__表示未分类）
     * @param pageNum
     * @param pageSize
     * @return
     */
    @Override
    public SkillDefinitionPage pageSkills(String keyword, String categoryCode, int pageNum, int pageSize) {
        LambdaQueryWrapper<SkillConfigEntity> wrapper = new LambdaQueryWrapper<SkillConfigEntity>()
                .orderByAsc(SkillConfigEntity::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SkillConfigEntity::getSkillId, kw)
                    .or().like(SkillConfigEntity::getSkillName, kw));
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            if (SkillCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
                // 未分类：分类列为空或空串
                wrapper.and(w -> w.isNull(SkillConfigEntity::getCategory)
                        .or().eq(SkillConfigEntity::getCategory, ""));
            } else {
                Set<String> subtreeCodes = skillCategoryRepository.findSubtreeCodes(categoryCode.trim());
                // 分类不存在时子树为空集，直接返回空页
                if (subtreeCodes.isEmpty()) {
                    return new SkillDefinitionPage(List.of(), 0);
                }
                wrapper.in(SkillConfigEntity::getCategory, subtreeCodes);
            }
        }
        Page<SkillConfigEntity> result = page(new Page<>(pageNum, pageSize), wrapper);
        List<SkillDefinition> records = result.getRecords().stream().map(this::toDefinition).toList();
        return new SkillDefinitionPage(records, result.getTotal());
    }

    @Override
    public List<SkillDefinition> listByTrustLevel(TrustLevel trustLevel) {
        List<SkillConfigEntity> all = list(new LambdaQueryWrapper<SkillConfigEntity>()
                .eq(SkillConfigEntity::getSkillStatus, 1)
                .orderByAsc(SkillConfigEntity::getId));
        return all.stream()
                .map(entity -> {
                    SkillDefinition def = toDefinition(entity);
                    cache.put(def.getSkillId(), def);
                    return def;
                })
                .filter(def -> def.getTrustLevel() == trustLevel)
                .toList();
    }

    @Override
    public void saveSkill(SkillDefinition definition, String operator, String remark) {
        SkillConfigEntity existing = getOne(new LambdaQueryWrapper<SkillConfigEntity>()
                .eq(SkillConfigEntity::getSkillId, definition.getSkillId()));
        if (existing != null) {
            // 更新前保存版本快照，变更说明为空时兜底默认文案
            String changeLog = (remark != null && !remark.isBlank()) ? remark : "更新前自动保存";
            saveVersion(existing.getSkillId(), existing.getSkillVersion(),
                    existing.getSkillContent(), changeLog, operator);
            SkillConfigEntity updated = toEntity(definition);
            updated.setId(existing.getId());
            int newVersion = existing.getSkillVersion() != null ? existing.getSkillVersion() + 1 : 2;
            updated.setSkillVersion(newVersion);
            updated.setUpdateUser(operator);
            updated.setUpdateTime(LocalDateTime.now());
            updateById(updated);
            // 回写新版本号，保持定义对象与库内版本一致
            definition.setSkillVersion(newVersion);
            cache.put(definition.getSkillId(), definition);
        } else {
            SkillConfigEntity entity = toEntity(definition);
            entity.setSkillStatus(1);
            entity.setSkillVersion(1);
            entity.setCreateUser(operator);
            entity.setCreateTime(LocalDateTime.now());
            save(entity);
            definition.setSkillVersion(1);
            cache.put(definition.getSkillId(), definition);
        }
        // 启用列表缓存失效，下次查询重新加载
        enabledListCache.invalidate(ENABLED_LIST_KEY);
    }

    @Override
    public List<SkillVersionInfo> listVersions(String skillId, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.max(pageSize, 1);
        List<SkillVersionEntity> rows = skillVersionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .orderByDesc(SkillVersionEntity::getVersion)
                .last("LIMIT " + (safePage - 1) * safeSize + ", " + safeSize));
        return rows.stream().map(this::toVersionInfo).toList();
    }

    @Override
    public long countVersions(String skillId) {
        Long count = skillVersionMapper.selectCount(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillId, skillId));
        return count != null ? count : 0L;
    }

    @Override
    public SkillVersionInfo getVersion(String skillId, int version) {
        // 同版本存在重复行时取id最大者，与唯一索引去重口径一致
        List<SkillVersionEntity> rows = skillVersionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getVersion, version)
                .orderByDesc(SkillVersionEntity::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : toVersionInfo(rows.get(0));
    }

    @Override
    public void saveEvaluation(String skillId, int version, String skillContent, java.math.BigDecimal qualityScore,
                               String evalDimensionsJson, String evalModel) {
        SkillVersionEntity row = skillVersionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getVersion, version)
                .orderByDesc(SkillVersionEntity::getId)
                .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (row == null) {
            // 当前版本无快照行时自动补建（写入当前内容，评测完成后该行即承载评分）
            row = new SkillVersionEntity();
            row.setSkillId(skillId);
            row.setVersion(version);
            row.setSkillContent(skillContent);
            row.setFingerprint(computeFingerprint(skillContent));
            row.setCreateTime(LocalDateTime.now());
            skillVersionMapper.insert(row);
        }
        row.setQualityScore(qualityScore);
        row.setEvalDimensions(evalDimensionsJson);
        row.setEvalModel(evalModel);
        row.setEvaluatedTime(LocalDateTime.now());
        skillVersionMapper.updateById(row);
        cache.invalidate(skillId);
    }

    @Override
    public java.util.Map<String, SkillVersionInfo> listLatestEvaluations() {
        // 技能量级有限（数十级），直接取评分相关列全量后在内存按版本归组取最新
        List<SkillVersionEntity> rows = skillVersionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                .select(SkillVersionEntity::getSkillId, SkillVersionEntity::getVersion,
                        SkillVersionEntity::getQualityScore, SkillVersionEntity::getEvaluatedTime));
        java.util.Map<String, SkillVersionInfo> latest = new java.util.HashMap<>();
        for (SkillVersionEntity row : rows) {
            if (row.getSkillId() == null || row.getVersion() == null) {
                continue;
            }
            SkillVersionInfo info = toVersionInfo(row);
            latest.merge(row.getSkillId(), info, (oldInfo, newInfo) ->
                    newInfo.getVersion() > oldInfo.getVersion() ? newInfo : oldInfo);
        }
        return latest;
    }

    @Override
    public boolean toggleStatus(String skillId, int status) {
        SkillConfigEntity entity = getOne(new LambdaQueryWrapper<SkillConfigEntity>()
                .eq(SkillConfigEntity::getSkillId, skillId));
        if (entity == null) return false;
        entity.setSkillStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        boolean result = updateById(entity);
        if (result) {
            cache.invalidate(skillId);
            enabledListCache.invalidate(ENABLED_LIST_KEY);
        }
        return result;
    }

    @Override
    public boolean deleteBySkillId(String skillId) {
        SkillConfigEntity entity = getOne(new LambdaQueryWrapper<SkillConfigEntity>()
                .eq(SkillConfigEntity::getSkillId, skillId));
        if (entity == null) return false;
        boolean result = removeById(entity.getId());
        if (result) {
            // 同步清理版本快照，避免孤儿快照干扰同名技能重建
            skillVersionMapper.delete(new LambdaQueryWrapper<SkillVersionEntity>()
                    .eq(SkillVersionEntity::getSkillId, skillId));
            cache.invalidate(skillId);
            enabledListCache.invalidate(ENABLED_LIST_KEY);
        }
        return result;
    }

    @Override
    public boolean updateTrustLevel(String skillId, String trustLevel) {
        SkillConfigEntity entity = getOne(new LambdaQueryWrapper<SkillConfigEntity>()
                .eq(SkillConfigEntity::getSkillId, skillId));
        if (entity == null) return false;
        entity.setTrustLevel(trustLevel);
        entity.setUpdateTime(LocalDateTime.now());
        boolean result = updateById(entity);
        if (result) {
            cache.invalidate(skillId);
            enabledListCache.invalidate(ENABLED_LIST_KEY);
        }
        return result;
    }

    /**
     * 保存技能版本快照
     * @param skillId
     * @param currentVersion
     * @param content
     * @param remark
     * @param operator
     */
    private void saveVersion(String skillId, Integer currentVersion, String content, String remark, String operator) {
        int versionNo = currentVersion != null ? currentVersion : 1;
        // 按(skill_id, version) upsert：评测功能可能已为当前版本创建快照行，直接insert会触发唯一键冲突
        List<SkillVersionEntity> existing = skillVersionMapper.selectList(new LambdaQueryWrapper<SkillVersionEntity>()
                .eq(SkillVersionEntity::getSkillId, skillId)
                .eq(SkillVersionEntity::getVersion, versionNo)
                .orderByDesc(SkillVersionEntity::getId)
                .last("LIMIT 1"));
        if (existing.isEmpty()) {
            SkillVersionEntity version = new SkillVersionEntity();
            version.setSkillId(skillId);
            version.setVersion(versionNo);
            version.setSkillContent(content);
            version.setChangeLog(remark);
            version.setFingerprint(computeFingerprint(content));
            version.setCreateUser(operator);
            version.setCreateTime(LocalDateTime.now());
            skillVersionMapper.insert(version);
        } else {
            // 已存在则覆盖更新旧内容快照（保留原创建人/创建时间，更新快照内容与指纹）
            SkillVersionEntity row = existing.get(0);
            row.setSkillContent(content);
            row.setChangeLog(remark);
            row.setFingerprint(computeFingerprint(content));
            row.setUpdateUser(operator);
            row.setUpdateTime(LocalDateTime.now());
            skillVersionMapper.updateById(row);
        }
    }

    /**
     * 版本实体转版本快照
     * @param entity
     * @return
     */
    private SkillVersionInfo toVersionInfo(SkillVersionEntity entity) {
        SkillVersionInfo info = new SkillVersionInfo();
        info.setVersion(entity.getVersion());
        info.setSkillContent(entity.getSkillContent());
        info.setChangeLog(entity.getChangeLog());
        info.setFingerprint(entity.getFingerprint());
        info.setCreateUser(entity.getCreateUser());
        info.setCreateTime(entity.getCreateTime());
        info.setQualityScore(entity.getQualityScore());
        info.setEvalDimensions(entity.getEvalDimensions());
        info.setEvalModel(entity.getEvalModel());
        info.setEvaluatedTime(entity.getEvaluatedTime());
        return info;
    }

    /**
     * 计算技能内容的SHA-256指纹
     * @param content
     * @return 64位十六进制字符串，内容为null时返回空字符串
     */
    private String computeFingerprint(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256算法不可用，无法计算技能指纹", e);
            return "";
        }
    }

    private SkillDefinition toDefinition(SkillConfigEntity entity) {
        SkillDefinition def = new SkillDefinition();
        def.setSkillId(entity.getSkillId());
        def.setSkillName(entity.getSkillName());
        def.setSkillDescription(entity.getSkillDescription());
        def.setSkillType(entity.getSkillType());
        def.setSkillContent(entity.getSkillContent());
        def.setBoundTools(parseJsonList(entity.getBoundTools()));
        def.setResources(parseJsonStringMap(entity.getResources()));
        def.setExecution(parseJsonMap(entity.getExecution()));
        def.setDependencies(parseJsonMap(entity.getDependencies()));
        def.setPresetParameters(parseJsonMap(entity.getPresetParameters()));
        def.setConditions(parseJsonMap(entity.getConditions()));
        def.setSkillVersion(entity.getSkillVersion() != null ? entity.getSkillVersion() : 1);
        // 实体层保持String存储，定义层枚举化（空/非法值降级COMMUNITY）
        def.setTrustLevel(TrustLevel.fromString(entity.getTrustLevel()));
        def.setCategory(entity.getCategory());
        return def;
    }

    private SkillConfigEntity toEntity(SkillDefinition def) {
        SkillConfigEntity entity = new SkillConfigEntity();
        entity.setSkillId(def.getSkillId());
        entity.setSkillName(def.getSkillName());
        entity.setSkillDescription(def.getSkillDescription());
        entity.setSkillType(def.getSkillType());
        entity.setSkillContent(def.getSkillContent());
        entity.setBoundTools(toJson(def.getBoundTools()));
        entity.setResources(toJson(def.getResources()));
        entity.setExecution(toJson(def.getExecution()));
        entity.setDependencies(toJson(def.getDependencies()));
        entity.setPresetParameters(toJson(def.getPresetParameters()));
        entity.setConditions(toJson(def.getConditions()));
        entity.setTrustLevel(def.getTrustLevel() != null ? def.getTrustLevel().name() : null);
        entity.setCategory(def.getCategory());
        return entity;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析JSON列表失败: {}", json, e);
            return null;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析JSON对象失败: {}", json, e);
            return null;
        }
    }

    private Map<String, String> parseJsonStringMap(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析资源文件JSON失败: {}", json, e);
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("序列化JSON失败", e);
            return null;
        }
    }
}