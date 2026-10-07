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
package com.yangqiongai.ai.agent.registry.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentDefinitionMapper;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentVersionMapper;
import com.yangqiongai.ai.agent.registry.event.AgentDisabledEvent;
import com.yangqiongai.ai.agent.registry.materialize.AgentConfigMaterializer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent注册中心管理单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentRegistryServiceImpl 单元测试")
class AgentRegistryServiceImplTest {

    private static final String VALID_CONFIG = "{\"model\":\"deepseek-v3\"}";

    @Mock
    private AgentDefinitionMapper definitionMapper;

    @Mock
    private AgentVersionMapper versionMapper;

    @Mock
    private AgentConfigMaterializer materializer;

    @Mock
    private AgentManager agentManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AgentRegistryServiceImpl service;

    private AgentDefinition definition;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AgentDefinition.class);

        service = new AgentRegistryServiceImpl();
        ReflectionTestUtils.setField(service, "definitionMapper", definitionMapper);
        ReflectionTestUtils.setField(service, "versionMapper", versionMapper);
        ReflectionTestUtils.setField(service, "materializer", materializer);
        ReflectionTestUtils.setField(service, "agentManager", agentManager);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);

        definition = new AgentDefinition();
        definition.setId(1L);
        definition.setAgentCode("agent-a");
        definition.setAgentName("选矿顾问");
        definition.setStatus("DRAFT");
    }

    @Test
    @DisplayName("创建定义：agentCode为空被拒绝")
    void createDefinitionBlankCodeRejected() {
        assertThatThrownBy(() -> service.createDefinition(new AgentDefinition()))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .hasMessageContaining("agentCode不能为空");
        assertThatThrownBy(() -> service.createDefinition(null))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class);
    }

    @Test
    @DisplayName("创建定义：已存在同编码定义被拒绝")
    void createDefinitionDuplicateRejected() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentDefinition input = new AgentDefinition();
        input.setAgentCode("agent-a");

        assertThatThrownBy(() -> service.createDefinition(input))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("创建定义：状态置DRAFT并填充门禁默认值，运行时缺位联动创建骨架")
    void createDefinitionFillsDefaults() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        when(agentManager.getByCode("agent-a")).thenReturn(null);
        AgentDefinition input = new AgentDefinition();
        input.setAgentCode("agent-a");

        AgentDefinition created = service.createDefinition(input);

        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(created.getRequireApproval()).isEqualTo(0);
        assertThat(created.getEvalEnabled()).isEqualTo(0);
        assertThat(created.getEvalPassThreshold()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(created.getCardEnabled()).isEqualTo(0);
        verify(definitionMapper).insert(input);
        ArgumentCaptor<Agent> skeletonCaptor = ArgumentCaptor.forClass(Agent.class);
        verify(agentManager).save(skeletonCaptor.capture());
        assertThat(skeletonCaptor.getValue().getAgentCode()).isEqualTo("agent-a");
        assertThat(skeletonCaptor.getValue().getAgentConfig()).isEqualTo("{}");
        assertThat(skeletonCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新定义：cardEnabled未传时保留原值不覆盖")
    void updateDefinitionCardEnabledNullKept() {
        definition.setCardEnabled(1);
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentDefinition input = new AgentDefinition();
        input.setAgentCode("agent-a");
        input.setAgentName("新名称");

        AgentDefinition updated = service.updateDefinition("agent-a", input);

        assertThat(updated.getCardEnabled()).isEqualTo(1);
        verify(definitionMapper).updateById(definition);
    }

    @Test
    @DisplayName("卡片启停：开启置1且仅更新卡片开关")
    void updateCardEnabledOn() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);

        AgentDefinition updated = service.updateCardEnabled("agent-a", true);

        assertThat(updated.getCardEnabled()).isEqualTo(1);
        verify(definitionMapper).updateById(definition);
    }

    @Test
    @DisplayName("卡片启停：关闭置0")
    void updateCardEnabledOff() {
        definition.setCardEnabled(1);
        when(definitionMapper.selectOne(any())).thenReturn(definition);

        AgentDefinition updated = service.updateCardEnabled("agent-a", false);

        assertThat(updated.getCardEnabled()).isEqualTo(0);
        verify(definitionMapper).updateById(definition);
    }

    @Test
    @DisplayName("卡片启停：定义不存在抛异常")
    void updateCardEnabledNotFound() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.updateCardEnabled("no-exist", true))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .extracting(e -> ((com.yangqiongai.ai.common.exception.AiException) e).getCode())
                .isEqualTo(72005);
    }

    @Test
    @DisplayName("创建定义：运行时已存在同编码Agent时不重复创建骨架")
    void createDefinitionSkipsSkeletonWhenRuntimeExists() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        when(agentManager.getByCode("agent-a")).thenReturn(new Agent());
        AgentDefinition input = new AgentDefinition();
        input.setAgentCode("agent-a");

        service.createDefinition(input);

        verify(definitionMapper).insert(input);
        verify(agentManager, never()).save(any(Agent.class));
    }

    @Test
    @DisplayName("更新定义：仅更新元数据不改变agentCode")
    void updateDefinitionKeepsAgentCode() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentDefinition input = new AgentDefinition();
        input.setAgentCode("other-code");
        input.setAgentName("新名称");
        input.setDescription("新描述");
        input.setCategory("NEW");

        AgentDefinition updated = service.updateDefinition("agent-a", input);

        assertThat(updated.getAgentCode()).isEqualTo("agent-a");
        assertThat(updated.getAgentName()).isEqualTo("新名称");
        assertThat(updated.getCategory()).isEqualTo("NEW");
        verify(definitionMapper).updateById(definition);
    }

    @Test
    @DisplayName("更新定义：定义不存在抛异常")
    void updateDefinitionNotFound() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.updateDefinition("no-exist", new AgentDefinition()))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .extracting(e -> ((com.yangqiongai.ai.common.exception.AiException) e).getCode())
                .isEqualTo(72005);
    }

    @Test
    @DisplayName("getDefinition：编码为空返回null")
    void getDefinitionBlankReturnsNull() {
        assertThat(service.getDefinition("")).isNull();
        assertThat(service.getDefinition(null)).isNull();
    }

    @Test
    @DisplayName("分页查询：页码页大小下限保护")
    void listDefinitionsSanitizesPageParams() {
        when(definitionMapper.selectCount(any())).thenReturn(1L);
        when(definitionMapper.selectList(any())).thenReturn(List.of(definition));

        Page<AgentDefinition> page = service.listDefinitions(0, -5, null, null);

        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("分页查询：total为0时不查询列表")
    void listDefinitionsZeroTotalSkipsList() {
        when(definitionMapper.selectCount(any())).thenReturn(0L);

        Page<AgentDefinition> page = service.listDefinitions(1, 10, null, null);

        assertThat(page.getRecords()).isEmpty();
        verify(definitionMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("启用状态切换：启用时物化生效版本")
    void updateStatusEnableMaterializes() {
        definition.setStatus("DISABLED");
        definition.setCurrentVersionId(9L);
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentVersion version = new AgentVersion();
        version.setId(9L);
        when(versionMapper.selectById(9L)).thenReturn(version);

        AgentDefinition result = service.updateStatus("agent-a", true);

        assertThat(result.getStatus()).isEqualTo("ENABLED");
        verify(materializer).materialize(definition, version);
    }

    @Test
    @DisplayName("启用状态切换：无生效版本时不物化")
    void updateStatusEnableWithoutVersionSkipsMaterialize() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);

        service.updateStatus("agent-a", true);

        verify(materializer, never()).materialize(any(), any());
    }

    @Test
    @DisplayName("启用状态切换：仅启用状态可禁用")
    void updateStatusDisableRequiresEnabled() {
        definition.setStatus("DRAFT");
        when(definitionMapper.selectOne(any())).thenReturn(definition);

        assertThatThrownBy(() -> service.updateStatus("agent-a", false))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .hasMessageContaining("仅启用状态");
    }

    @Test
    @DisplayName("启用状态切换：禁用切物化并发事件")
    void updateStatusDisableMaterializesAndPublishesEvent() {
        definition.setStatus("ENABLED");
        when(definitionMapper.selectOne(any())).thenReturn(definition);

        AgentDefinition result = service.updateStatus("agent-a", false);

        assertThat(result.getStatus()).isEqualTo("DISABLED");
        verify(materializer).disable("agent-a");
        verify(eventPublisher).publishEvent(any(AgentDisabledEvent.class));
    }

    @Test
    @DisplayName("删除定义：非草稿状态被拒绝")
    void deleteDefinitionRequiresDraft() {
        definition.setStatus("ENABLED");
        when(definitionMapper.selectOne(any())).thenReturn(definition);

        assertThatThrownBy(() -> service.deleteDefinition("agent-a"))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .hasMessageContaining("仅草稿状态");
    }

    @Test
    @DisplayName("删除定义：存在版本时被拒绝")
    void deleteDefinitionWithVersionsRejected() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        when(versionMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteDefinition("agent-a"))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .extracting(e -> ((com.yangqiongai.ai.common.exception.AiException) e).getCode())
                .isEqualTo(72006);
    }

    @Test
    @DisplayName("删除定义：草稿且无版本时删除成功")
    void deleteDefinitionSuccess() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        when(versionMapper.selectCount(any())).thenReturn(0L);

        service.deleteDefinition("agent-a");

        verify(definitionMapper).deleteById(1L);
    }

    @Test
    @DisplayName("创建版本：定义不存在抛异常")
    void createVersionDefinitionNotFound() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        AgentVersion version = new AgentVersion();
        version.setConfigJson(VALID_CONFIG);

        assertThatThrownBy(() -> service.createVersion("no-exist", version))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .extracting(e -> ((com.yangqiongai.ai.common.exception.AiException) e).getCode())
                .isEqualTo(72005);
    }

    @Test
    @DisplayName("创建版本：配置非法被拒绝")
    void createVersionInvalidConfigRejected() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentVersion version = new AgentVersion();
        version.setConfigJson("{bad");

        assertThatThrownBy(() -> service.createVersion("agent-a", version))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class);
        verify(versionMapper, never()).insert(any(AgentVersion.class));
    }

    @Test
    @DisplayName("创建版本：版本号重复被拒绝")
    void createVersionDuplicateVersionNoRejected() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        when(versionMapper.selectOne(any())).thenReturn(new AgentVersion());
        AgentVersion version = new AgentVersion();
        version.setVersionNo("v1");
        version.setConfigJson(VALID_CONFIG);

        assertThatThrownBy(() -> service.createVersion("agent-a", version))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .hasMessageContaining("版本号已存在");
    }

    @Test
    @DisplayName("创建版本：自动生成版本号并计算hash与状态")
    void createVersionGeneratesDefaults() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentVersion version = new AgentVersion();
        version.setConfigJson(VALID_CONFIG);

        AgentVersion created = service.createVersion("agent-a", version);

        assertThat(created.getAgentCode()).isEqualTo("agent-a");
        assertThat(created.getVersionNo()).isNotBlank();
        assertThat(created.getConfigHash()).isNotBlank();
        assertThat(created.getStatus()).isEqualTo("DRAFT");
        verify(versionMapper).insert(version);
    }

    @Test
    @DisplayName("更新草稿：非草稿状态被拒绝")
    void updateDraftRequiresDraft() {
        AgentVersion existing = new AgentVersion();
        existing.setId(1L);
        existing.setVersionNo("v1");
        existing.setStatus("PUBLISHED");
        when(versionMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> service.updateDraft(1L, new AgentVersion()))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .hasMessageContaining("仅草稿版本可编辑");
    }

    @Test
    @DisplayName("更新草稿：配置变更重算hash")
    void updateDraftRecalculatesHash() {
        AgentVersion existing = new AgentVersion();
        existing.setId(1L);
        existing.setVersionNo("v1");
        existing.setStatus("DRAFT");
        existing.setConfigJson(VALID_CONFIG);
        when(versionMapper.selectById(1L)).thenReturn(existing);
        AgentVersion input = new AgentVersion();
        input.setConfigJson("{\"model\":\"qwen-max\"}");
        input.setChangelog("换模型");

        AgentVersion updated = service.updateDraft(1L, input);

        assertThat(updated.getConfigJson()).isEqualTo("{\"model\":\"qwen-max\"}");
        assertThat(updated.getConfigHash()).isNotEqualTo(com.yangqiongai.ai.agent.registry.config.AgentConfigValidator.hash(VALID_CONFIG));
        assertThat(updated.getChangelog()).isEqualTo("换模型");
        verify(versionMapper).updateById(existing);
    }

    @Test
    @DisplayName("更新草稿：空配置时保留原配置")
    void updateDraftBlankConfigKeepsOriginal() {
        AgentVersion existing = new AgentVersion();
        existing.setId(1L);
        existing.setStatus("DRAFT");
        existing.setConfigJson(VALID_CONFIG);
        when(versionMapper.selectById(1L)).thenReturn(existing);

        AgentVersion updated = service.updateDraft(1L, new AgentVersion());

        assertThat(updated.getConfigJson()).isEqualTo(VALID_CONFIG);
    }

    @Test
    @DisplayName("更新草稿：版本不存在抛异常")
    void updateDraftNotFound() {
        when(versionMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateDraft(404L, new AgentVersion()))
                .isInstanceOf(com.yangqiongai.ai.common.exception.AiException.class)
                .extracting(e -> ((com.yangqiongai.ai.common.exception.AiException) e).getCode())
                .isEqualTo(72003);
    }

    @Test
    @DisplayName("listVersions校验定义存在并返回列表")
    void listVersionsReturnsList() {
        when(definitionMapper.selectOne(any())).thenReturn(definition);
        AgentVersion version = new AgentVersion();
        version.setVersionNo("v1");
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        List<AgentVersion> versions = service.listVersions("agent-a");

        assertThat(versions).hasSize(1);
    }

    @Test
    @DisplayName("getVersion直接透传selectById")
    void getVersionDelegates() {
        AgentVersion version = new AgentVersion();
        when(versionMapper.selectById(1L)).thenReturn(version);
        assertThat(service.getVersion(1L)).isSameAs(version);
    }

    @Test
    @DisplayName("ArgumentCaptor捕获插入的定义")
    void insertCaptorSmoke() {
        when(definitionMapper.selectOne(any())).thenReturn(null);
        when(agentManager.getByCode("agent-b")).thenReturn(null);
        AgentDefinition input = new AgentDefinition();
        input.setAgentCode("agent-b");

        service.createDefinition(input);

        ArgumentCaptor<AgentDefinition> captor = ArgumentCaptor.forClass(AgentDefinition.class);
        verify(definitionMapper).insert(captor.capture());
        assertThat(captor.getValue().getAgentCode()).isEqualTo("agent-b");
    }
}
