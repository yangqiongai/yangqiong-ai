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
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDirectoryEntity;
import com.yangqiongai.ai.agent.data.registry.repository.AgentDirectoryRepository;
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
 * 智能体目录管理
 * @author yangqiong
 */
@Service
public class AgentDirectoryService {

    private final AgentDirectoryRepository directoryRepository;

    private final AgentRepository agentRepository;

    public AgentDirectoryService(AgentDirectoryRepository directoryRepository,
                                 AgentRepository agentRepository) {
        this.directoryRepository = directoryRepository;
        this.agentRepository = agentRepository;
    }

    /**
     * 查询智能体目录树（各节点含挂载智能体数）
     * @return
     */
    public Map<String, Object> tree() {
        List<DirectoryNode> nodes = buildDirectoryTree(directoryRepository.findAll());
        long ungroupedCount = agentRepository.list().stream()
                .filter(agent -> agent.getDirectoryCode() == null || agent.getDirectoryCode().isBlank())
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("ungroupedCount", ungroupedCount);
        return result;
    }

    /**
     * 新增目录节点
     * @param directory
     * @return
     */
    public AgentDirectoryEntity create(AgentDirectoryEntity directory) {
        if (directory == null || directory.getCode() == null || directory.getCode().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录编码不能为空");
        }
        if (directory.getName() == null || directory.getName().isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录名称不能为空");
        }
        String code = directory.getCode().trim();
        if (!code.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录编码仅支持字母/数字/下划线/中划线，长度不超过64");
        }
        if (AgentDirectoryRepository.UNGROUPED_CODE.equals(code)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录编码为保留编码: " + code);
        }
        if (directoryRepository.findByCode(code).isPresent()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录编码已存在: " + code);
        }
        if (directory.getParentId() != null) {
            List<AgentDirectoryEntity> all = directoryRepository.findAll();
            if (all.stream().noneMatch(item -> directory.getParentId().equals(item.getId()))) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父目录节点不存在");
            }
        }
        directory.setCode(code);
        directoryRepository.create(directory);
        return directory;
    }

    /**
     * 更新目录节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param directory
     * @return
     */
    public AgentDirectoryEntity update(Long id, AgentDirectoryEntity directory) {
        if (directory == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        List<AgentDirectoryEntity> all = directoryRepository.findAll();
        AgentDirectoryEntity exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录节点不存在");
        }
        if (directory.getParentId() != null) {
            if (directory.getParentId().equals(id)) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父节点不能选择自身");
            }
            if (collectDescendantIds(all, id).contains(directory.getParentId())) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父节点不能选择自身子孙节点");
            }
            if (all.stream().noneMatch(item -> directory.getParentId().equals(item.getId()))) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "父目录节点不存在");
            }
        }
        exist.setName(directory.getName() != null ? directory.getName() : exist.getName());
        exist.setParentId(directory.getParentId());
        exist.setSortNum(directory.getSortNum() != null ? directory.getSortNum() : 0);
        directoryRepository.update(exist);
        return exist;
    }

    /**
     * 删除目录节点（存在子节点或挂载智能体时禁止删除）
     * @param id
     */
    public void delete(Long id) {
        List<AgentDirectoryEntity> all = directoryRepository.findAll();
        AgentDirectoryEntity exist = all.stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst().orElse(null);
        if (exist == null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "目录节点不存在");
        }
        if (all.stream().anyMatch(item -> id.equals(item.getParentId()))) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "存在子目录节点，请先删除子节点");
        }
        long agentCount = agentRepository.list().stream()
                .filter(agent -> exist.getCode().equals(agent.getDirectoryCode()))
                .count();
        if (agentCount > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "该目录下挂载了" + agentCount + "个智能体，请先调整智能体归属");
        }
        directoryRepository.deleteById(id);
    }

    /**
     * 收集指定节点的全部子孙节点ID
     * @param all
     * @param rootId
     * @return
     */
    private Set<Long> collectDescendantIds(List<AgentDirectoryEntity> all, Long rootId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            for (AgentDirectoryEntity item : all) {
                if (current.equals(item.getParentId()) && result.add(item.getId())) {
                    stack.push(item.getId());
                }
            }
        }
        return result;
    }

    /**
     * 组装目录树（各节点含挂载智能体数）
     * @param directories
     * @return
     */
    private List<DirectoryNode> buildDirectoryTree(List<AgentDirectoryEntity> directories) {
        Map<String, Long> countByCode = agentRepository.list().stream()
                .filter(agent -> agent.getDirectoryCode() != null && !agent.getDirectoryCode().isBlank())
                .collect(Collectors.groupingBy(Agent::getDirectoryCode, Collectors.counting()));
        Map<Long, DirectoryNode> nodeById = new LinkedHashMap<>();
        for (AgentDirectoryEntity directory : directories) {
            DirectoryNode node = new DirectoryNode();
            node.setId(directory.getId());
            node.setCode(directory.getCode());
            node.setName(directory.getName());
            node.setParentId(directory.getParentId());
            node.setSortNum(directory.getSortNum());
            node.setAgentCount(countByCode.getOrDefault(directory.getCode(), 0L));
            node.setChildren(new ArrayList<>());
            nodeById.put(directory.getId(), node);
        }
        List<DirectoryNode> roots = new ArrayList<>();
        for (AgentDirectoryEntity directory : directories) {
            DirectoryNode node = nodeById.get(directory.getId());
            DirectoryNode parent = directory.getParentId() == null ? null : nodeById.get(directory.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                // 根节点或父节点缺失的孤儿节点统一挂在顶层
                roots.add(node);
            }
        }
        Comparator<DirectoryNode> byOrder = Comparator.comparing(DirectoryNode::getSortNum,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(DirectoryNode::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(byOrder);
        for (DirectoryNode node : nodeById.values()) {
            node.getChildren().sort(byOrder);
        }
        return roots;
    }

    /**
     * 智能体目录树节点
     */
    public static class DirectoryNode {

        /**
         * 主键
         */
        private Long id;

        /**
         * 目录编码（创建时自定义，唯一且不可修改）
         */
        private String code;

        /**
         * 目录名称
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
         * 挂载智能体数
         */
        private Long agentCount;

        /**
         * 子节点列表
         */
        private List<DirectoryNode> children;

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

        public Long getAgentCount() {
            return agentCount;
        }

        public void setAgentCount(Long agentCount) {
            this.agentCount = agentCount;
        }

        public List<DirectoryNode> getChildren() {
            return children;
        }

        public void setChildren(List<DirectoryNode> children) {
            this.children = children;
        }
    }
}
