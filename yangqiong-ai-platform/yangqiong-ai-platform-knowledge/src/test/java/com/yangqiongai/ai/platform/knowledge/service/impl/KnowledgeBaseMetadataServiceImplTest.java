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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.platform.knowledge.mapper.KnowledgeBaseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库元数据版本解析
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseMetadataServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    private KnowledgeBaseMetadataServiceImpl service;

    private KnowledgeBase kb;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
    }

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseMetadataServiceImpl();
        ReflectionTestUtils.setField(service, "knowledgeBaseMapper", knowledgeBaseMapper);
        kb = new KnowledgeBase();
        kb.setKbId("kb_test");
        kb.setKbName("测试库");
    }

    @Test
    @DisplayName("kbId为空返回null且不查询")
    void ensureReturnsNullWhenKbIdBlank() {
        assertThat(service.ensureActiveVersion(null)).isNull();
        assertThat(service.ensureActiveVersion("  ")).isNull();
        verify(knowledgeBaseMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("知识库不存在返回null")
    void ensureReturnsNullWhenKbMissing() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(null);
        assertThat(service.ensureActiveVersion("kb_missing")).isNull();
    }

    @Test
    @DisplayName("已有活跃版本直接返回不回写")
    void ensureReturnsExistingVersionWithoutUpdate() {
        kb.setActiveVersion("v3");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        String version = service.ensureActiveVersion("kb_test");
        assertThat(version).isEqualTo("v3");
        verify(knowledgeBaseMapper, never()).updateById(any(KnowledgeBase.class));
    }

    @Test
    @DisplayName("活跃版本为空时初始化v1并回写")
    void ensureInitializesDefaultVersionWhenBlank() {
        kb.setActiveVersion(null);
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        String version = service.ensureActiveVersion("kb_test");
        assertThat(version).isEqualTo("v1");
        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).updateById(captor.capture());
        assertThat(captor.getValue().getActiveVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("活跃版本为空白串时同样初始化")
    void ensureInitializesDefaultVersionWhenWhitespace() {
        kb.setActiveVersion("   ");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        String version = service.ensureActiveVersion("kb_test");
        assertThat(version).isEqualTo("v1");
        verify(knowledgeBaseMapper).updateById(any(KnowledgeBase.class));
    }
}
