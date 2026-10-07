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
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.event.DocumentIngestEvent;
import com.yangqiongai.ai.common.rag.DocumentMetadataHandler;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.platform.knowledge.entity.DataSourceIngestLog;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.platform.knowledge.mapper.DataSourceIngestLogMapper;
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
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 数据源接入记录
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DataSourceIngestServiceImplTest {

    @Mock
    private DocumentMetadataHandler documentMetadataHandler;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private DataSourceIngestLogMapper dataSourceIngestLogMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DataSourceIngestServiceImpl dataSourceIngestService;

    private KnowledgeBase kb;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
        TableInfoHelper.initTableInfo(assistant, DataSourceIngestLog.class);
    }

    @BeforeEach
    void setUp() {
        kb = new KnowledgeBase();
        kb.setKbId("kb-1");
        kb.setUserId("user-1");
    }

    @Test
    @DisplayName("接入成功 - 记录成功日志且字段完整")
    void ingestSuccessRecordLog() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(documentMetadataHandler.createDocument(any(IngestDocument.class))).thenAnswer(inv -> {
            IngestDocument doc = inv.getArgument(0);
            doc.setDocId("doc-100");
            return doc;
        });

        IngestDocument result = dataSourceIngestService.ingestFromText("kb-1", "测试标题", "测试内容");

        assertThat(result.getDocId()).isEqualTo("doc-100");
        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(1)).insert(captor.capture());
        DataSourceIngestLog record = captor.getValue();
        assertThat(record.getIngestType()).isEqualTo("TEXT");
        assertThat(record.getTitle()).isEqualTo("测试标题");
        assertThat(record.getKbId()).isEqualTo("kb-1");
        assertThat(record.getDocId()).isEqualTo("doc-100");
        assertThat(record.getContentSize()).isEqualTo((long) "测试内容".length());
        assertThat(record.getUserId()).isEqualTo("user-1");
        assertThat(record.getSuccess()).isTrue();
        assertThat(record.getErrorMessage()).isNull();
        assertThat(record.getDurationMs()).isGreaterThanOrEqualTo(0L);
        assertThat(record.getIngestTime()).isNotNull();
    }

    @Test
    @DisplayName("内容为空 - 抛异常且记录失败日志")
    void ingestBlankContentRecordFailLog() {
        assertThatThrownBy(() -> dataSourceIngestService.ingestFromText("kb-1", "标题", "  "))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("文本内容不能为空");

        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(1)).insert(captor.capture());
        DataSourceIngestLog record = captor.getValue();
        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getErrorMessage()).contains("文本内容不能为空");
        assertThat(record.getDocId()).isNull();
        verify(documentMetadataHandler, never()).createDocument(any());
    }

    @Test
    @DisplayName("知识库ID为空 - 抛异常且记录失败日志")
    void ingestBlankKbIdRecordFailLog() {
        assertThatThrownBy(() -> dataSourceIngestService.ingestFromText(null, "标题", "内容"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("知识库ID不能为空");

        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getSuccess()).isFalse();
        assertThat(captor.getValue().getErrorMessage()).contains("知识库ID不能为空");
    }

    @Test
    @DisplayName("接入异常 - 记录失败日志后原样抛出")
    void ingestExceptionRecordFailLogAndRethrow() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(documentMetadataHandler.createDocument(any(IngestDocument.class)))
                .thenThrow(new RuntimeException("存储写入失败"));

        assertThatThrownBy(() -> dataSourceIngestService.ingestFromText("kb-1", "标题", "内容"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("存储写入失败");

        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(1)).insert(captor.capture());
        DataSourceIngestLog record = captor.getValue();
        assertThat(record.getSuccess()).isFalse();
        assertThat(record.getErrorMessage()).isEqualTo("存储写入失败");
        verify(eventPublisher, never()).publishEvent(any(DocumentIngestEvent.class));
    }

    @Test
    @DisplayName("日志写入失败 - 不影响接入主流程")
    void logWriteFailureNotAffectIngest() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(documentMetadataHandler.createDocument(any(IngestDocument.class))).thenAnswer(inv -> {
            IngestDocument doc = inv.getArgument(0);
            doc.setDocId("doc-200");
            return doc;
        });
        when(dataSourceIngestLogMapper.insert(any(DataSourceIngestLog.class)))
                .thenThrow(new RuntimeException("日志表异常"));

        IngestDocument result = dataSourceIngestService.ingestFromText("kb-1", "标题", "内容");

        assertThat(result.getDocId()).isEqualTo("doc-200");
        verify(eventPublisher, times(1)).publishEvent(any(DocumentIngestEvent.class));
    }

    @Test
    @DisplayName("四种接入方式 - 类型与来源URL映射正确")
    void ingestTypeMapping() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(documentMetadataHandler.createDocument(any(IngestDocument.class))).thenAnswer(inv -> {
            IngestDocument doc = inv.getArgument(0);
            doc.setDocId("doc-x");
            return doc;
        });

        dataSourceIngestService.ingestFromDatabase("kb-1", "t", "c");
        dataSourceIngestService.ingestFromApi("kb-1", "t", "c", "http://api");
        dataSourceIngestService.ingestFromWebpage("kb-1", "http://web", "t", "c");
        dataSourceIngestService.ingestFromText("kb-1", "t", "c");

        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(4)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataSourceIngestLog::getIngestType)
                .containsExactly("DATABASE", "API", "WEBPAGE", "TEXT");
        assertThat(captor.getAllValues()).extracting(DataSourceIngestLog::getSourceUrl)
                .containsExactly(null, "http://api", "http://web", null);
    }

    @Test
    @DisplayName("知识库不存在 - 日志userId为空仍记录成功")
    void kbNotFoundRecordSuccessWithoutUser() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(null);
        when(documentMetadataHandler.createDocument(any(IngestDocument.class))).thenAnswer(inv -> {
            IngestDocument doc = inv.getArgument(0);
            doc.setDocId("doc-300");
            return doc;
        });

        IngestDocument result = dataSourceIngestService.ingestFromText("kb-404", "标题", "内容");

        assertThat(result.getDocId()).isEqualTo("doc-300");
        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getSuccess()).isTrue();
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    @DisplayName("网页接入 - 来源URL写入记录")
    void webpageIngestRecordSourceUrl() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(documentMetadataHandler.createDocument(any(IngestDocument.class))).thenAnswer(inv -> {
            IngestDocument doc = inv.getArgument(0);
            doc.setDocId("doc-400");
            return doc;
        });

        dataSourceIngestService.ingestFromWebpage("kb-1", "https://example.com/page", "网页标题", "网页内容");

        ArgumentCaptor<DataSourceIngestLog> captor = ArgumentCaptor.forClass(DataSourceIngestLog.class);
        verify(dataSourceIngestLogMapper, times(1)).insert(captor.capture());
        DataSourceIngestLog record = captor.getValue();
        assertThat(record.getIngestType()).isEqualTo("WEBPAGE");
        assertThat(record.getSourceUrl()).isEqualTo("https://example.com/page");
        assertThat(record.getTitle()).isEqualTo("网页标题");
        assertThat(record.getSuccess()).isTrue();
    }
}
