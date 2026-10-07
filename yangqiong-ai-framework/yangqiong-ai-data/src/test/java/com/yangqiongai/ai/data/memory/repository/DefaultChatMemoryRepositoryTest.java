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
package com.yangqiongai.ai.data.memory.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.data.memory.entity.ChatMemory;
import com.yangqiongai.ai.data.memory.mapper.ChatMemoryMapper;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话记忆存储测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultChatMemoryRepositoryTest {

    @Mock
    private ChatMemoryMapper chatMemoryMapper;

    private DefaultChatMemoryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChatMemory.class);
        repository = new DefaultChatMemoryRepository();
        java.lang.reflect.Field mapperField = DefaultChatMemoryRepository.class.getDeclaredField("chatMemoryMapper");
        mapperField.setAccessible(true);
        mapperField.set(repository, chatMemoryMapper);
    }

    private ChatMemory entity(Long id, String role, String content, LocalDateTime createTime) {
        ChatMemory entity = new ChatMemory();
        entity.setId(id);
        entity.setMessageId("msg-" + id);
        entity.setSessionId("sess-001");
        entity.setUserId("user-001");
        entity.setMessageRole(role);
        entity.setMessageContent(content);
        entity.setCreateTime(createTime);
        entity.setScopeId("default");
        return entity;
    }

    @Test
    @DisplayName("按会话查询时先按create_time升序再按id升序排序")
    void findBySessionId_ordersByCreateTimeThenId() {
        when(chatMemoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        repository.findBySessionId("sess-001");

        ArgumentCaptor<LambdaQueryWrapper<ChatMemory>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(chatMemoryMapper).selectList(captor.capture());
        // create_time为秒级精度，同一秒落库的消息依赖id次级排序保证顺序稳定
        String segment = captor.getValue().getSqlSegment();
        int createTimeIdx = segment.indexOf("create_time ASC");
        int idIdx = segment.indexOf("id ASC");
        assertThat(createTimeIdx).isGreaterThanOrEqualTo(0);
        assertThat(idIdx).isGreaterThan(createTimeIdx);
    }

    @Test
    @DisplayName("按会话查询时结果映射为消息记录")
    void findBySessionId_mapsEntitiesToRecords() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 10, 0, 0);
        when(chatMemoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        entity(1L, "USER", "你好", now),
                        entity(2L, "ASSISTANT", "您好，有什么可以帮您？", now)));

        List<ChatMemoryRecord> records = repository.findBySessionId("sess-001");

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getMessageRole()).isEqualTo("USER");
        assertThat(records.get(0).getMessageContent()).isEqualTo("你好");
        assertThat(records.get(0).getSessionId()).isEqualTo("sess-001");
        assertThat(records.get(1).getMessageRole()).isEqualTo("ASSISTANT");
        assertThat(records.get(1).getCreateTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("保存消息时字段映射正确并回填主键")
    void saveMessage_mapsRecordToEntityAndBackfillsId() {
        when(chatMemoryMapper.insert(any(ChatMemory.class))).thenAnswer(inv -> {
            ChatMemory entity = inv.getArgument(0);
            entity.setId(77L);
            return 1;
        });
        ChatMemoryRecord record = new ChatMemoryRecord();
        record.setMessageId("msg-1");
        record.setSessionId("sess-001");
        record.setUserId("user-001");
        record.setMessageRole("USER");
        record.setMessageContent("你好");
        record.setTokenCount(10);
        record.setScopeId("default");

        ChatMemoryRecord saved = repository.saveMessage(record);

        ArgumentCaptor<ChatMemory> captor = ArgumentCaptor.forClass(ChatMemory.class);
        verify(chatMemoryMapper).insert(captor.capture());
        ChatMemory inserted = captor.getValue();
        assertThat(inserted.getMessageId()).isEqualTo("msg-1");
        assertThat(inserted.getSessionId()).isEqualTo("sess-001");
        assertThat(inserted.getUserId()).isEqualTo("user-001");
        assertThat(inserted.getMessageRole()).isEqualTo("USER");
        assertThat(inserted.getMessageContent()).isEqualTo("你好");
        assertThat(inserted.getTokenCount()).isEqualTo(10);
        assertThat(saved.getId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("按会话删除委托Mapper删除")
    void deleteBySessionId_delegatesToMapper() {
        when(chatMemoryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(2);

        repository.deleteBySessionId("sess-001");

        verify(chatMemoryMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("按主键查询不存在时返回null")
    void findById_notFound_returnsNull() {
        when(chatMemoryMapper.selectById(99L)).thenReturn(null);

        assertThat(repository.findById(99L)).isNull();
    }

    @Test
    @DisplayName("按主键查询存在时映射为消息记录")
    void findById_found_mapsToRecord() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 10, 0, 0);
        when(chatMemoryMapper.selectById(1L)).thenReturn(entity(1L, "ASSISTANT", "回答", now));

        ChatMemoryRecord record = repository.findById(1L);

        assertThat(record).isNotNull();
        assertThat(record.getId()).isEqualTo(1L);
        assertThat(record.getMessageRole()).isEqualTo("ASSISTANT");
        assertThat(record.getMessageContent()).isEqualTo("回答");
    }
}
