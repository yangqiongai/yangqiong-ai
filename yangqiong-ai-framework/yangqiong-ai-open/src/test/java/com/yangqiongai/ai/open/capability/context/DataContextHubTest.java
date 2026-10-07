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
package com.yangqiongai.ai.open.capability.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 数据上下文枢纽
 * @author yangqiong
 */
class DataContextHubTest {

    private DataContextRepository repository;

    private DataContextHub hub;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(DataContextRepository.class);
        hub = new DataContextHub(repository);
    }

    @Test
    void testPushAndGetContext() {
        DataContext context = new DataContext();
        context.setType("project-info");
        context.setContent("项目数据内容");
        context.setName("项目信息");
        context.setTtlSeconds(0);

        when(repository.save(any(DataContext.class))).thenReturn("ref-001");
        when(repository.get("ref-001")).thenReturn(Optional.of(context));

        String ref = hub.push(context);
        assertThat(ref).isEqualTo("ref-001");

        DataContext result = hub.get(ref);
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("project-info");
        assertThat(result.getContent()).isEqualTo("项目数据内容");
    }

    @Test
    void testPushBatchReturnsRefs() {
        DataContext ctx1 = new DataContext();
        ctx1.setType("type-a");
        ctx1.setTtlSeconds(0);
        DataContext ctx2 = new DataContext();
        ctx2.setType("type-b");
        ctx2.setTtlSeconds(0);

        when(repository.save(any(DataContext.class)))
                .thenReturn("ref-001")
                .thenReturn("ref-002");

        List<String> refs = hub.pushBatch(List.of(ctx1, ctx2));

        assertThat(refs).hasSize(2);
        assertThat(refs).containsExactly("ref-001", "ref-002");
    }

    @Test
    void testGetReturnsNullForNonExistentRef() {
        when(repository.get("non-existent")).thenReturn(Optional.empty());

        DataContext result = hub.get("non-existent");
        assertThat(result).isNull();
    }

    @Test
    void testRenderToPromptWithRefs() {
        DataContext ctx1 = new DataContext();
        ctx1.setType("project-info");
        ctx1.setName("项目信息");
        ctx1.setContent("这是一个项目");

        DataContext ctx2 = new DataContext();
        ctx2.setType("contract-text");
        ctx2.setName("合同文本");
        ctx2.setContent("这是一份合同");

        when(repository.getBatch(List.of("ref-1", "ref-2"))).thenReturn(List.of(ctx1, ctx2));

        String result = hub.renderToPrompt(List.of("ref-1", "ref-2"));

        assertThat(result).contains("以下是相关数据上下文：");
        assertThat(result).contains("--- 项目信息 ---");
        assertThat(result).contains("这是一个项目");
        assertThat(result).contains("--- 合同文本 ---");
        assertThat(result).contains("这是一份合同");
    }

    @Test
    void testRenderToPromptWithNullRefs() {
        String result = hub.renderToPrompt(null);
        assertThat(result).isEmpty();
    }

    @Test
    void testRenderToPromptWithEmptyRefs() {
        String result = hub.renderToPrompt(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void testRenderToPromptWithEmptyStoreResult() {
        when(repository.getBatch(anyList())).thenReturn(List.of());

        String result = hub.renderToPrompt(List.of("ref-1"));
        assertThat(result).isEmpty();
    }

    @Test
    void testRenderToPromptWithNullNameUsesType() {
        DataContext ctx = new DataContext();
        ctx.setType("custom-type");
        ctx.setName(null);
        ctx.setContent("内容");

        when(repository.getBatch(List.of("ref-1"))).thenReturn(List.of(ctx));

        String result = hub.renderToPrompt(List.of("ref-1"));

        assertThat(result).contains("--- custom-type ---");
        assertThat(result).contains("内容");
    }

    @Test
    void testPushWithDefaultTtl() {
        DataContext context = new DataContext();
        context.setType("test-type");
        context.setContent("test");
        context.setTtlSeconds(-1);

        when(repository.save(any(DataContext.class))).thenAnswer(invocation -> {
            DataContext arg = invocation.getArgument(0);
            assertThat(arg.getTtlSeconds()).isEqualTo(3600);
            return "ref-001";
        });

        String ref = hub.push(context);
        assertThat(ref).isEqualTo("ref-001");
    }

    @Test
    void testGetBatch() {
        DataContext ctx1 = new DataContext();
        ctx1.setType("type-a");
        ctx1.setContent("content-a");

        when(repository.getBatch(List.of("ref-1", "ref-2"))).thenReturn(List.of(ctx1));

        List<DataContext> result = hub.getBatch(List.of("ref-1", "ref-2"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("type-a");
    }
}