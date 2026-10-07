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
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能体目录管理单测
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentDirectoryServiceTest {

    @Mock
    private AgentDirectoryRepository directoryRepository;

    @Mock
    private AgentRepository agentRepository;

    private AgentDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new AgentDirectoryService(directoryRepository, agentRepository);
    }

    @Test
    @DisplayName("目录树：返回层级结构与各节点智能体数")
    void treeBuildsHierarchy() {
        Agent inChild = agent("agent-a", "child-dir");
        Agent inRoot = agent("agent-b", "root-dir");
        Agent ungrouped = agent("agent-c", null);
        when(agentRepository.list()).thenReturn(List.of(inChild, inRoot, ungrouped));
        when(directoryRepository.findAll()).thenReturn(List.of(
                directory(1L, "root-dir", null),
                directory(2L, "child-dir", 1L)));

        Map<String, Object> result = service.tree();

        List<AgentDirectoryService.DirectoryNode> nodes =
                (List<AgentDirectoryService.DirectoryNode>) result.get("nodes");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getCode()).isEqualTo("root-dir");
        assertThat(nodes.get(0).getAgentCount()).isEqualTo(1L);
        assertThat(nodes.get(0).getChildren()).hasSize(1);
        assertThat(nodes.get(0).getChildren().get(0).getAgentCount()).isEqualTo(1L);
        assertThat(result.get("ungroupedCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("新增目录：编码非法、重复或保留编码时拒绝")
    void createValidations() {
        AgentDirectoryEntity badCode = directory(null, "非法编码!", null);
        assertThatThrownBy(() -> service.create(badCode)).isInstanceOf(AiException.class);

        AgentDirectoryEntity reserved = directory(null, "__ungrouped__", null);
        assertThatThrownBy(() -> service.create(reserved)).isInstanceOf(AiException.class)
                .hasMessageContaining("保留编码");

        AgentDirectoryEntity dup = directory(null, "dup-dir", null);
        when(directoryRepository.findByCode("dup-dir")).thenReturn(Optional.of(dup));
        assertThatThrownBy(() -> service.create(dup)).isInstanceOf(AiException.class);

        AgentDirectoryEntity orphan = directory(null, "orphan-dir", 999L);
        when(directoryRepository.findByCode("orphan-dir")).thenReturn(Optional.empty());
        when(directoryRepository.findAll()).thenReturn(List.of(directory(1L, "root-dir", null)));
        assertThatThrownBy(() -> service.create(orphan)).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("新增目录：合法节点写入库并返回")
    void createPersists() {
        when(directoryRepository.findByCode("new-dir")).thenReturn(Optional.empty());
        when(directoryRepository.findAll()).thenReturn(List.of(directory(1L, "root-dir", null)));

        AgentDirectoryEntity request = directory(null, "new-dir", 1L);
        request.setName("新目录");
        AgentDirectoryEntity created = service.create(request);

        assertThat(created.getCode()).isEqualTo("new-dir");
        ArgumentCaptor<AgentDirectoryEntity> captor = ArgumentCaptor.forClass(AgentDirectoryEntity.class);
        verify(directoryRepository).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("new-dir");
    }

    @Test
    @DisplayName("更新目录：父节点不能选择自身或子孙")
    void updateRejectsCycle() {
        when(directoryRepository.findAll()).thenReturn(List.of(
                directory(1L, "root-dir", null),
                directory(2L, "child-dir", 1L)));

        AgentDirectoryEntity selfParent = directory(1L, "root-dir", 1L);
        assertThatThrownBy(() -> service.update(1L, selfParent)).isInstanceOf(AiException.class);

        AgentDirectoryEntity descendantParent = directory(1L, "root-dir", 2L);
        assertThatThrownBy(() -> service.update(1L, descendantParent)).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("更新目录：编码不可修改，合法变更写入库")
    void updateIgnoresCodeAndPersists() {
        when(directoryRepository.findAll()).thenReturn(List.of(
                directory(1L, "root-dir", null),
                directory(2L, "child-dir", 1L)));

        // 提交体携带新的编码，更新应以存量编码为准
        AgentDirectoryEntity patch = directory(2L, "hacked-code", null);
        patch.setName("改名目录");
        AgentDirectoryEntity updated = service.update(2L, patch);

        assertThat(updated.getCode()).isEqualTo("child-dir");
        assertThat(updated.getName()).isEqualTo("改名目录");
        ArgumentCaptor<AgentDirectoryEntity> captor = ArgumentCaptor.forClass(AgentDirectoryEntity.class);
        verify(directoryRepository).update(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("child-dir");
        assertThat(captor.getValue().getParentId()).isNull();
    }

    @Test
    @DisplayName("删除目录：存在子节点或挂载智能体时拒绝")
    void deleteValidations() {
        when(directoryRepository.findAll()).thenReturn(List.of(
                directory(1L, "root-dir", null),
                directory(2L, "child-dir", 1L)));
        when(agentRepository.list()).thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(AiException.class)
                .hasMessageContaining("子目录节点");

        // 子节点清理后，节点下仍挂载智能体时同样拒绝
        when(directoryRepository.findAll()).thenReturn(List.of(directory(1L, "root-dir", null)));
        when(agentRepository.list()).thenReturn(List.of(agent("agent-a", "root-dir")));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(AiException.class)
                .hasMessageContaining("挂载");
    }

    @Test
    @DisplayName("删除目录：空节点允许删除")
    void deletePersists() {
        when(agentRepository.list()).thenReturn(List.of(agent("agent-b", null)));
        when(directoryRepository.findAll()).thenReturn(List.of(directory(1L, "root-dir", null)));

        service.delete(1L);

        verify(directoryRepository).deleteById(1L);
    }

    private Agent agent(String agentCode, String directoryCode) {
        Agent agent = new Agent();
        agent.setAgentCode(agentCode);
        agent.setAgentName(agentCode);
        agent.setDirectoryCode(directoryCode);
        return agent;
    }

    private AgentDirectoryEntity directory(Long id, String code, Long parentId) {
        AgentDirectoryEntity directory = new AgentDirectoryEntity();
        directory.setId(id);
        directory.setCode(code);
        directory.setName(code);
        directory.setParentId(parentId);
        directory.setSortNum(0);
        return directory;
    }
}
