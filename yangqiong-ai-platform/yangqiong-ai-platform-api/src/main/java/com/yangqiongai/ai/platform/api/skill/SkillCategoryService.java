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
package com.yangqiongai.ai.platform.api.skill;

import com.yangqiongai.ai.agent.skill.model.SkillCategory;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.repository.SkillCategoryRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能分类管理
 * @author yangqiong
 */
@Service
public class SkillCategoryService {

    private final SkillCategoryRepository categoryRepository;

    private final SkillConfigRepository skillConfigRepository;

    public SkillCategoryService(SkillCategoryRepository categoryRepository, SkillConfigRepository skillConfigRepository) {
        this.categoryRepository = categoryRepository;
        this.skillConfigRepository = skillConfigRepository;
    }

    /**
     * 查询技能分类树（各节点含挂载技能数，计数口径与技能管理列表一致，仅统计配置表中的自定义技能）
     * @return
     */
    public Map<String, Object> tree() {
        List<CategoryNode> nodes = buildCategoryTree(categoryRepository.findAll());
        long ungroupedCount = skillConfigRepository.listAll().stream()
                .filter(skill -> skill.getCategory() == null || skill.getCategory().isBlank())
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("ungroupedCount", ungroupedCount);
        return result;
    }

    /**
     * 新增分类节点
     * @param category
     * @return
     */
    public SkillCategory create(SkillCategory category) {
        if (category == null || category.getCode() == null || category.getCode().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类编码不能为空");
        }
        if (category.getName() == null || category.getName().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类名称不能为空");
        }
        String code = category.getCode().trim();
        if (!code.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类编码仅支持字母/数字/下划线/中划线，长度不超过64");
        }
        if (categoryRepository.findByCode(code).isPresent()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类编码已存在: " + code);
        }
        if (category.getParentId() != null) {
            List<SkillCategory> all = categoryRepository.findAll();
            if (all.stream().noneMatch(item -> category.getParentId().equals(item.getId()))) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父分类节点不存在");
            }
        }
        category.setCode(code);
        categoryRepository.create(category);
        return category;
    }

    /**
     * 更新分类节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param category
     * @return
     */
    public SkillCategory update(Long id, SkillCategory category) {
        if (category == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        List<SkillCategory> all = categoryRepository.findAll();
        SkillCategory exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类节点不存在");
        }
        if (category.getParentId() != null) {
            if (category.getParentId().equals(id)) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父节点不能选择自身");
            }
            if (collectDescendantIds(all, id).contains(category.getParentId())) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父节点不能选择自身子孙节点");
            }
            if (all.stream().noneMatch(item -> category.getParentId().equals(item.getId()))) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父分类节点不存在");
            }
        }
        exist.setName(category.getName() != null ? category.getName() : exist.getName());
        exist.setParentId(category.getParentId());
        exist.setSortNum(category.getSortNum() != null ? category.getSortNum() : 0);
        categoryRepository.update(exist);
        return exist;
    }

    /**
     * 删除分类节点（存在子节点或挂载技能时禁止删除）
     * @param id
     */
    public void delete(Long id) {
        List<SkillCategory> all = categoryRepository.findAll();
        SkillCategory exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类节点不存在");
        }
        if (all.stream().anyMatch(item -> id.equals(item.getParentId()))) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "存在子分类节点，请先删除子节点");
        }
        long skillCount = skillConfigRepository.listAll().stream()
                .filter(skill -> exist.getCode().equals(skill.getCategory()))
                .count();
        if (skillCount > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "该分类下挂载了" + skillCount + "个技能，请先调整技能归属");
        }
        categoryRepository.deleteById(id);
    }

    /**
     * 解析分类子树（含自身）的全部节点编码，__ungrouped__返回虚拟编码自身
     * @param categoryCode
     * @return
     */
    public Set<String> resolveSubtreeCodes(String categoryCode) {
        if (SkillCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
            return Set.of(SkillCategoryRepository.UNGROUPED_CODE);
        }
        return categoryRepository.findSubtreeCodes(categoryCode);
    }

    /**
     * 校验技能归属的分类编码必须存在于分类树
     * @param category
     */
    public void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        if (categoryRepository.findByCode(category.trim()).isEmpty()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类节点不存在: " + category);
        }
    }

    /**
     * 收集指定节点的全部子孙节点ID
     * @param all
     * @param rootId
     * @return
     */
    private Set<Long> collectDescendantIds(List<SkillCategory> all, Long rootId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            for (SkillCategory item : all) {
                if (current.equals(item.getParentId()) && result.add(item.getId())) {
                    stack.push(item.getId());
                }
            }
        }
        return result;
    }

    /**
     * 组装分类树（各节点含挂载技能数）
     * @param categories
     * @return
     */
    private List<CategoryNode> buildCategoryTree(List<SkillCategory> categories) {
        Map<String, Long> countByCode = skillConfigRepository.listAll().stream()
                .filter(skill -> skill.getCategory() != null && !skill.getCategory().isBlank())
                .collect(Collectors.groupingBy(SkillDefinition::getCategory, Collectors.counting()));
        Map<Long, CategoryNode> nodeById = new LinkedHashMap<>();
        for (SkillCategory category : categories) {
            CategoryNode node = new CategoryNode();
            node.setId(category.getId());
            node.setCode(category.getCode());
            node.setName(category.getName());
            node.setParentId(category.getParentId());
            node.setSortNum(category.getSortNum());
            node.setSkillCount(countByCode.getOrDefault(category.getCode(), 0L));
            node.setChildren(new ArrayList<>());
            nodeById.put(category.getId(), node);
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (SkillCategory category : categories) {
            CategoryNode node = nodeById.get(category.getId());
            CategoryNode parent = category.getParentId() == null ? null : nodeById.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                // 根节点或父节点缺失的孤儿节点统一挂在顶层
                roots.add(node);
            }
        }
        Comparator<CategoryNode> byOrder = Comparator.comparing(CategoryNode::getSortNum,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CategoryNode::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(byOrder);
        for (CategoryNode node : nodeById.values()) {
            node.getChildren().sort(byOrder);
        }
        return roots;
    }

    /**
     * 技能分类树节点
     */
    public static class CategoryNode {

        /**
         * 主键
         */
        private Long id;

        /**
         * 分类编码（创建时自定义，唯一且不可修改）
         */
        private String code;

        /**
         * 分类名称
         */
        private String name;

        /**
         * 父节点ID（NULL为根节点）
         */
        private Long parentId;

        /**
         * 排序号
         */
        private Integer sortNum;

        /**
         * 挂载技能数
         */
        private Long skillCount;

        /**
         * 子节点列表
         */
        private List<CategoryNode> children;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public Integer getSortNum() {
            return sortNum;
        }

        public void setSortNum(Integer sortNum) {
            this.sortNum = sortNum;
        }

        public Long getSkillCount() {
            return skillCount;
        }

        public void setSkillCount(Long skillCount) {
            this.skillCount = skillCount;
        }

        public List<CategoryNode> getChildren() {
            return children;
        }

        public void setChildren(List<CategoryNode> children) {
            this.children = children;
        }
    }
}
