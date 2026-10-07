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
package com.yangqiongai.ai.data.rag.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.data.rag.entity.KbSliceRecord;
import com.yangqiongai.ai.data.rag.mapper.KbSliceRecordMapper;
import com.yangqiongai.ai.rag.model.SliceRecord;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库切片记录测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultSliceRecordRepositoryTest {

    @Mock
    private KbSliceRecordMapper kbSliceRecordMapper;

    private DefaultSliceRecordRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KbSliceRecord.class);
        repository = new DefaultSliceRecordRepository();
        java.lang.reflect.Field sliceField = DefaultSliceRecordRepository.class.getDeclaredField("kbSliceRecordMapper");
        sliceField.setAccessible(true);
        sliceField.set(repository, kbSliceRecordMapper);
    }

    private KbSliceRecord sliceEntity(String docId) {
        KbSliceRecord entity = new KbSliceRecord();
        entity.setSliceId("slice-" + docId);
        entity.setDocId(docId);
        entity.setKbId("kb-001");
        entity.setContent("切片内容");
        entity.setIsActive(true);
        entity.setVersion("v1");
        return entity;
    }

    private Map<String, Object> docNameRow(String docId, String docName) {
        return Map.of("docId", docId, "docName", docName);
    }

    @Test
    @DisplayName("全文检索结果批量回填文档名称")
    void searchFullTextSlices_fillsDocNames() {
        when(kbSliceRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                sliceEntity("doc-001"), sliceEntity("doc-002")));
        when(kbSliceRecordMapper.selectDocNamesByDocIds(anyCollection())).thenReturn(List.of(
                docNameRow("doc-001", "架构模式.pdf"), docNameRow("doc-002", "TLCL.pdf")));

        List<SliceRecord> records = repository.searchFullTextSlices("kb-001", "v1", true, 10, "关键词", null);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getDocName()).isEqualTo("架构模式.pdf");
        assertThat(records.get(1).getDocName()).isEqualTo("TLCL.pdf");
    }

    @Test
    @DisplayName("文档表查不到映射时不报错且docName为null")
    void searchFullTextSlices_missingDocKeepsNullName() {
        when(kbSliceRecordMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sliceEntity("doc-404")));
        when(kbSliceRecordMapper.selectDocNamesByDocIds(anyCollection())).thenReturn(List.of());

        List<SliceRecord> records = repository.searchFullTextSlices("kb-001", "v1", true, 10, "关键词", null);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getDocName()).isNull();
    }

    @Test
    @DisplayName("切片无文档ID时跳过文档名回填查询")
    void searchFullTextSlices_skipsFillWhenNoDocIds() {
        KbSliceRecord entity = sliceEntity(null);
        when(kbSliceRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<SliceRecord> records = repository.searchFullTextSlices("kb-001", "v1", false, 10, "关键词", null);

        assertThat(records).hasSize(1);
        verify(kbSliceRecordMapper, never()).selectDocNamesByDocIds(any(Collection.class));
    }
}
