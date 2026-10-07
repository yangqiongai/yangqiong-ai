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

import com.yangqiongai.ai.agent.tool.model.ToolConfigCategory;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigCategoryRepository;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
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
 * 工具配置分类管理
 * @author yangqiong
 */
@Service
public class ToolConfigCategoryService {

    private final ToolConfigCategoryRepository categoryRepository;

    private final ToolConfigRepository toolConfigRepository;

    public ToolConfigCategoryService(ToolConfigCategoryRepository categoryRepository,
                                     ToolConfigRepository toolConfigRepository) {
        this.categoryRepository = categoryRepository;
        this.toolConfigRepository = toolConfigRepository;
    }

    /**
     * 查询工具配置分类树（各节点含挂载工具数）
     * @return
     */
    public Map<String, Object> tree() {
        List<CategoryNode> nodes = buildCategoryTree(categoryRepository.findAll());
        long ungroupedCount = toolConfigRepository.listAll().stream()
                .filter(info -> info.getCategory() == null || info.getCategory().isBlank())
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
    public ToolConfigCategory create(ToolConfigCategory category) {
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
            List<ToolConfigCategory> all = categoryRepository.findAll();
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
    public ToolConfigCategory update(Long id, ToolConfigCategory category) {
        if (category == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        List<ToolConfigCategory> all = categoryRepository.findAll();
        ToolConfigCategory exist = all.stream()
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
     * 删除分类节点（存在子节点或挂载工具时禁止删除）
     * @param id
     */
    public void delete(Long id) {
        List<ToolConfigCategory> all = categoryRepository.findAll();
        ToolConfigCategory exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "分类节点不存在");
        }
        if (all.stream().anyMatch(item -> id.equals(item.getParentId()))) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "存在子分类节点，请先删除子节点");
        }
        long toolCount = toolConfigRepository.listAll().stream()
                .filter(info -> exist.getCode().equals(info.getCategory()))
                .count();
        if (toolCount > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "该分类下挂载了" + toolCount + "个工具，请先调整工具归属");
        }
        categoryRepository.deleteById(id);
    }

    /**
     * 解析分类子树（含自身）的全部节点编码，__ungrouped__返回虚拟编码自身
     * @param categoryCode
     * @return
     */
    public Set<String> resolveSubtreeCodes(String categoryCode) {
        if (ToolConfigCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
            return Set.of(ToolConfigCategoryRepository.UNGROUPED_CODE);
        }
        return categoryRepository.findSubtreeCodes(categoryCode);
    }

    /**
     * 校验工具归属的分类编码必须存在于分类树
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
    private Set<Long> collectDescendantIds(List<ToolConfigCategory> all, Long rootId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            for (ToolConfigCategory item : all) {
                if (current.equals(item.getParentId()) && result.add(item.getId())) {
                    stack.push(item.getId());
                }
            }
        }
        return result;
    }

    /**
     * 组装分类树（各节点含挂载工具数）
     * @param categories
     * @return
     */
    private List<CategoryNode> buildCategoryTree(List<ToolConfigCategory> categories) {
        Map<String, Long> countByCode = toolConfigRepository.listAll().stream()
                .filter(info -> info.getCategory() != null && !info.getCategory().isBlank())
                .collect(Collectors.groupingBy(ToolConfigInfo::getCategory, Collectors.counting()));
        Map<Long, CategoryNode> nodeById = new LinkedHashMap<>();
        for (ToolConfigCategory category : categories) {
            CategoryNode node = new CategoryNode();
            node.setId(category.getId());
            node.setCode(category.getCode());
            node.setName(category.getName());
            node.setParentId(category.getParentId());
            node.setSortNum(category.getSortNum());
            node.setToolCount(countByCode.getOrDefault(category.getCode(), 0L));
            node.setChildren(new ArrayList<>());
            nodeById.put(category.getId(), node);
        }
        List<CategoryNode> roots = new ArrayList<>();
        for (ToolConfigCategory category : categories) {
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
     * 工具配置分类树节点
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
         * 挂载工具数
         */
        private Long toolCount;

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

        public Long getToolCount() {
            return toolCount;
        }

        public void setToolCount(Long toolCount) {
            this.toolCount = toolCount;
        }

        public List<CategoryNode> getChildren() {
            return children;
        }

        public void setChildren(List<CategoryNode> children) {
            this.children = children;
        }
    }
}
