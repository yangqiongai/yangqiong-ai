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
package com.yangqiongai.ai.platform.knowledge.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.platform.knowledge.mapper.KbDocumentMapper;
import com.yangqiongai.ai.platform.knowledge.mapper.KnowledgeBaseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KnowledgeBaseServiceImpl 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KbDocumentMapper kbDocumentMapper;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    private KnowledgeBase kb;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
    }

    @BeforeEach
    void setUp() {
        kb = new KnowledgeBase();
        kb.setKbName("测试知识库");
        kb.setKbDescription("描述");
    }

    @Test
    @DisplayName("create - 自动生成kbId")
    void create_autoGeneratesKbId() {
        kb.setKbId(null);
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenReturn(1);

        KnowledgeBase result = knowledgeBaseService.create(kb);

        assertThat(result.getKbId()).isNotNull().startsWith("kb_");
        verify(knowledgeBaseMapper).insert(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("create - 忽略前端传入的kbId，强制重新生成")
    void create_ignoresInputKbId() {
        kb.setKbId("frontend-input-id");
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenReturn(1);

        KnowledgeBase result = knowledgeBaseService.create(kb);

        assertThat(result.getKbId()).isNotEqualTo("frontend-input-id").startsWith("kb_");
    }

    @Test
    @DisplayName("create - 自动设置createTime")
    void create_setsCreateTime_whenNull() {
        kb.setKbId(null);
        kb.setCreateTime(null);
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenReturn(1);

        KnowledgeBase result = knowledgeBaseService.create(kb);

        assertThat(result.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("create - 设置createUser时同步填充updateUser和updateTime")
    void create_setsUpdateUser_whenCreateUserPresent() {
        kb.setKbId(null);
        kb.setCreateUser("test-user");
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenReturn(1);

        KnowledgeBase result = knowledgeBaseService.create(kb);

        assertThat(result.getUpdateUser()).isEqualTo("test-user");
        assertThat(result.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("findByKbId - 存在时返回Optional")
    void findByKbId_returnsOptional_whenFound() {
        kb.setKbId("kb-123");
        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(kb);

        Optional<KnowledgeBase> result = knowledgeBaseService.findByKbId("kb-123");

        assertThat(result).isPresent();
        assertThat(result.get().getKbId()).isEqualTo("kb-123");
    }

    @Test
    @DisplayName("findByKbId - 不存在时返回空Optional")
    void findByKbId_returnsEmptyOptional_whenNotFound() {
        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Optional<KnowledgeBase> result = knowledgeBaseService.findByKbId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll - 返回列表")
    void findAll_returnsList() {
        KnowledgeBase kb1 = new KnowledgeBase();
        kb1.setKbId("kb-1");
        KnowledgeBase kb2 = new KnowledgeBase();
        kb2.setKbId("kb-2");
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb1, kb2));

        List<KnowledgeBase> result = knowledgeBaseService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getKbId()).isEqualTo("kb-1");
        assertThat(result.get(1).getKbId()).isEqualTo("kb-2");
    }

    @Test
    @DisplayName("findAll - 查询条件排除已删除状态")
    void findAll_excludesDeletedStatus() {
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of());

        knowledgeBaseService.findAll();

        ArgumentCaptor<LambdaQueryWrapper<KnowledgeBase>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(knowledgeBaseMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("kb_status");
    }

    @Test
    @DisplayName("deleteByKbId - 知识库不存在时抛出AiException")
    void deleteByKbId_throwsAiException_whenNotFound() {
        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> knowledgeBaseService.deleteByKbId("nonexistent"))
                .isInstanceOf(AiException.class)
                .satisfies(ex -> {
                    AiException aiEx = (AiException) ex;
                    assertThat(aiEx.getCode()).isEqualTo(AiErrorCode.RAG_KB_NOT_FOUND.getCode());
                });

        verify(knowledgeBaseMapper, never()).updateById(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("update - 知识库不存在时抛出AiException")
    void update_throwsAiException_whenNotFound() {
        kb.setKbId("nonexistent");
        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> knowledgeBaseService.update(kb))
                .isInstanceOf(AiException.class);

        verify(knowledgeBaseMapper, never()).updateById(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("update - 保护id/kbId/createTime/createUser字段不被篡改")
    void update_protectsImmutableFields() {
        kb.setKbId("kb-123");
        kb.setId(999L);  // 前端篡改的id
        kb.setCreateUser("hacker");
        kb.setCreateTime(LocalDateTime.of(2020, 1, 1, 0, 0));

        KnowledgeBase existing = new KnowledgeBase();
        existing.setKbId("kb-123");
        existing.setId(1L);
        existing.setCreateUser("original-user");
        existing.setCreateTime(LocalDateTime.of(2024, 1, 1, 0, 0));

        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(knowledgeBaseMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);

        KnowledgeBase result = knowledgeBaseService.update(kb);

        assertThat(result.getId()).isEqualTo(1L);  // 使用existing.id，非前端传入的999
        assertThat(result.getKbId()).isEqualTo("kb-123");
        assertThat(result.getCreateUser()).isEqualTo("original-user");
        assertThat(result.getCreateTime()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0));
        assertThat(result.getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("deleteByKbId - 知识库存在时正常删除")
    void deleteByKbId_deletes_whenFound() {
        kb.setKbId("kb-123");
        kb.setId(1L);
        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(kb);
        when(knowledgeBaseMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);

        knowledgeBaseService.deleteByKbId("kb-123");

        verify(knowledgeBaseMapper).updateById(any(KnowledgeBase.class));
        assertThat(kb.getKbStatus()).isEqualTo(2);
    }
}
